package blockchain

import scala.util.Try
import WalletDirectoryMessage._
import MempoolMessage._

object TransactionCliMain {
  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      println("Usage : sbt \"runMain blockchain.TransactionCliMain <from> <to> <amount>\"")
      return
    }

    val from = args(0)
    val to = args(1)
    val amountOpt = Try(BigDecimal(args(2))).toOption

    if (amountOpt.isEmpty) {
      println(s"Montant invalide : ${args(2)}")
      return
    }

    val result = StateStore.withLockedRuntime() { runtime =>
      val pending = runtime.mempool.ask(GetPendingOutgoing(from, _))
      runtime.walletDirectory.ask(CreateTransaction(from, to, amountOpt.get, pending, _)) match {
        case Left(error) => Left(error)
        case Right(tx)   => runtime.mempool.ask(TryAddTransaction(tx, runtime.walletDirectory, _)).map(_ => tx)
      }
    }

    result match {
      case None =>
        println("Aucun état trouvé. Lance d'abord : sbt \"runMain blockchain.BootstrapMain\"")
      case Some(Left(error)) =>
        println(error)
      case Some(Right(tx)) =>
        println("Transaction ajoutée à la mempool :")
        println(s"${tx.from} -> ${tx.to} | amount=${Transaction.formatAmount(tx.amount)} | signature=${tx.signature}")
    }
  }
}
