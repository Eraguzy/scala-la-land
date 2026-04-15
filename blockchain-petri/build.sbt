ThisBuild / version := "0.2.0"
ThisBuild / scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .settings(
    name := "blockchain-petri",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.8.8",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )
