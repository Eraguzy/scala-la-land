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
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
    ),
  )
