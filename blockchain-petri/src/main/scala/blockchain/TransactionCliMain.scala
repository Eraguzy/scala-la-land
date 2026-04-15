package blockchain

import scala.util.Try
import WalletDirectoryMessage._
import MempoolMessage._

// Point d'entrée CLI pour soumettre une transaction à la mempool.
object TransactionCliMain {
  def main(args: Array[String]): Unit = {
    if (args.length < 3 || args.length > 4) {
      println("Usage : sbt \"runMain blockchain.TransactionCliMain <from> <to> <amount> [fees]\"")
      return
    }

    val from = args(0)
    val to = args(1)
    val amountOpt = Try(BigDecimal(args(2))).toOption
    // Si l'utilisateur n'indique pas de frais, on applique une valeur par défaut
    // afin de conserver un flux de démo simple.
    val feesOpt = args.lift(3).map(value => Try(BigDecimal(value)).toOption).getOrElse(Some(BigDecimal(0.1)))

    if (amountOpt.isEmpty) {
      println(s"Montant invalide : ${args(2)}")
      return
    }
    if (feesOpt.isEmpty) {
      println(s"Frais invalides : ${args(3)}")
      return
    }

    val result = StateStore.withLockedRuntime() { runtime =>
      runtime.walletDirectory.ask(SubmitTransactionFromWallet(from, to, amountOpt.get, feesOpt.get, runtime.mempool, _))
    }

    result match {
      case None =>
        println("Aucun état trouvé. Lance d'abord : sbt \"runMain blockchain.BootstrapMain\"")
      case Some(Left(error)) =>
        println(error)
      case Some(Right(tx)) =>
        println("Transaction ajoutée à la mempool :")
        println(
          s"${tx.from} -> ${tx.to} | amount=${Transaction.formatAmount(tx.amount)} | fees=${Transaction.formatAmount(tx.fees)} | ts=${tx.timestamp} | signature=${tx.signature}"
        )
    }
  }
}
