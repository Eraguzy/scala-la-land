package functions

import java.security.MessageDigest
import objects.UnsignedTransaction
import java.math.BigInteger
import java.util.Random
import scala.util.Try

object Crypto {
  // string to be inputted in hash function representing the tx data
  private def prepareData(tx: UnsignedTransaction): String = {
    // should be bytes here be we keep it simple since it's not the main concern of this repo
    s"${tx.nonce}-${tx.from}-${tx.to}-${tx.amount}-${tx.fees}-${tx.timestamp}"
  }

  def sha256(text: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(text.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }

  def hashTx(tx: UnsignedTransaction): String = {
    val payload = prepareData(tx)
    sha256(payload)
  }

  private def parseKey(key: String): (BigInt, BigInt) = {
    val parts = key.split("-", 2)
    if (parts.length != 2) {
      throw new IllegalArgumentException("invalid key format, expected n-exp")
    }
    (BigInt(parts(0)), BigInt(parts(1)))
  }

  // RSA signature generation
  def sign(txHash: String, n: BigInt, d: BigInt): Option[String] = {
    val message = BigInt(txHash, 16) // base 16 since hash is hex string

    // textbook RSA signature: s = (msg)^d mod n
    Some(message.modPow(d, n).toString(16))
  }

  // RSA sig check
  def verify(
      tx: UnsignedTransaction,
      signature: String,
      publicKey: String
  ): Boolean = {
    Try {
      val (n, e) = parseKey(publicKey)
      val expectedHash = BigInt(hashTx(tx), 16).mod(n)
      val signatureInt = BigInt(signature, 16)

      // textbook RSA verification: m = s^e mod n
      val recoveredHash = signatureInt.modPow(e, n)
      recoveredHash == expectedHash
    }.getOrElse(false)
  }

  // use RSA to generate wallet keys (n, pubInt, privInt)
  def genWalletInts(): (BigInt, BigInt, BigInt) = {
    val (prime1, prime2) = Utils.genTwoDiffPrimes()
    val n = prime1 * prime2
    val phi = (prime1 - 1) * (prime2 - 1)

    val e = Utils.findE(phi)
    val f: BigInt = e.getOrElse(BigInt(65537))

    val d = f.modInverse(phi) // d = e^-1 mod phi

    (n, f, d)
  }
}
