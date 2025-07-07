import xerial.sbt.Sonatype.sonatypeCentralHost
import xerial.sbt.Sonatype.autoImport.*
import sbt.librarymanagement.CrossVersion

// Define Scala versions
val scala212 = "2.12.18"
val scala213 = "2.13.14"
val scala3   = "3.3.4"

// CI/CD and publishing configuration
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / sonatypeTimeoutMillis  := 60000
// Dynamic versioning for CI releases with sbt-ci-release
ThisBuild / dynverSonatypeSnapshots := true
ThisBuild / scalaVersion            := scala212
ThisBuild / crossScalaVersions      := Seq(scala212, scala213, scala3)
ThisBuild / versionScheme           := Some("semver-spec")
ThisBuild / version                 := "0.1.0-RC10"

// Scalafix configuration
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// Organization info for publishing
ThisBuild / organization         := "com.tjclp"
ThisBuild / organizationName     := "TJC LP"
ThisBuild / organizationHomepage := Some(url("https://tjclp.com"))

// License and developer information
ThisBuild / licenses := List(
  "MIT" -> url("https://opensource.org/licenses/MIT")
)
ThisBuild / homepage := Some(url("https://github.com/TJC-LP/xlcr"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/TJC-LP/xlcr"),
    "scm:git@github.com:TJC-LP/xlcr.git"
  )
)
ThisBuild / developers := List(
  Developer(
    "arcaputo3",
    "Richie Caputo",
    "rcaputo3@tjclp.com",
    url("https://tjclp.com")
  )
)

// For IDE compatibility
val circeVersion     = "0.14.10"
val ktorVersion      = "3.0.3"
val zioVersion       = "2.1.0"
val zioConfigVersion = "4.0.1"
val zioHttpVersion   = "3.0.0-RC4"
val tikaVersion      = "2.9.1"
val sparkVersion     = "3.5.2"
val compatVersion    = "2.13.0" // keep in one place
val xmlVersion       = "2.3.0"

