package functions

import java.security.MessageDigest
import objects.UnsignedTransaction

object Crypto {

  private def prepareData(tx: UnsignedTransaction): String = {
    s"${tx.id}-${tx.from}-${tx.to}-${tx.amount}-${tx.fees}-${tx.timestamp}"
  }

  def sha256(text: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(text.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }

  def sign(tx: UnsignedTransaction, privateKey: String): String = {
    val payload = prepareData(tx)
    val hash = sha256(payload)
    s"$hash-signed-by-$privateKey"
  }

  def verify(tx: UnsignedTransaction, signature: String, publicKey: String): Boolean = {
    //recalcul du hash local
    val payload = prepareData(tx)
    val localHash = sha256(payload)

    if (!signature.contains("-signed-by-")) return false

    val extractedHash = signature.split("-signed-by-").head
    val signerKey = signature.split("-signed-by-").last

    // Le hash correspond-il ? ET la clé utilisée est-elle bien celle du sender ?
    localHash == extractedHash && signerKey == publicKey
  }
}