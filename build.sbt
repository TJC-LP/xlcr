ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.4"

lazy val root = (project in file("."))
  .settings(
    name := "xlcr",
    idePackagePrefix := Some("com.tjclp.xlcr")
  )

val circeVersion = "0.14.10"
libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.apache.tika" % "tika-core" % "3.0.0",
  // This provides a POM that aggregates all parser modules, but you need the actual parser modules you want:
  "org.apache.tika" % "tika-parsers" % "3.0.0",

  // Include the standard parser package if you need general file format support
  "org.apache.tika" % "tika-parsers-standard-package" % "3.0.0",

  // Logging
  "org.slf4j" % "slf4j-api" % "2.0.16",
  "ch.qos.logback" % "logback-classic" % "1.5.14",
  "org.apache.logging.log4j" % "log4j-api" % "2.24.3",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.24.3",

  // Parsing and JSON
  "com.github.scopt" %% "scopt" % "4.1.0",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,

  // Kotlin MCP
  "io.modelcontextprotocol" % "kotlin-sdk" % "0.1.0"
)
