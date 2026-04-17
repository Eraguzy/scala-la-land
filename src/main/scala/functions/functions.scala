// temporary file
// files in this folder should be functions implementing the logics
package functions

import java.security.MessageDigest

object Crypto {
  def sha256(text: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(text.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }

  // Pour simuler la signature du Wallet mentionnée dans ton diagramme
  def sign(data: String, privKey: String): String = sha256(data + privKey)
}