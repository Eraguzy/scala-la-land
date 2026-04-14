package blockchain

object BootstrapMain {
  def main(args: Array[String]): Unit = {
    val snapshot = DemoData.initialSnapshot()
    StateStore.initialize(snapshot, overwrite = true)

    val runtime = ActorRuntime.fromSnapshot(snapshot)

    println("État initial créé dans le dossier runtime/.")
    println("Commandes conseillées :")
    println("  1) sbt \"runMain blockchain.ViewerMain\"")
    println("  2) sbt \"runMain blockchain.TransactionCliMain alice bob 12\"")
    println("  3) sbt \"runMain blockchain.ValidatorMinerMain validator-1\"")
    println()
    println(ConsoleRenderer.render(runtime, StateStore.defaultRuntimeDir))
  }
}
