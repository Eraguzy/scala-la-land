
# DBActor (`actors/blockchain.scala`)

This document describes the `DBActor` actor (referred to as the "DB" or "blockchain" actor): its public messages, internal behaviour, and examples of how other actors should interact with it.

**Goals**
- Persist validated blocks to disk (`ledger.txt`) and serve them to the HTTP API.
- Compute per-wallet balances by replaying the on-disk ledger up to a given timestamp.
- Keep an in-memory list of blocks so the API can serve them without re-parsing the file on every request.

## Messages (how to use)

The `DBActor` protocol is defined in `messages/blockchain.scala`. The public messages you will use are:

- `SaveBlock(block: Block, replyTo: ActorRef[DB.Response])`
  - Appends a fully mined block to `ledger.txt` and updates the in-memory block list.
  - The caller receives `DB.Success` on success, or `DB.Failed(reason)` if an I/O error occurs.
  - Used by the `ValidatorActor` after a block has been successfully mined.

- `AppendBlock(block: Block, replyTo: ActorRef[DB.Response])`
  - Identical behaviour to `SaveBlock`. Both commands write the block to disk and reply with `DB.Success` or `DB.Failed`.

- `GetLastBlock(replyTo: ActorRef[DB.LastBlockInfo])`
  - Returns a `DB.LastBlockInfo(hash: String, id: Int)` describing the hash and index of the last persisted block.
  - Used by the `ValidatorActor` before mining so it can chain the new block correctly.

- `GetBalanceAtDate(publicKey: String, targetTimestamp: Long, replyTo: ActorRef[DB.BalanceResponse])`
  - Replays every transaction recorded in `ledger.txt` up to and including `targetTimestamp` and computes the net balance (credits − debits) for `publicKey`.
  - The caller receives a `DB.BalanceResponse(balance: Double)`.
  - Used by the `Wallet` actor to verify that a wallet has sufficient funds before signing a transaction.

- `GetAllBlocks(replyTo: ActorRef[DB.BlocksData])`
  - Returns the full in-memory `List[DB.BlockInfo]` as `DB.BlocksData(blocks)`.
  - Used by the HTTP API to serve the `/blocks` endpoint without touching the disk.

Notes on usage:
- All replies are sent to the provided `replyTo` ActorRef. Use an `ask` pattern or a message adapter when calling from another actor.
- The actor is started with `SupervisorStrategy.resume`: an unexpected exception keeps the current state intact rather than resetting the chain.

## How it works (internals)

- **State**: `lastHash: String`, `currentId: Int`, `blocks: List[DB.BlockInfo]` — all threaded through the recursive `behavior` function (no mutable variables).
- **Startup**: the actor deletes any existing `ledger.txt` on startup so each run begins with a clean chain. This avoids balance inconsistencies caused by keys from a previous session.
- **Block format on disk**:
  ```
  BLOCK|ID:<id>|PREV:<prevHash>|TS:<timestamp>
  TX|<from>|<to>|<amount>
  ---
  ```
  Each block header is followed by one `TX` line per transaction, then a `---` separator.
- **Hash chaining**: after a successful write, the actor computes `sha256(block.toString)` as the `lastHash` for the next block, increments `currentId`, and appends the new `BlockInfo` to the in-memory list before tail-calling `behavior(...)`.
- **Balance replay**: `GetBalanceAtDate` opens `ledger.txt`, tracks the timestamp of each `BLOCK|` line, and accumulates credits/debits for the requested key only for blocks whose timestamp ≤ `targetTimestamp`. The file is closed after the scan regardless of errors.

## Error handling & logging

- **I/O failure on write**: logged with `ctx.log.error`; `DB.Failed(reason)` is sent to the caller and the state is left unchanged (the block is not counted).
- **Balance scan error**: logged with `ctx.log.error`; `DB.BalanceResponse(0.0)` is returned so the caller degrades gracefully rather than hanging.

## Implementation notes and decisions

- Blocks are kept both on disk (for durability) and in memory (for fast API reads) — the two representations stay in sync because they are updated atomically in the same `behavior` transition.
- `Using(new PrintWriter(...))` is used for all file writes so the writer is always closed even if an exception is thrown mid-write.
- The actor uses `SupervisorStrategy.resume` (not `restart`) so a transient I/O error does not reset `lastHash`/`currentId` to their initial values and corrupt the chain.
