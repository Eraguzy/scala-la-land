package functions

import java.security.MessageDigest
import objects.UnsignedTransaction
import java.math.BigInteger
import java.util.Random
import scala.util.Try

object Crypto {

  // we concatenate fields as a plain string for simplicity
  // in production you'd serialize to bytes to avoid any ambiguity between fields
  private def prepareData(tx: UnsignedTransaction): String =
    s"${tx.nonce}-${tx.from}-${tx.to}-${tx.amount}-${tx.fees}-${tx.timestamp}"

  def sha256(text: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(text.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }

  def hashTx(tx: UnsignedTransaction): String = sha256(prepareData(tx))

  private def parseKey(key: String): (BigInt, BigInt) = {
    val parts = key.split("-", 2)
    if (parts.length != 2)
      throw new IllegalArgumentException("invalid key format, expected n-exp")
    (BigInt(parts(0)), BigInt(parts(1)))
  }

  // textbook RSA — no padding, not production-safe, but sufficient for this project
  def sign(txHash: String, n: BigInt, d: BigInt): Option[String] = {
    val message = BigInt(txHash, 16)   // hash is a hex string
    Some(message.modPow(d, n).toString(16))
  }

  def verify(tx: UnsignedTransaction, signature: String, publicKey: String): Boolean =
    Try {
      val (n, e)         = parseKey(publicKey)
      val expectedHash   = BigInt(hashTx(tx), 16).mod(n)
      val signatureInt   = BigInt(signature, 16)
      val recoveredHash  = signatureInt.modPow(e, n)
      recoveredHash == expectedHash
    }.getOrElse(false)

  def genWalletInts(): (BigInt, BigInt, BigInt) = {
    val (p, q) = Utils.genTwoDiffPrimes()
    val n      = p * q
    val phi    = (p - 1) * (q - 1)
    val e      = Utils.findE(phi).getOrElse(BigInt(65537)) // 65537 is the standard RSA public exponent
    val d      = e.modInverse(phi)
    (n, e, d)
  }
}
