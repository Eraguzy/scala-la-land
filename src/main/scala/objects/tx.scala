package objects

import java.util.UUID
import akka.actor.typed.ActorRef

case class UnsignedTransaction(
    from: String, // public key of the sender
    to: String, // public key of the recipient
    amount: BigInt, // amount
    fees: BigInt, // fees for the priority queue
    nonce: Long, // number of previous transactions sent by this wallet
    timestamp: Long // creation date
)

case class SignedTransaction(
    tx: UnsignedTransaction,
    txId: String, // should be Crypto.hashTx(tx)
    signature: String
)

// each tx in the mempool is associated to a replyTo to confirm if it was accepted or not
case class PendingTx(tx: SignedTransaction, replyTo: ActorRef[Boolean])
