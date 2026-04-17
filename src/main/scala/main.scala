package blockchain

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import actors._
import messages._

object Main {
  def main(args: Array[String]): Unit = {


    val system = ActorSystem(Behaviors.setup[Nothing] { ctx =>
      // 1. Spawn de la DB et de la Mempool
      val db = ctx.spawn(DBActor(), "blockchain-db")
      val mempool = ctx.spawn(MempoolActor(), "mempool")

      // 2. Spawn du Wallet (Alice commence avec 100)
      val walletAlice = ctx.spawn(
        WalletActor("alice_pub", "alice_priv", 100, mempool),
        "wallet-alice"
      )

      // 3. Spawn du Validator (Il a besoin de connaître la Mempool et la DB)
      val validator = ctx.spawn(ValidatorActor(mempool, db), "validator")

      // --- SCÉNARIO DE TEST ---
      // Alice crée une transaction
      walletAlice ! Wallet.CreateTx("bob_pub", 30)

      // On attend un peu et on lance le minage
      ctx.scheduleOnce(scala.concurrent.duration.Duration(2, "seconds"), validator, Validator.StartMining)

      Behaviors.empty
    }, "blockchain-system")
  }
}