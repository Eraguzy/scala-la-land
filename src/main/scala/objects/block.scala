// chained by hashes in blockchain
package objects

// Importation nécessaire car Block utilise SignedTransaction
import objects.SignedTransaction

case class Block(
                  id: Long,
                  transactions: List[SignedTransaction],
                  previousHash: String,
                  timestamp: Long
                )