import kotlin.Keys.{kotlinLib, kotlinVersion, kotlincJvmTarget}

// Define Scala versions
val scala212 = "2.12.18"
val scala3 = "3.3.4"

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := scala3
ThisBuild / crossScalaVersions := Seq(scala212, scala3)

// For IDE compatibility

val circeVersion = "0.14.10"
val ktorVersion = "3.0.3"
val zioVersion = "2.1.0"
val zioConfigVersion = "4.0.1"
val zioHttpVersion = "3.0.0-RC4"
val tikaVersion = "2.9.0"

// Common settings and dependencies
lazy val commonSettings = Seq(
  organization := "com.tjclp.xlcr",
  idePackagePrefix := Some("com.tjclp.xlcr"),
  resolvers += "Aspose Java Repository" at "https://releases.aspose.com/java/repo/",
  libraryDependencies ++= Seq(
    // Common logging
    "org.slf4j" % "slf4j-api" % "2.0.16",
    "ch.qos.logback" % "logback-classic" % "1.5.15",
    "org.apache.logging.log4j" % "log4j-api" % "2.24.3",
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.24.3",
    // ZIO core (for all modules)
    "dev.zio" %% "zio" % zioVersion,
    "dev.zio" %% "zio-streams" % zioVersion
  ),
  // Source directory configuration for cross-compilation
  Compile / unmanagedSourceDirectories ++= {
    // sbt typically includes src/main/scala automatically.
    // We explicitly add the version-specific directories here.
    val base = baseDirectory.value / "src" / "main"
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(base / "scala-2") // Add src/main/scala-2
      case Some((3, _)) => Seq(base / "scala-3") // Add src/main/scala-3
      case _            => Seq.empty // No extra directories for other versions
    }
  },
  // Similarly for test sources if you use version-specific tests
  Test / unmanagedSourceDirectories ++= {
    val base = baseDirectory.value / "src" / "test"
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(base / "scala-2") // Add src/test/scala-2
      case Some((3, _)) => Seq(base / "scala-3") // Add src/test/scala-3
      case _            => Seq.empty
    }
  }
)

// Core Scala project
lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "xlcr-core",
    libraryDependencies ++= Seq(
      // Scala-specific dependencies
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalatestplus" %% "scalacheck-1-17" % "3.2.18.0" % Test,
      "org.scalacheck" %% "scalacheck" % "1.18.1" % Test,
      "com.github.scopt" %% "scopt" % "4.1.0",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      // Apache Tika
      "org.apache.tika" % "tika-core" % tikaVersion,
      "org.apache.tika" % "tika-parsers" % tikaVersion,
      "org.apache.tika" % "tika-parsers-standard-package" % tikaVersion,
      // Apache Commons Compress for archive formats
      "org.apache.commons" % "commons-compress" % "1.25.0",
      // Jakarta Mail for email parsing
      "com.sun.mail" % "jakarta.mail" % "2.0.1",
      // JAI
      "com.github.jai-imageio" % "jai-imageio-core" % "1.4.0",
      "com.github.jai-imageio" % "jai-imageio-jpeg2000" % "1.4.0",
      // PDF
      "org.apache.pdfbox" % "pdfbox" % "3.0.4",
      // XML
      "org.apache.xmlgraphics" % "batik-all" % "1.18",
      // ZIO specific dependencies
      "dev.zio" %% "zio-config" % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "io.circe" %% "circe-generic-extras" % "0.14.4",
          "org.scala-lang" % "scala-reflect" % scalaVersion.value
        )
      case _ => Seq() // No special options for Scala 3
    }),
    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "-Ypartial-unification",
          "-language:higherKinds",
          "-language:implicitConversions"
        )
      case _ => Seq() // No special options for Scala 3
    })
  )

// New Aspose integration module
lazy val coreAspose = (project in file("core-aspose"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "xlcr-core-aspose",
    // Added resolver settings specifically for Aspose
    resolvers ++= Seq(
      "Aspose Java Repository" at "https://releases.aspose.com/java/repo/"
    ),
    // Add scala language features
    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) =>
        Seq(
          "-Ypartial-unification",
          "-language:higherKinds",
          "-language:implicitConversions",
          // Add the following to help with name conflicts
          "-Yresolve-term-conflict:package"
        )
      case _ =>
        Seq(
          "-Yresolve-term-conflict:package" // tell Scala to prefer the package
        )
    }),
    libraryDependencies ++= Seq(
      "com.aspose" % "aspose-cells" % "25.4",
      "com.aspose" % "aspose-words" % "25.4" classifier "jdk17",
      "com.aspose" % "aspose-slides" % "25.4" classifier "jdk16",
      "com.aspose" % "aspose-email" % "25.3" classifier "jdk16",
      "com.aspose" % "aspose-zip" % "25.3"
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

// SpreadsheetLLM integration module
lazy val coreSpreadsheetLLM = (project in file("core-spreadsheetllm"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "xlcr-core-spreadsheetllm",
    libraryDependencies ++= Seq(
      // Apache POI for Excel handling
      "org.apache.poi" % "poi" % "5.2.5",
      "org.apache.poi" % "poi-ooxml" % "5.2.5",
      // JSON processing
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      // CLI argument parsing
      "com.github.scopt" %% "scopt" % "4.1.0",
      // ZIO specific dependencies
      "dev.zio" %% "zio-config" % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
      // Testing
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )

// Root project for aggregating
lazy val root = (project in file("."))
  .aggregate(core, coreAspose, coreSpreadsheetLLM, server)
  .settings(
    name := "xlcr",
    // Don't publish the root project
    publish := {},
    publishLocal := {}
  )
