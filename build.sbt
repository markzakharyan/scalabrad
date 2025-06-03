lazy val commonSettings = Seq(
  organization := "org.labrad",

  version := {
    IO.read(file("core/src/main/resources/org/labrad/version.txt")).trim()
  },

  scalaVersion := "2.11.12",
  javacOptions ++= Seq("-source", "17", "-target", "17"),

  scalacOptions ++= Seq(
    "-deprecation",
    "-feature"
  ),

  licenses += ("GPL-2.0", url("http://www.gnu.org/licenses/gpl-2.0.html")),

  // dependencies
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "com.lihaoyi" %% "fastparse" % "0.3.7",
    "org.clapper" %% "argot" % "1.0.4",
    "io.netty" % "netty-all" % "4.1.122.Final",
    "joda-time" % "joda-time" % "2.13.1",
    "org.joda" % "joda-convert" % "2.2.4",
    "org.slf4j" % "slf4j-api" % "2.0.9",
    "ch.qos.logback" % "logback-classic" % "1.5.18",
    "com.typesafe.play" %% "anorm" % "2.5.3",
    "org.xerial" % "sqlite-jdbc" % "3.49.1.0",
    "org.bouncycastle" % "bcprov-jdk15on" % "1.70",
    "org.bouncycastle" % "bcpkix-jdk15on" % "1.70",
    "org.mindrot" % "jbcrypt" % "0.4",
    "com.google.api-client" % "google-api-client" % "2.8.0",
    "com.google.http-client" % "google-http-client" % "1.47.0",
    "com.google.http-client" % "google-http-client-jackson2" % "1.47.0"
  ),

  // When running, connect std in and tell manager to stop on EOF (ctrl+D).
  // This allows us to stop the manager without using ctrl+C, which kills sbt.
  run / fork := true,
  run / connectInput := true,
  javaOptions += "-Dorg.labrad.stopOnEOF=true",

  // testing
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.19" % "test"
  ),

  Test / fork := true,
  Test / parallelExecution := false,
  Test / javaOptions += "-Xmx1g",
)

lazy val all = project.in(file("."))
  .aggregate(core, manager)

lazy val core = project.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "scalabrad-core"
  )

lazy val manager = project.in(file("manager"))
  .dependsOn(core)
  .enablePlugins(PackPlugin)
  .settings(commonSettings)
  .settings(
    name := "scalabrad-manager",

    packMain := Map(
      "labrad" -> "org.labrad.manager.Manager",
      "labrad-migrate-registry" -> "org.labrad.registry.Migrate",
      "labrad-sql-test" -> "org.labrad.registry.SQLTest"
    ),
    packGenerateWindowsBatFile := true,
    packArchivePrefix := "scalabrad"
  )
