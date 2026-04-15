error id: file:///H:/Documents/dev/scala-la-land/src/main/scala/bank/Main.scala:ActorSystem.
file:///H:/Documents/dev/scala-la-land/src/main/scala/bank/Main.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -akka/actor/typed/ActorSystem.
	 -akka/actor/typed/ActorSystem#
	 -akka/actor/typed/ActorSystem().
	 -ActorSystem.
	 -ActorSystem#
	 -ActorSystem().
	 -scala/Predef.ActorSystem.
	 -scala/Predef.ActorSystem#
	 -scala/Predef.ActorSystem().
offset: 152
uri: file:///H:/Documents/dev/scala-la-land/src/main/scala/bank/Main.scala
text:
```scala
package bank

import akka.actor.typed.ActorSystem

object Main extends App {
  println("Starting Blockchain Akka System...")

  val system = Acto@@rSystem(DBBlockchain(), "blockchain-system")

  // Vous pouvez ajouter le code de démonstration ici
  println("Blockchain system initialized")

  // Laisser le système tourner
  sys.addShutdownHook(system.terminate())
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 