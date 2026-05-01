package functions

object Miner {

  // @tailrec is important here — the nonce can reach millions before a valid hash is found,
  // and a plain recursive call would blow the stack
  @scala.annotation.tailrec
  def mine(txsData: String, previousHash: String, timestamp: Long, nonce: Long, difficulty: String): (Long, String) = {
    val hash = Crypto.sha256(s"$txsData-$previousHash-$timestamp-$nonce")
    if (hash.startsWith(difficulty)) (nonce, hash)
    else mine(txsData, previousHash, timestamp, nonce + 1, difficulty)
  }
}
