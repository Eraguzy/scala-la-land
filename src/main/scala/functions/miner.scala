package functions

object Miner {

  @scala.annotation.tailrec
  def mine(txsData: String, previousHash: String, timestamp: Long, nonce: Long, difficulty: String): (Long, String) = {
    
    val dataToHash = s"$txsData-$previousHash-$timestamp-$nonce"
    val hash = Crypto.sha256(dataToHash)

    if (hash.startsWith(difficulty)) {
      (nonce, hash)
    } else {
      mine(txsData, previousHash, timestamp, nonce + 1, difficulty) // Appel récursif
    }
  }
}