package blockchain

import ValidatorMessage._

// Point d'entrée CLI qui demande à un validateur de miner un bloc unique.
// Les paramètres logEvery/delay servent uniquement à contrôler la verbosité de sortie.
object ValidatorMinerMain {
  def main(args: Array[String]): Unit = {
    val validatorAddress = args.headOption.getOrElse("validator-1")
    val logEvery = args.lift(1).flatMap(_.toLongOption).getOrElse(1L)
    val attemptDelayMs = args.lift(2).flatMap(_.toLongOption).getOrElse(0L)

    val result = StateStore.withLockedRuntime() { runtime =>
      runtime.validator(validatorAddress) match {
        case None      => Left(s"Validateur introuvable : $validatorAddress")
        case Some(ref) => ref.ask(MineOnce(logEvery, attemptDelayMs, _))
      }
    }

    result match {
      case None =>
        println("Aucun état trouvé. Lance d'abord : sbt \"runMain blockchain.BootstrapMain\"")
      case Some(Left(error)) =>
        println(error)
      case Some(Right(block)) =>
        println("Bloc ajouté avec succès à la blockchain partagée.")
        println(s"Bloc miné : #${block.index} | hash=${block.hash} | nonce=${block.nonce}")
    }
  }
}
