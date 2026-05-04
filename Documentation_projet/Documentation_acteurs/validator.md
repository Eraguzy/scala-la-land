
# ValidatorActor (`actors/validator.scala`)

This document describes the `ValidatorActor`: its public messages, internal behaviour, and how it coordinates with the mempool and the DB actor to mine and persist blocks.

**Goals**
- Periodically poll the mempool for pending transactions and mine a new block.
- Produce a fee transaction for each mined transaction and include it in the block.
- Persist the mined block to the DB, then notify wallets and clean the mempool only after the DB confirms success.

## Messages (how to use)

The `ValidatorActor` protocol is defined in `messages/validator.scala`. In normal operation these messages are triggered internally (by the timer or by message adapters), not by external actors. They are documented here for completeness.

- `StartMining` *(internal — fired by timer)*
  - Sent automatically every **5 seconds** by an Akka `withTimers` scheduler.
  - Triggers a `Mempool.GetTxs` query to fetch the next batch of pending transactions.

- `ProcessBlock(txs: List[PendingTx])` *(internal — adapter from mempool reply)*
  - Received when the mempool replies with a batch of transactions.
  - If the list is non-empty, the actor queries the DB for the last block hash/id before starting mining.
  - If the list is empty, the actor does nothing and waits for the next timer tick.

- `ProcessMining(lastHash: String, currentId: Int)` *(internal — adapter from DB reply)*
  - Received after the DB replies with `LastBlockInfo`.
  - The actor runs the proof-of-work loop, constructs the new block, and sends it to the DB for persistence.

- `ConfirmSaved` / `SaveFailed(error)` *(internal — adapters from DB save response)*
  - `ConfirmSaved`: the DB persisted the block successfully. The actor notifies all waiting wallets (`replyTo ! true`) and tells the mempool to remove the confirmed transactions.
  - `SaveFailed`: the DB write failed. The actor notifies waiting wallets with `false` and discards the transactions without retrying.

Notes on usage:
- The validator is spawned once at startup with references to the mempool and DB actors. No external actor needs to send it messages directly.
- `StartMining` ticks are **ignored** while the actor is in `waitingForDbState` to prevent a second mining cycle from starting before the DB has acknowledged the current block.

## How it works (internals)

- **State**: `pendingTxs: List[PendingTx]` — the batch currently being mined, threaded through recursive `behavior` calls alongside stable references to `mempool`, `db`, and the three message adapters.
- **Mining loop**: `functions.Miner.mine(txsData, lastHash, timestamp, 0, DIFFICULTY)` increments a nonce until `sha256(txsData-lastHash-timestamp-nonce)` starts with `"0000"`. The function is `@tailrec` so arbitrarily large nonces do not overflow the stack.
- **Difficulty**: hardcoded to `"0000"` (four leading zeros).
- **Fee transactions**: for each `PendingTx`, a synthetic `SignedTransaction` is created with `from = originalTx.from`, `to = "0-0"`, `amount = originalTx.fees`, and `signature = "SYSTEM_FEE"`. These are appended to the real transactions before mining so fees are recorded on-chain.
- **Block construction**: `Block(currentId, allTxs, lastHash, timestamp)` where `allTxs = pendingTxs.map(_.tx) ++ feeTxs`.
- **Two-phase commit**: the actor sends `DB.SaveBlock` and immediately switches to `waitingForDbState`. Wallet notifications and mempool cleanup happen only after receiving `ConfirmSaved`, ensuring wallets are never told a transaction succeeded if persistence failed.

## State machine

```
         timer tick
              │
        ┌─────▼───────┐
        │ StartMining │──(empty txs)──► Behaviors.same
        └─────┬───────┘
              │ non-empty txs
        ┌─────▼───────────┐
        │  ProcessBlock   │ (query DB for last block)
        └─────┬───────────┘
              │
        ┌─────▼───────────┐
        │ ProcessMining   │ (run PoW, build block, send to DB)             
        └─────┬───────────┘
              │
        ┌─────▼───────────────┐
        │  waitingForDbState  │◄── ignores StartMining ticks
        └──┬──────────────────┘
           │             │
     ConfirmSaved    SaveFailed
           │             │
     notify wallets  notify wallets
     (true)          (false)
     clean mempool
```

## Error handling & logging

- **Empty mempool**: the actor logs nothing and simply waits for the next timer tick.
- **DB save failure**: logged with `ctx.log.error`; all wallets in the batch receive `false` and the transactions are dropped from `pendingTxs` without retrying.
- **Timer ticks during DB wait**: silently ignored via the catch-all `case _ => Behaviors.same` in `waitingForDbState`.

## Implementation notes and decisions

- Message adapters (`messageAdapter`) are used to convert `Mempool.Txs` and `DB.LastBlockInfo` replies into `Validator.Command` messages. This keeps the actor's mailbox type-safe — it only ever receives `Validator.Command` messages.
- The fee transaction carries `nonce = 0` and `fees = 0` because it is a synthetic system entry, not a user-initiated transfer.
- Wallet reply actors (`PendingTx.replyTo`) are stored in `pendingTxs` so the validator can send success/failure notifications directly back to the originating wallet without any intermediate actor.
