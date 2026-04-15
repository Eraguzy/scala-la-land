package blockchain

import java.nio.charset.StandardCharsets
import java.security.{KeyFactory, KeyPairGenerator, MessageDigest, PrivateKey, PublicKey, Signature}
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.util.Base64

// Boite a outils cryptographique minimale utilisee par les wallets et le ledger.
object CryptoUtils {
  private val b64Encoder = Base64.getEncoder
  private val b64Decoder = Base64.getDecoder

  // SHA-256 est utilise pour le hash des blocs et comme mecanisme de secours legacy.
  def sha256(input: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(input.getBytes(StandardCharsets.UTF_8)).map("%02x".format(_)).mkString
  }

  def generateKeyPair(): (String, String) = {
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)
    val keyPair = generator.generateKeyPair()
    val publicKey = b64Encoder.encodeToString(keyPair.getPublic.getEncoded)
    val privateKey = b64Encoder.encodeToString(keyPair.getPrivate.getEncoded)
    // Les cles sont renvoyees en DER + Base64 pour faciliter la persistence snapshot.
    (publicKey, privateKey)
  }

  // Signe un payload avec la cle privee RSA (format PKCS8 encode Base64).
  def sign(payload: String, privateKeyBase64: String): String = {
    val privateKey = toPrivateKey(privateKeyBase64)
    val signer = Signature.getInstance("SHA256withRSA")
    signer.initSign(privateKey)
    signer.update(payload.getBytes(StandardCharsets.UTF_8))
    b64Encoder.encodeToString(signer.sign())
  }

  // Verifie une signature RSA avec la cle publique (format X509 encode Base64).
  def verify(payload: String, signatureBase64: String, publicKeyBase64: String): Boolean = {
    try {
      val publicKey = toPublicKey(publicKeyBase64)
      val verifier = Signature.getInstance("SHA256withRSA")
      verifier.initVerify(publicKey)
      verifier.update(payload.getBytes(StandardCharsets.UTF_8))
      verifier.verify(b64Decoder.decode(signatureBase64))
    } catch {
      case _: Throwable => false
    }
  }

  private def toPrivateKey(privateKeyBase64: String): PrivateKey = {
    val bytes = b64Decoder.decode(privateKeyBase64)
    val spec = new PKCS8EncodedKeySpec(bytes)
    KeyFactory.getInstance("RSA").generatePrivate(spec)
  }

  private def toPublicKey(publicKeyBase64: String): PublicKey = {
    val bytes = b64Decoder.decode(publicKeyBase64)
    val spec = new X509EncodedKeySpec(bytes)
    KeyFactory.getInstance("RSA").generatePublic(spec)
  }
}
