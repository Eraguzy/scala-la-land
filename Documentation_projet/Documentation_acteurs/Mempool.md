# MempoolActor (`actors/mempool.scala`)

## Messaging Actors and Protocols

**MempoolActor** (`actors/mempool.scala`)

- Received commands: `AddTx`, `GetTxs`, `RemoveTxs`, `ViewPending`
- Monitoring: `Behaviors.supervise(...).onFailure[Exception](SupervisorStrategy.restart)`
- Immutable state managed recursively with `behavior(txs: List[PendingTx])`
- In-memory storage only: no persistence to disk, the mempool is rebuilt empty after a reboot
- Signature verification before acceptance with `Crypto.verify(...)`
- Sorting of transactions by descending fee with `.sortBy(...)(Ordering[BigInt].reverse)`
- Sending to Validator: a maximum batch of 2 transactions with `splitAt(2)`

- Clearly distinguishes between reading (`GetTxs`, `ViewPending`) and deleting (`RemoveTxs`)

## Global Actor Function

This actor represents the **mempool**, i.e., the queue of transactions not yet processed. Its role is to receive signed transactions, verify their validity, store them in memory in order of priority, and then forward them to the validator on demand.

Important point: it never directly modifies an internal variable with `var`. Instead, it stores its state in the `txs` parameter of the `behavior` function, and then recreates a new behavior with each modification.

```scala
def apply(): Behavior[Mempool.Command] =

Behaviors.supervise(behavior(List.empty))

.onFailure[Exception](SupervisorStrategy.restart)

```

Here, at startup, the actor creates an empty mempool with `List.empty`. Then, it is supervised: if an exception occurs, the actor restarts automatically, which also resets the transaction list to zero.

## State Management

The core of the actor is located here:

```scala
private def behavior(txs: List[PendingTx]): Behavior[Mempool.Command] =

Behaviors.receive { (ctx, msg) =>

msg match {
...

}

}
```

This function represents the current state of the mempool. The `txs` parameter contains all pending transactions at that precise moment.

When a transaction is added or deleted, the actor does not modify the current list: it calculates a new list and then calls `behavior(updatedList)`. This is precisely what guarantees an immutable state while preserving the storage logic.

## Message Details

### `AddTx(signedTx, replyTo)`

This message adds a signed transaction to the mempool.

```scala
case Mempool.AddTx(signedTx, replyTo) =>

val isValid = Crypto.verify(signedTx.tx, signedTx.signature, signedTx.tx.from)

```

The first step is to verify the signature. The actor calls `Crypto.verify(...)` with:

- the raw transaction data `signedTx.tx`

- the signature `signedTx.signature`

- the issuer's public key `signedTx.tx.from`

If the signature is invalid, the transaction is immediately rejected:

```scala
replyTo ! false
Behaviors.same
```

Here, `replyTo ! false` sends a negative response to the actor who requested the addition. Then, `Behaviors.same` means that the initial state is preserved, so the transaction is not added to the mempool.

If the signature is valid, the transaction is encapsulated in a `PendingTx` object, added to the list, and then sorted by descending fees:

```scala
val updated = (PendingTx(signedTx, replyTo) :: txs)

.sortBy(_.tx.tx.fees)(Ordering[BigInt].reverse)
```

The `::` places the item at the top of the list. Then, the sorting reorganizes all transactions in descending order of fees. In practice, this simulates a small priority queue: the transactions most valuable for mining are processed first.

Finally, the actor returns a new behavior:

```scala
behavior(updated)
```

Thus, the return value is not a business value, but a **new state of the actor** containing the updated mempool.

### `GetTxs(replyTo)`

This message is used by the validator to request transactions to mine.

```scala
case Mempool.GetTxs(replyTo) =>

val (toSend, _) = txs.splitAt(2)
```

`splitAt(2)` splits the list into two parts:

- `toSend` contains the first two transactions
- the rest is ignored here

It is important to note that the transactions **are not deleted at this stage**. The actor only performs a partial read of the queue.

Then, it responds to the requester with:

```scala
replyTo ! Mempool.Txs(toSend)
```

If the mempool is empty, it still sends a response, but with an empty list:

```scala
replyTo ! Mempool.Txs(List.empty)

```

Therefore, in all cases, the validator receives a response. This prevents an actor from waiting indefinitely without a response.

### `RemoveTxs(confirmedTxs)`

This message is used to clear the mempool after validation and confirmation in the database.

``scala
case Mempool.RemoveTxs(confirmedTxs) =>

val remaining = txs.filterNot(t => confirmedTxs.exists(_.txId == t.tx.txId))

```

Here, the actor compares the values
