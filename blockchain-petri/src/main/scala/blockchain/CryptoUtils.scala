package blockchain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object CryptoUtils {
  def sha256(input: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(input.getBytes(StandardCharsets.UTF_8)).map("%02x".format(_)).mkString
  }
}
