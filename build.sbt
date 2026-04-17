name := "blockchain-akka"

version := "0.1"

scalaVersion := "3.3.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
  "ch.qos.logback" % "logback-classic" % "1.4.14"
)