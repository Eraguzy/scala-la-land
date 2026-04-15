package blockchain

// Viewer en lecture seule: recharge l'état depuis le disque à chaque rafraîchissement.
// Il ne modifie pas l'état blockchain, il sert uniquement à l'observabilité.
object ViewerMain {
  def main(args: Array[String]): Unit = {
    val refreshMs = args.headOption.flatMap(_.toLongOption).getOrElse(1000L)

    while (true) {
      ConsoleRenderer.clearScreen()
      StateStore.loadSnapshot() match {
        case None =>
          println("Aucun état trouvé. Lance d'abord : sbt \"runMain blockchain.BootstrapMain\"")
        case Some(snapshot) =>
          val runtime = ActorRuntime.fromSnapshot(snapshot)
          try {
            println(ConsoleRenderer.render(runtime, StateStore.defaultRuntimeDir))
          } finally {
            runtime.shutdown()
          }
          println(s"Auto-refresh toutes les ${refreshMs} ms. Ctrl+C pour quitter.")
      }

      Thread.sleep(refreshMs)
    }
  }
}
