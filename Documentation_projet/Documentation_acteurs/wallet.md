
# Wallet actor

This document describes the `Wallet` actor used in the project: its public messages, internal behaviour, and examples of how other actors or external code should interact with it.

**Goals**
- Expose a simple API to create signed transactions and query wallet keys/balance.
- Ensure transaction ordering and consistency via an internal queue.

## Messages (how to use)

The `Wallet` protocol is defined in `messages/wallet.scala`. The public messages you will use are:

- `CreateTx(to: String, amount: BigInt, fee: BigInt, replyTo: ActorRef[Boolean])`
	- Public entry point to request a new transaction.
	- The actor first checks the effective balance (initial balance + chain activity) before signing and sending the transaction to the mempool.
	- The caller receives `true` on success, `false` on failure.

- `GetBalance(replyTo: ActorRef[BigInt])`
	- Requests the wallet's effective balance. The actor queries the DB and replies with a `BigInt`.

- `GetPublicKey(replyTo: ActorRef[String])`
	- Returns the wallet's public key as a `String`.

- `GetPrivateKey(replyTo: ActorRef[String])`
	- Returns the wallet's private key as a `String` (in a real life situation this obviously shouldn't exist lol, only provided for internal tooling).

Notes on usage:
- Always provide a `replyTo` `ActorRef` to receive the result. For HTTP handlers or test code you can spawn a short-lived adapter actor or use an `ask` pattern.
- `CreateTx` is safe to call concurrently: the actor serialises outgoing transactions with an internal queue so non-overlapping callers are handled in order.

## How it works (internals)

- State: each wallet maintains `n`, `pubInt`, `privInt`, `initialBalance`, `name`, `nonce`, and a `txInProgress` flag.
- Transaction flow:
	1. Caller sends `CreateTx`.
	2. If a transaction is already in progress the request is queued as a `PendingTx`.
	3. When processing, the actor asynchronously queries the DB for the wallet's chain balance (`GetBalance`).
	4. Once the balance is available (and valid e.g. enough funds), the actor constructs an `UnsignedTransaction`, computes its hash and signs it using the wallet's private integers via `Crypto.sign`.
	5. If signing succeeds, the actor sends `Mempool.AddTx(signed, replyTo)` and immediately deducts `amount + fee` from the local `initialBalance` and increments `nonce` to keep subsequent queued requests consistent.
	6. If insufficient funds or signing fails, the actor replies `false` to the original `replyTo` and continues with the next queued transaction.

- Queueing: queued transactions are processed FIFO. The queue ensures that concurrent requests don't observe stale local balance or nonce.

## Error handling & logging

- Insufficient funds: logged with `ctx.log.error` and the caller is replied with `false`.
- Signing failure: logged and the caller receives `false`.
- DB/mempool failures: mempool and DB actors are expected to reply using the provided `replyTo` actor refs; wallet handles those outcomes by replying `false` when appropriate.

## Implementation notes and decisions

- The wallet performs local balance deduction immediately after submitting a signed transaction to avoid race conditions with queued transactions.
- The actor splits `CreateTx` into two messages (`CreateTxInternal`) to accommodate asynchronous DB queries without blocking.
- Keys are simple integer-based values produced by `Crypto.genWalletInts()` and formatted as `"n-pubInt"` / `"n-privInt"`.