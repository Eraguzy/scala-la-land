error id: file:///H:/Documents/dev/scala-la-land/src/main/scala/bank/Actors.scala:
file:///H:/Documents/dev/scala-la-land/src/main/scala/bank/Actors.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -WalletCommand#
	 -scala/Predef.WalletCommand#
offset: 1212
uri: file:///H:/Documents/dev/scala-la-land/src/main/scala/bank/Actors.scala
text:
```scala
package bank

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import scala.collection.mutable
import scala.math.BigDecimal
import java.util.PriorityQueue
import java.util.Comparator

/**
 * Cryptographie simple pour les tests (utiliser une vraie lib en production)
 */
object Crypto {
  /**
   * Simule une signature avec la clé privée
   * En production, utiliser RSA ou ECDSA
   */
  def sign(message: String, privateKey: String): String = {
    val combined = message + privateKey
    hashString(combined)
  }

  /**
   * Vérifie une signature avec la clé publique
   */
  def verify(message: String, signature: String, publicKey: String): Boolean = {
    val combined = message + publicKey
    hashString(combined) == signature
  }

  private def hashString(str: String): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(str.getBytes("UTF-8"))
    hash.map("%02x".format(_)).mkString
  }
}

/**
 * WalletActor : Crée et signe des transactions
 */
object WalletActor {
  def apply(publicKey: String, privateKey: String): Behavior[WalletComm@@and] =
    Behaviors.receive { (context, message) =>
      message match {
        case CreateTransaction(receiver, amount, fees, replyTo) =>
          context.log.info(s"[$publicKey] Creating transaction to $receiver for $amount")
          
          val transaction = Transaction(
            sender = publicKey,
            receiver = receiver,
            amount = amount,
            fees = fees,
            timestamp = System.currentTimeMillis(),
            nonce = scala.util.Random.nextInt(10000)
          )

          val messageHash = transaction.toHash
          val signature = Crypto.sign(messageHash, privateKey)

          val signedTransaction = SignedTransaction(
            transaction = transaction,
            signature = signature,
            publicKey = publicKey
          )

          replyTo ! TransactionCreated(Some(signedTransaction), None)

          Behaviors.same

        case QueryBalance(replyTo) =>
          context.log.info(s"[$publicKey] Querying balance")
          replyTo ! BalanceResponse(BigDecimal(1000), publicKey)
          Behaviors.same
      }
    }
}

/**
 * MempoolActor : Gère une file d'attente des transactions en attente de minage
 * Trie par fees (les plus élevés en premier)
 */
object MempoolActor {
  def apply(): Behavior[MempoolCommand] = {
    // Comparateur pour trier les transactions par fees décroissants
    val comparator: Comparator[SignedTransaction] = new Comparator[SignedTransaction] {
      override def compare(tx1: SignedTransaction, tx2: SignedTransaction): Int =
        tx2.transaction.fees.compare(tx1.transaction.fees)  // Reverse order (highest first)
    }

    val mempool = new PriorityQueue[SignedTransaction](comparator)

    Behaviors.receive { (context, message) =>
      message match {
        case SubmitTransaction(signedTx, replyTo) =>
          context.log.info(
            s"[Mempool] Received transaction from ${signedTx.publicKey} to ${signedTx.transaction.receiver} " +
            s"(fees: ${signedTx.transaction.fees})"
          )

          // Vérifier la signature
          val messageHash = signedTx.transaction.toHash
          val isValid = Crypto.verify(messageHash, signedTx.signature, signedTx.publicKey)

          if (isValid) {
            mempool.add(signedTx)
            context.log.info(s"[Mempool] Transaction added. Queue size: ${mempool.size()}")
            replyTo ! TransactionSubmitResponse(true, "Transaction added to mempool")
          } else {
            context.log.error(s"[Mempool] Invalid signature for transaction from ${signedTx.publicKey}")
            replyTo ! TransactionSubmitResponse(false, "Invalid signature")
          }

          Behaviors.same

        case RequestTransactions(count, replyTo) =>
          context.log.info(s"[Mempool] Validator requested $count transactions (${mempool.size()} available)")

          val requested = scala.collection.mutable.ListBuffer[SignedTransaction]()
          for (_ <- 0 until math.min(count, mempool.size())) {
            requested += mempool.poll()
          }

          replyTo ! TransactionsResponse(requested.toList, "mempool")
          Behaviors.same

        case DeleteCompletedTransactions(transactions, replyTo) =>
          context.log.info(s"[Mempool] Removing ${transactions.length} completed transactions")
          
          // Les transactions ont déjà été retirées via RequestTransactions
          // Mais on peut implémenter une logique plus robuste ici
          val deletedCount = transactions.length

          replyTo ! DeletionAckowledged(true, deletedCount)
          Behaviors.same
      }
    }
  }
}

/**
 * ValidatorActor : Mine les blocs (Proof of Work)
 * Cherche un hash commençant par "000"
 */
object ValidatorActor {
  def apply(dbRef: ActorRef[DBCommand], mempoolRef: ActorRef[MempoolCommand]): Behavior[ValidatorCommand] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage { message =>
        message match {
          case RequestBlockData(replyTo) =>
            context.log.info("[Validator] Requesting last block from DB")
            
            // Créer un adapter pour convertir LastBlockResponse en ValidatorCommand
            val adapter: ActorRef[LastBlockResponse] = context.messageAdapter[LastBlockResponse] { response =>
              ProcessBlockData(BlockDataResponse(response.blockHash, response.blockId), replyTo)
            }
            
            dbRef ! GetLastBlock(adapter)

            Behaviors.same

          case ProcessBlockData(blockData, replyTo) =>
            context.log.info(s"[Validator] Received last block: ${blockData.lastBlockId}")
            replyTo ! blockData
            Behaviors.same

          case ProposeNewBlock(block, replyTo) =>
            context.log.info(s"[Validator] Proposing new block ${block.id} with ${block.transactions.length} transactions")
            
            // Adapter pour convertir BlockAppendResponse 
            val adapter: ActorRef[BlockAppendResponse] = context.messageAdapter[BlockAppendResponse] { response =>
              BlockAppendResult(response, replyTo)
            }
            
            dbRef ! AppendBlock(block, adapter)

            Behaviors.same

          case BlockAppendResult(response, replyTo) =>
            replyTo ! response
            Behaviors.same

          case StartMining =>
            context.log.info("[Validator] Mining started")
            Behaviors.same

          case _ =>
            Behaviors.same
        }
      }
    }
}

/**
 * DBBlockchain : Stocke les blocs et gère l'état de la blockchain
 */
object DBBlockchain {
  def apply(): Behavior[DBCommand] = {
    val blocks = mutable.ListBuffer[Block]()
    val accounts = mutable.Map[String, BigDecimal]()

    // Initialiser avec un bloc Genesis
    val genesisBlock = Block(
      id = 0,
      transactions = List(),
      previousBlockHash = "genesis",
      proofOfWork = 0,
      timestamp = System.currentTimeMillis()
    )
    blocks += genesisBlock

    // Initialiser quelques comptes pour les tests
    accounts("wallet1") = BigDecimal(1000)
    accounts("wallet2") = BigDecimal(1000)

    Behaviors.receive { (context, message) =>
      message match {
        case GetLastBlock(replyTo) =>
          val lastBlock = blocks.last
          context.log.info(s"[DB] Returning last block: ${lastBlock.id}")
          replyTo ! LastBlockResponse(Some(lastBlock), lastBlock.hash, lastBlock.id)
          Behaviors.same

        case AppendBlock(newBlock, replyTo) =>
          context.log.info(s"[DB] Appending block ${newBlock.id} with ${newBlock.transactions.length} transactions")
          
          // Valider le PoW
          if (isValidProofOfWork(newBlock)) {
            blocks += newBlock

            // Mettre à jour les soldes
            for (signedTx <- newBlock.transactions) {
              val tx = signedTx.transaction
              val senderBalance = accounts.getOrElse(tx.sender, BigDecimal(0))
              val receiverBalance = accounts.getOrElse(tx.receiver, BigDecimal(0))

              accounts(tx.sender) = senderBalance - tx.amount - tx.fees
              accounts(tx.receiver) = receiverBalance + tx.amount
            }

            context.log.info(s"[DB] Block appended successfully. Total blocks: ${blocks.length}")
            replyTo ! BlockAppendResponse(true, newBlock.id, "Block appended")
          } else {
            context.log.error(s"[DB] Invalid PoW for block ${newBlock.id}")
            replyTo ! BlockAppendResponse(false, -1, "Invalid PoW")
          }

          Behaviors.same

        case GetBalance(publicKey, replyTo) =>
          val balance = accounts.getOrElse(publicKey, BigDecimal(0))
          context.log.info(s"[DB] Balance for $publicKey: $balance")
          replyTo ! BalanceResponse(balance, publicKey)
          Behaviors.same

        case GetAllBlocks(replyTo) =>
          context.log.info(s"[DB] Returning all blocks (${blocks.length} total)")
          replyTo ! AllBlocksResponse(blocks.toList)
          Behaviors.same
      }
    }
  }

  /**
   * Valide que le PoW du bloc est correct
   * Le hash doit commencer par "000"
   */
  private def isValidProofOfWork(block: Block): Boolean = {
    val content = s"${block.transactions.map(_.transaction.toHash).mkString(",")}|${block.previousBlockHash}|${block.proofOfWork}"
    val hash = hashString(content)
    hash.startsWith("0")
  }

  private def hashString(str: String): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(str.getBytes("UTF-8"))
    hash.map("%02x".format(_)).mkString
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 