import kotlin.Keys.{kotlinLib, kotlinVersion, kotlincJvmTarget}

// Define Scala versions
val scala212 = "2.12.18"
val scala3 = "3.3.4"

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := scala212
ThisBuild / crossScalaVersions := Seq(scala212, scala3)

// For IDE compatibility
val circeVersion = "0.14.10"
val ktorVersion = "3.0.3"
val zioVersion = "2.1.0"
val zioConfigVersion = "4.0.1"
val zioHttpVersion = "3.0.0-RC4"
val tikaVersion = "2.9.1"
val sparkVersion = "3.5.2"

// Common settings and dependencies
lazy val commonSettings = Seq(
  organization := "com.tjclp.xlcr",
  idePackagePrefix := Some("com.tjclp.xlcr"),
  resolvers += "Aspose Java Repository" at "https://releases.aspose.com/java/repo/",
  libraryDependencies ++= Seq(
    // Common logging - simplified to just SLF4J + Logback
    "org.slf4j" % "slf4j-api" % "2.0.16",
    "ch.qos.logback" % "logback-classic" % "1.5.15",
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
  .settings(assemblySettings)
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
      "org.apache.commons" % "commons-compress" % "1.23.0",
      // Jakarta Mail for email parsing
      "org.eclipse.angus" % "jakarta.mail" % "2.0.2",
      "jakarta.activation" % "jakarta.activation-api" % "2.1.3",
      // JAI
      "com.github.jai-imageio" % "jai-imageio-core" % "1.4.0",
      "com.github.jai-imageio" % "jai-imageio-jpeg2000" % "1.4.0",
      // PDF
      "org.apache.pdfbox" % "pdfbox" % "2.0.29",
      // XML
      "org.apache.xmlgraphics" % "batik-all" % "1.18",
      // ODFDOM for OpenDocument files
      "org.odftoolkit" % "odfdom-java" % "0.12.0",
      // ZIO specific dependencies
      "dev.zio" %% "zio-config" % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
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
  .settings(assemblySettings)
  .settings(
    name := "xlcr-core-aspose",
    // Added resolver settings specifically for Aspose
    resolvers ++= Seq(
      "Aspose Java Repository" at "https://releases.aspose.com/java/repo/"
    ),
    // WORKAROUND: Disable Scaladoc generation to avoid name conflicts in Aspose libraries
    // There's a name clash in the Aspose slides library where both a package and an object
    // with the same name "ms" exist under "com.aspose.slides", which causes Scaladoc to fail
    // with: "package com.aspose.slides contains object and package with same name: ms"
    Compile / doc / sources := Seq.empty,
    // Don't add the Scaladoc jar to the publishing task
    Compile / packageDoc / publishArtifact := false,
    // Add scala language features
    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "-Ypartial-unification",
          "-language:higherKinds",
          "-language:implicitConversions",
          // Add the following to help with name conflicts
          "-Yresolve-term-conflict:package"
        )
      case _ =>
        Seq(
          "-language:higherKinds",
          "-language:implicitConversions",
          // Add the following to help with name conflicts
          "-Yresolve-term-conflict:package"
        )
    }),
    libraryDependencies ++= Seq(
      "com.aspose" % "aspose-cells" % "25.4",
      "com.aspose" % "aspose-words" % "25.4" classifier "jdk17",
      "com.aspose" % "aspose-slides" % "25.4" classifier "jdk16",
      "com.aspose" % "aspose-email" % "25.3" classifier "jdk16",
      "com.aspose" % "aspose-zip" % "25.3",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )

// ---------------------------------------------------------------------------
// Spark composable pipeline module (Scala 2.12 / 2.13 only - not Scala 3 compatible)
// ---------------------------------------------------------------------------

lazy val coreSpark = (project in file("core-spark"))
  .dependsOn(core, coreAspose, coreSpreadsheetLLM)
  .settings(commonSettings)
  .settings(assemblySettings)
  .settings(
    name := "xlcr-core-spark",
    scalaVersion := "2.12.18",
    // Only support Scala 2.12 and 2.13 for Spark (not Scala 3)
    crossScalaVersions := Seq("2.12.18", "2.13.14"),
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
      "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion % "provided",
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )

