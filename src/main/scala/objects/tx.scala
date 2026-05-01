package objects

import akka.actor.typed.ActorRef

case class UnsignedTransaction(
    from: String,      // sender's public key  (format: "n-e")
    to: String,        // recipient's public key
    amount: BigInt,
    fees: BigInt,      // higher fee = higher priority in the mempool queue
    nonce: Long,       // how many txs this wallet has sent so far — prevents replay attacks
    timestamp: Long
)

case class SignedTransaction(
    tx: UnsignedTransaction,
    txId: String,      // SHA-256 of the unsigned tx fields
    signature: String  // txId signed with the sender's private key
)

// wraps a signed tx with a callback so the wallet knows if it was accepted or rejected
case class PendingTx(tx: SignedTransaction, replyTo: ActorRef[Boolean])
