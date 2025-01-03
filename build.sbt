import kotlin.Keys.{kotlinLib, kotlinVersion, kotlincJvmTarget}

ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.4"

val circeVersion = "0.14.10"
val ktorVersion  = "3.0.3"

// Common settings and dependencies
lazy val commonSettings = Seq(
  organization := "com.tjclp.xlcr",
  idePackagePrefix := Some("com.tjclp.xlcr"),

  libraryDependencies ++= Seq(
    // Common logging
    "org.slf4j" % "slf4j-api" % "2.0.16",
    "ch.qos.logback" % "logback-classic" % "1.5.15",
    "org.apache.logging.log4j" % "log4j-api" % "2.24.3",
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.24.3"
  )
)

// Core Scala project
lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "xlcr-core",
    libraryDependencies ++= Seq(
      // Scala-specific dependencies
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "com.github.scopt" %% "scopt" % "4.1.0",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,

      // Apache Tika
      "org.apache.tika" % "tika-core" % "3.0.0",
      "org.apache.tika" % "tika-parsers" % "3.0.0",
      "org.apache.tika" % "tika-parsers-standard-package" % "3.0.0",

      // PDF
      "org.apache.pdfbox" % "pdfbox" % "3.0.3"
    )
  )

// Kotlin server project
lazy val server = (project in file("server"))
  .enablePlugins(KotlinPlugin)
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "xlcr-server",
    kotlinVersion := "1.9.10",
    kotlincJvmTarget := "1.8",
    kotlinLib("stdlib"),

    // Kotlin source directory configuration
    Compile / sourceDirectories += baseDirectory.value / "src" / "main" / "kotlin",
    Test / sourceDirectories += baseDirectory.value / "src" / "test" / "kotlin",

    libraryDependencies ++= Seq(
      // Ktor dependencies
      "io.ktor" % "ktor-server-core" % ktorVersion,
      "io.ktor" % "ktor-server-cio" % ktorVersion,
      "io.ktor" % "ktor-server-websockets" % ktorVersion,
      "io.ktor" % "ktor-server-content-negotiation" % ktorVersion,

      // Kotlin MCP
      "io.modelcontextprotocol" % "kotlin-sdk" % "0.2.0"
    )
  )

// Root project for aggregating
lazy val root = (project in file("."))
  .aggregate(core, server)
  .settings(
    name := "xlcr",
    // Don't publish the root project
    publish := {},
    publishLocal := {}
  )
