name := "blockchain-akka"

version := "0.1"

scalaVersion := "3.3.1"

val AkkaVersion     = "2.8.5"
val AkkaHttpVersion = "10.5.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed"       % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream"             % AkkaVersion,
  "com.typesafe.akka" %% "akka-http"               % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json"    % AkkaHttpVersion,
  "ch.qos.logback"    %  "logback-classic"         % "1.4.14"
)