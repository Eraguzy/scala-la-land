package bank

import java.time.Instant
import scala.math.BigDecimal

/**
 * Modèle de données pour la Blockchain
 */

/** Transaction avec signature */
case class Transaction(
  sender: String,        // Clé publique du sender
  receiver: String,      // Clé publique du receiver
  amount: BigDecimal,    // Montant à transférer
  fees: BigDecimal,      // Frais de transaction (pour le tri en Priority Queue)
  timestamp: Long,       // Timestamp de création
  nonce: Int             // Nonce pour éviter les rejeux
) {
  /**
   * Génère un hash SHA-256 du contenu de la transaction (sans signature)
   */
  def toHash: String = {
    val content = s"$sender|$receiver|$amount|$fees|$timestamp|$nonce"
    hashString(content)
  }

  private def hashString(str: String): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(str.getBytes("UTF-8"))
    hash.map("%02x".format(_)).mkString
  }
}

/** Transaction signée */
case class SignedTransaction(
  transaction: Transaction,
  signature: String,      // Signature générée avec la clé privée
  publicKey: String       // Clé publique du sender
)

/** Block dans la Blockchain */
case class Block(
  id: Long,                           // Identifiant séquentiel du block
  transactions: List[SignedTransaction], // Transactions minées
  previousBlockHash: String,          // Hash du block précédent
  proofOfWork: Long,                  // i tel que Hash(data + i) commence par "000"
  timestamp: Long                     // Timestamp de création
) {
  /**
   * Calcule le hash du bloc actuel
   */
  def hash: String = {
    val content = s"$id|${transactions.map(_.transaction.toHash).mkString(",")}|$previousBlockHash|$proofOfWork|$timestamp"
    hashString(content)
  }

  private def hashString(str: String): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(str.getBytes("UTF-8"))
    hash.map("%02x".format(_)).mkString
  }
}

/** État d'un compte dans la Blockchain */
case class Account(
  publicKey: String,
  balance: BigDecimal
)

/** Exception pour les opérations invalides */
case class TransactionValidationException(msg: String) extends Exception(msg)
case class InsufficientFundsException(msg: String) extends Exception(msg)
