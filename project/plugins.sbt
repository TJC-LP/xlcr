// Core plugins
addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings"  % "1.1.2")
addSbtPlugin("com.eed3si9n"        % "sbt-assembly"      % "2.2.0")
addSbtPlugin("nl.gn0s1s"           % "sbt-dotenv"        % "3.0.0")

// GitHub Actions and release automation
addSbtPlugin("com.github.sbt" % "sbt-github-actions" % "0.24.0")
addSbtPlugin("com.github.sbt" % "sbt-ci-release"     % "1.5.12")

// Code quality plugins
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.12.1")

// Documentation
addSbtPlugin("com.github.sbt" % "sbt-unidoc"  % "0.5.0")
addSbtPlugin("com.github.sbt" % "sbt-site"    % "1.6.0")
addSbtPlugin("com.github.sbt" % "sbt-ghpages" % "0.8.0")

// Sonatype publishing
// Automated snapshot and release publishing to Maven Central
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.9.3")
