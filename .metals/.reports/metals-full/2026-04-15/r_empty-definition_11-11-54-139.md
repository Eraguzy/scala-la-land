error id: file:///H:/Documents/dev/scala-la-land/build.sbt:
file:///H:/Documents/dev/scala-la-land/build.sbt
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -Dependencies.Seq.
	 -Dependencies.Seq#
	 -Dependencies.Seq().
	 -Seq.
	 -Seq#
	 -Seq().
	 -scala/Predef.Seq.
	 -scala/Predef.Seq#
	 -scala/Predef.Seq().
offset: 733
uri: file:///H:/Documents/dev/scala-la-land/build.sbt
text:
```scala
import Dependencies._

ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "blockchain.akka"

lazy val root = (project in file("."))
  .settings(
    name := "scala-la-land",
    version := "0.1.0",
    libraryDependencies ++= Seq(
      // Akka
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.4.12",
      
      // Serialization
      "com.google.protobuf" % "protobuf-java" % "3.24.4",
      
      // Testing
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    ),
    scalacOptions ++= @@Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
    ),
  )

```


#### Short summary: 

empty definition using pc, found symbol in pc: 