// Kotlin server project
lazy val server = (project in file("server"))
  .enablePlugins(KotlinPlugin)
  .dependsOn(core)
  .settings(commonSettings)
  .settings(assemblySettings)
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
  .settings(assemblySettings)
  .settings(
    name := "xlcr-core-spreadsheetllm",
    libraryDependencies ++= Seq(
      // Apache POI for Excel handling
      "org.apache.poi" % "poi" % "5.2.5",
      "org.apache.poi" % "poi-ooxml" % "5.2.5",
      // ODFDOM for OpenDocument (ODS) format
      "org.odftoolkit" % "odfdom-java" % "0.12.0",
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

// Assembly settings
lazy val assemblySettings = Seq(
  // Set the assembly file name to include the module name
  assembly / assemblyJarName := s"${name.value}-assembly-${version.value}.jar",
  // Configure merge strategy
  assembly / assemblyMergeStrategy := {
    // Handle logback.xml - use the first one
    case "logback.xml" => MergeStrategy.first

    // Handle Main class conflicts (for modules that all have Main.class)
    case PathList(ps @ _*) if ps.last == "Main.class"  => MergeStrategy.first
    case PathList(ps @ _*) if ps.last == "Main$.class" => MergeStrategy.first
    case PathList(ps @ _*) if ps.last == "Main.tasty"  => MergeStrategy.first

    // Handle module-info.class conflicts
    case PathList("module-info.class") => MergeStrategy.discard
    case PathList("META-INF", "versions", "9", "module-info.class") =>
      MergeStrategy.discard

    // Handle META-INF conflicts
    case PathList("META-INF", "MANIFEST.MF")       => MergeStrategy.discard
    case PathList("META-INF", "mailcap")           => MergeStrategy.first
    case PathList("META-INF", "mailcap.default")   => MergeStrategy.first
    case PathList("META-INF", "mimetypes.default") => MergeStrategy.first
    case PathList("META-INF", "versions", "9", "module-info.class") =>
      MergeStrategy.discard
    case PathList("META-INF", "kotlin-project-structure-metadata.json") =>
      MergeStrategy.discard
    case PathList("META-INF", xs @ _*) if xs.exists(_.endsWith(".DSA")) =>
      MergeStrategy.discard
    case PathList("META-INF", xs @ _*) if xs.exists(_.endsWith(".SF")) =>
      MergeStrategy.discard
    case PathList("META-INF", xs @ _*) if xs.exists(_.endsWith(".RSA")) =>
      MergeStrategy.discard

    // Handle Log implementation conflicts
    case PathList("org", "apache", "commons", "logging", xs @ _*) =>
      MergeStrategy.first
    case PathList("org", "apache", "commons", "logging", "impl", xs @ _*) =>
      MergeStrategy.first

    // Handle Jakarta/Javax activation and mail conflicts
    case PathList(
          "META-INF",
          "services",
          "javax.activation.DataContentHandler"
        ) =>
      MergeStrategy.filterDistinctLines
    case PathList(
          "META-INF",
          "services",
          "jakarta.activation.DataContentHandler"
        ) =>
      MergeStrategy.filterDistinctLines
    case PathList("javax", "activation", xs @ _*)      => MergeStrategy.first
    case PathList("jakarta", "activation", xs @ _*)    => MergeStrategy.first
    case PathList("jakarta", "mail", xs @ _*)          => MergeStrategy.first
    case PathList("com", "sun", "activation", xs @ _*) => MergeStrategy.first
    case PathList("com", "sun", "mail", xs @ _*)       => MergeStrategy.first

    // Handle overlaps between xml-apis-ext and xml-apis
    case PathList("license", "LICENSE.dom-software.txt") => MergeStrategy.first

    // Handle library.properties conflicts (including java-rdfa and scala-library)
    case PathList("library.properties") => MergeStrategy.first

    // Handle Kotlin-specific files
    case PathList(ps @ _*) if ps.last == "manifest" => MergeStrategy.discard
    case PathList(ps @ _*)
        if ps.last == "module" && ps.exists(_ == "linkdata") =>
      MergeStrategy.discard

    // Handle CommonMain/Native/Posix/etc linkdata
    case PathList(ps @ _*) if ps.contains("Main") && ps.contains("linkdata") =>
      MergeStrategy.discard
    case PathList(ps @ _*) if ps.contains("Main") && ps.contains("default") =>
      MergeStrategy.discard

    // Default strategy
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  }
)

// Custom assembly tasks
lazy val assembleCore = taskKey[File]("Assemble the core module")
lazy val assembleAspose = taskKey[File]("Assemble the aspose module")
lazy val assembleSpreadsheetLLM =
  taskKey[File]("Assemble the spreadsheetLLM module")
lazy val assembleSpark = taskKey[File]("Assemble the spark module")

// Root project for aggregating
lazy val root = (project in file("."))
  .aggregate(core, coreAspose, coreSpreadsheetLLM, coreSpark, server)
  .settings(
    name := "xlcr",
    // Don't publish the root project
    publish := {},
    publishLocal := {},
    // Custom assembly tasks
    assembleCore := (core / assembly).value,
    assembleAspose := (coreAspose / assembly).value,
    assembleSpreadsheetLLM := (coreSpreadsheetLLM / assembly).value,
    assembleSpark := (coreSpark / assembly).value,
    // Define projects that should be included when cross-building for Scala 3
    crossScalaVersions := Seq(scala212, scala3),
    // Custom tasks for different Scala versions
    addCommandAlias(
      "compileScala3",
      ";++3.3.4; core/compile; coreAspose/compile; coreSpreadsheetLLM/compile; server/compile"
    ),
    addCommandAlias("compileScala2", ";++2.12.18; compile"),
    addCommandAlias(
      "testScala3",
      ";++3.3.4; core/test; coreAspose/test; coreSpreadsheetLLM/test; server/test"
    ),
    addCommandAlias("testScala2", ";++2.12.18; test")
  )
  .settings(assemblySettings)
