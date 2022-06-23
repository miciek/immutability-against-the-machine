lazy val root = (project in file("."))
  .settings(
    name            := "immutability-against-the-machine",
    organization    := "Michał Płachta",
    version         := "1.0",
    scalaVersion    := "3.1.2",
    scalacOptions ++= List("-unchecked"),
    libraryDependencies ++= Seq(
      "org.typelevel"  %% "cats-effect"      % "3.3.12",
      // imperative libraries:
      "org.apache.jena" % "apache-jena-libs" % "4.5.0",
      "org.apache.jena" % "jena-fuseki-main" % "4.5.0",
      "org.slf4j"       % "slf4j-nop"        % "2.0.0-alpha6"
    ),
    initialCommands := s"""
      import cats.effect._, cats.implicits._, cats.effect.unsafe.implicits.global
      import scala.concurrent.duration._, java.util.concurrent._
      import scala.jdk.javaapi.CollectionConverters.asScala
      import org.apache.jena.query._, org.apache.jena.rdfconnection._
    """,
    run / fork      := true,
    run / javaOptions += "-ea"
  )
