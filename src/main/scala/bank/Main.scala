package bank

import akka.actor.typed.ActorSystem

object Main extends App {
  println("Starting Blockchain Akka System...")

  val system = ActorSystem(DBBlockchain(), "blockchain-system")

  // Vous pouvez ajouter le code de démonstration ici
  println("Blockchain system initialized")

  // Laisser le système tourner
  sys.addShutdownHook(system.terminate())
}