// Common settings and dependencies
lazy val commonSettings = Seq(
  organization     := "com.tjclp.xlcr",
  idePackagePrefix := Some("com.tjclp.xlcr"),
  resolvers += "Aspose Java Repository".at("https://releases.aspose.com/java/repo/"),
  // Add appropriate compiler flags for unused imports based on Scala version
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => Seq("-Ywarn-unused-import")
    case Some((2, 13)) => Seq("-Wunused:imports")
    case Some((3, _))  => Seq("-Wunused:imports")
    case _             => Seq()
  }),
  libraryDependencies ++= Seq(
    // Common logging - simplified to just SLF4J + Logback
    "org.slf4j"      % "slf4j-api"       % "2.0.16",
    "ch.qos.logback" % "logback-classic" % "1.5.15",
    // ZIO core (for all modules)
    "dev.zio"                %% "zio"                     % zioVersion,
    "dev.zio"                %% "zio-streams"             % zioVersion,
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.13.0"
  ),

  // Source directory configuration for cross-compilation
  Compile / unmanagedSourceDirectories ++= {
    // sbt typically includes src/main/scala automatically.
    // We explicitly add the version-specific directories here.
    val base = baseDirectory.value / "src" / "main"
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(base / "scala-2") // Add src/main/scala-2
      case Some((3, _)) => Seq(base / "scala-3") // Add src/main/scala-3
      case _            => Seq.empty             // No extra directories for other versions
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
    // All Scala versions for core
    crossScalaVersions := Seq(scala212, scala213, scala3),
    name               := "xlcr-core",
    libraryDependencies ++= Seq(
      // Scala-specific dependencies
      "org.scalatest"     %% "scalatest"       % "3.2.19"   % Test,
      "org.scalatestplus" %% "scalacheck-1-17" % "3.2.18.0" % Test,
      "org.scalacheck"    %% "scalacheck"      % "1.18.1"   % Test,
      "com.github.scopt"  %% "scopt"           % "4.1.0",
      "io.circe"          %% "circe-core"      % circeVersion,
      "io.circe"          %% "circe-generic"   % circeVersion,
      "io.circe"          %% "circe-parser"    % circeVersion,
      // Apache Tika
      "org.apache.tika" % "tika-core"                     % tikaVersion,
      "org.apache.tika" % "tika-parsers"                  % tikaVersion,
      "org.apache.tika" % "tika-parsers-standard-package" % tikaVersion,
      // Apache Commons Compress for archive formats
      "org.apache.commons" % "commons-compress" % "1.23.0",
      // Jakarta Mail for email parsing
      "org.eclipse.angus"  % "jakarta.mail"           % "2.0.2",
      "jakarta.activation" % "jakarta.activation-api" % "2.1.3",
      // JAI
      "com.github.jai-imageio" % "jai-imageio-core"     % "1.4.0",
      "com.github.jai-imageio" % "jai-imageio-jpeg2000" % "1.4.0",
      // PDF
      "org.apache.pdfbox" % "pdfbox" % "2.0.29",
      // XML
      "org.apache.xmlgraphics" % "batik-all" % "1.18",
      // ODFDOM for OpenDocument files
      "org.odftoolkit" % "odfdom-java" % "0.12.0",
      // Apache POI for Excel handling
      "org.apache.poi" % "poi"       % "5.2.5",
      "org.apache.poi" % "poi-ooxml" % "5.2.5",
      // ZIO specific dependencies
      "dev.zio"       %% "zio-config"          % zioConfigVersion,
      "dev.zio"       %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio"       %% "zio-config-typesafe" % zioConfigVersion,
      "dev.zio"       %% "zio-test"            % zioVersion % Test,
      "dev.zio"       %% "zio-test-sbt"        % zioVersion % Test,
      "org.scalatest" %% "scalatest"           % "3.2.19"   % Test
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "io.circe"      %% "circe-generic-extras" % "0.14.4",
          "org.scala-lang" % "scala-reflect"        % scalaVersion.value
        )
      case _ => Seq() // No special options for Scala 3
    }),
    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) =>
        Seq(
          "-Ypartial-unification",
          "-language:higherKinds",
          "-language:implicitConversions"
        )
      case Some((2, 13)) =>
        Seq(
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
    // All Scala versions for core-aspose
    crossScalaVersions := Seq(scala212, scala213, scala3),
    name               := "xlcr-core-aspose",
    // Added resolver settings specifically for Aspose
    resolvers ++= Seq(
      "Aspose Java Repository".at("https://releases.aspose.com/java/repo/")
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
      case Some((2, 13)) =>
        Seq(
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
    // --- ① Dottydoc offload: give it no sources ----------------------
    Compile / doc / sources := {
      if (scalaVersion.value.startsWith("3.")) Seq.empty // ⇒ empty jar
      else (Compile / doc / sources).value
    },

    // --- ② Make sure the (empty) jar is still attached & published ---
    Compile / packageDoc / publishArtifact := true,
    Compile / doc / scalacOptions ++= Seq(
      "-Yresolve-term-conflict:package" // <- crucial for Aspose
    ),
    libraryDependencies ++= Seq(
      "com.aspose"     % "aspose-cells"  % "25.4",
      ("com.aspose"    % "aspose-words"  % "25.4").classifier("jdk17"),
      ("com.aspose"    % "aspose-slides" % "25.4").classifier("jdk16"),
      ("com.aspose"    % "aspose-email"  % "25.3").classifier("jdk16"),
      "com.aspose"     % "aspose-zip"    % "25.3",
      ("com.aspose"    % "aspose-pdf"    % "25.4").classifier("jdk17"),
      "org.scalatest" %% "scalatest"     % "3.2.19" % Test
    )
  )

lazy val coreSpark = (project in file("core-spark"))
  .dependsOn(core, coreAspose, coreSpreadsheetLLM)
  .settings(commonSettings)
  .settings(assemblySettings)
  .settings(
    name               := "xlcr-core-spark",
    scalaVersion       := "2.12.18",
    crossScalaVersions := Seq(scala212, scala213, scala3),
    // Fix for Spark tests with Java 11+
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    Test / fork := true,
    Test / javaOptions ++= Seq(
      "--add-opens", "java.base/java.lang=ALL-UNNAMED",
      "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
      "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
      "--add-opens", "java.base/java.io=ALL-UNNAMED",
      "--add-opens", "java.base/java.net=ALL-UNNAMED",
      "--add-opens", "java.base/java.nio=ALL-UNNAMED",
      "--add-opens", "java.base/java.util=ALL-UNNAMED",
      "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED",
      "--add-opens", "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
      "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
      "--add-opens", "java.base/sun.nio.cs=ALL-UNNAMED",
      "--add-opens", "java.base/sun.security.action=ALL-UNNAMED",
      "--add-opens", "java.base/sun.util.calendar=ALL-UNNAMED"
    ),
    // ---------------------------------------------------------------------
    // Spark does not ship native Scala-3 artifacts (yet).  Because Scala 3
    // remains binary-compatible with Scala 2.13, we can safely depend on the
    // 2.13 JARs when cross-building with Scala 3.  sbt exposes a helper
    // (`CrossVersion.for3Use2_13`) that performs this substitution for us.
    // ---------------------------------------------------------------------
    libraryDependencies ++= Seq(
      ("org.apache.spark" %% "spark-sql" % sparkVersion % "provided")
        .cross(CrossVersion.for3Use2_13),
      ("org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion % "provided")
        .cross(CrossVersion.for3Use2_13),
      // "org.scala-lang.modules" % "scala-xml"     % "2.3.0",
      // Testing
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),
    // eliminate any _3 variants that slipped in
    excludeDependencies ++= Seq(
      ExclusionRule("org.scala-lang.modules", "scala-collection-compat_3"),
      ExclusionRule("org.scala-lang.modules", "scala-xml_3")
    ),

    // then add exactly the artefacts we want
    libraryDependencies ++= Seq(
      ("org.scala-lang.modules" % "scala-collection-compat" % compatVersion)
        .cross(CrossVersion.for3Use2_13),
      ("org.scala-lang.modules" % "scala-xml" % xmlVersion)
        .cross(CrossVersion.for3Use2_13)
    )
  )

// SpreadsheetLLM integration module
lazy val coreSpreadsheetLLM = (project in file("core-spreadsheetllm"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(assemblySettings)
  .settings(
    name := "xlcr-core-spreadsheetllm",
    // All Scala versions for core-spreadsheetllm
    crossScalaVersions := Seq(scala212, scala213, scala3),
    libraryDependencies ++= Seq(
      // ODFDOM for OpenDocument (ODS) format
      "org.odftoolkit" % "odfdom-java" % "0.12.0",
      // JSON processing
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion,
      // CLI argument parsing
      "com.github.scopt" %% "scopt" % "4.1.0",
      // ZIO specific dependencies
      "dev.zio" %% "zio-config"          % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
      "dev.zio" %% "zio-test"            % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt"        % zioVersion % Test,
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
lazy val assembleCore   = taskKey[File]("Assemble the core module")
lazy val assembleAspose = taskKey[File]("Assemble the aspose module")
lazy val assembleSpreadsheetLLM =
  taskKey[File]("Assemble the spreadsheetLLM module")
lazy val assembleSpark = taskKey[File]("Assemble the spark module")

// -----------------------------------------------------------------------------
// GitHub Actions CI/CD configuration
// -----------------------------------------------------------------------------

// Specify Java versions for the build matrix
ThisBuild / githubWorkflowJavaVersions := Seq(
  JavaSpec.temurin("17"),
  JavaSpec.temurin("21")
)

// Use all supported Scala versions in the matrix
ThisBuild / githubWorkflowScalaVersions := Seq(scala212, scala213, scala3)

// Only run CI on main branch and version tags (vX.Y.Z)
ThisBuild / githubWorkflowTargetBranches := Seq("main", "feat/*")
ThisBuild / githubWorkflowTargetTags     := Seq("v*")

// Add environment variables for the workflow
ThisBuild / githubWorkflowEnv := Map(
  "ASPOSE_TOTAL_LICENSE_B64" -> "${{ secrets.ASPOSE_TOTAL_LICENSE_B64 }}"
)

// Add quality gates to the workflow
ThisBuild / githubWorkflowBuildPreamble ++= Seq(
  WorkflowStep.Sbt(List("scalafmtCheckAll"), name = Some("Check Formatting"))
)

ThisBuild / evictionErrorLevel := Level.Warn
// Force dependency resolution by adding the specific deps
updateOptions := updateOptions.value.withLatestSnapshots(false)

// Publishing configuration for release builds
ThisBuild / githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v")))
ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    commands = List("ci-release"),
    name = Some("Publish project"),
    env = Map(
      "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )
)

// Root project for aggregating
lazy val root = (project in file("."))
  .aggregate(core, coreAspose, coreSpreadsheetLLM, coreSpark)
  .settings(
    name := "xlcr",
    // Don't publish the root project
    publish      := {},
    publishLocal := {},
    // Custom assembly tasks
    assembleCore           := (core / assembly).value,
    assembleAspose         := (coreAspose / assembly).value,
    assembleSpreadsheetLLM := (coreSpreadsheetLLM / assembly).value,
    assembleSpark          := (coreSpark / assembly).value,
    // Define projects that should be included when cross-building
    crossScalaVersions := Seq(
      scala212,
      scala213
    ), // Root project only uses 2.12 and 2.13 for all modules
    // Custom tasks for different Scala versions
    addCommandAlias(
      "compileScala3",
      ";++3.3.4; compile"
    ),
    addCommandAlias("compileScala2", ";++2.12.18; compile"),
    addCommandAlias("compileScala213", ";++2.13.14; compile"),
    addCommandAlias(
      "testScala3",
      ";++3.3.4; test"
    ),
    addCommandAlias("testScala2", ";++2.12.18; test"),
    addCommandAlias("testScala213", ";++2.13.14; test"),
    // Scalafix for different Scala versions
    addCommandAlias(
      "scalafixAll212",
      ";++2.12.18; scalafixAll"
    ),
    addCommandAlias(
      "scalafixAll213",
      ";++2.13.14; scalafixAll"
    ),
    addCommandAlias(
      "scalafixAll3",
      ";++3.3.4; scalafixAll"
    ),
    addCommandAlias(
      "validateCode",
      ";scalafmtCheckAll;scalafixAll;test"
    )
  )
  .settings(assemblySettings)
