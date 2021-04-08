import sbt.Keys.scalacOptions
import sbt.ScriptedPlugin.autoImport.scriptedBufferLog

Global / onChangedBuildSource := ReloadOnSourceChanges

val scala213        = "2.13.5"
val scala213Version = settingKey[String]("Scala 2.13 version")
scala213Version := scala213
val scala212        = "2.12.13"
val scala212Version = settingKey[String]("Scala 2.12 version")
scala212Version := scala212

lazy val commonSettings = Seq(
  organization := "com.scalatsi",
  version := "0.5.1-SNAPSHOT",
  scalaVersion := scala213,
  crossScalaVersions := Seq(scala212, scala213),
  compilerOptions
  //expandMacros, // Uncomment to view code generated by macro
)

// Publish under the old organisation
lazy val codestarSettings = commonSettings ++ (organization := "nl.codestar")

/* Settings to publish to maven central */
lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging),
  credentials ++= sys.env
    .get("MAVEN_CENTRAL_USER")
    .map(user => Seq(Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, sys.env("MAVEN_CENTRAL_PASSWORD"))))
    .getOrElse(Seq()),
  licenses := Seq("MIT" -> url("https://github.com/scala-tsi/scala-tsi/blob/master/LICENSE")),
  homepage := Some(url("https://scalatsi.com")),
  scmInfo := Some(ScmInfo(url("https://github.com/scala-tsi/scala-tsi"), "scm:git@github.com:scala-tsi/scala-tsi.git")),
  developers := List(
    Developer(
      id = "dhoepelman",
      name = "David Hoepelman",
      email = "992153+dhoepelman@users.noreply.github.com",
      url = url("https://github.com/dhoepelman")
    ),
    Developer(id = "donovan", name = "Donovan de Kuiper", email = "donovan.de.kuiper@ordina.nl", url = url("https://github.com/Hayena"))
  )
) ++
  // CI-only settings, enabled if $CI env variable is set to "true"
  sys.env
    .get("CI")
    .collect({ case "true" =>
      Seq(
        usePgpKeyHex("6044257F427C2854A6F9A0C211A02377A6DD0E59"),
        pgpSecretRing := file(".circleci/circleci.key.asc"),
        pgpPublicRing := file(".circleci/circleci.pub.asc"),
        pgpPassphrase := sys.env.get("GPG_passphrase").map(_.toCharArray)
      )
    })
    .getOrElse(Seq())

lazy val compilerOptions = scalacOptions := Seq(
  "-Xsource:2.13",
  "-unchecked",
  "-feature",
  "-deprecation",
  "-Xlint",
  "-encoding",
  "UTF8",
  "-target:jvm-1.8",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Ywarn-dead-code",
  "-Ywarn-unused:implicits",
  "-Ywarn-unused:imports",
  "-Ywarn-unused:locals",
  "-Ywarn-unused:params",
  "-Ywarn-unused:patvars",
  "-Ywarn-unused:privates",
  "-language:experimental.macros"
) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 13)) =>
    Seq(
      "-Xfatal-warnings" // fatal warnings is possible now warnings can be supressed with 2.13.2's @nowarn
    )
  case Some((2, 12)) =>
    Seq(
      "-Yno-adapted-args",
      "-Xfuture",
      "-language:higherKinds"
    )
  case _ => throw new IllegalArgumentException(s"Unconfigured scala version ${scalaVersion.value}")
})

lazy val expandMacros = scalacOptions += "-Ymacro-debug-lite"

/** ***************
  * scala-tsi and macros
  * **************
  */

lazy val `scala-tsi-macros` = (project in file("macros"))
  .settings(
    commonSettings,
    name := "scala-tsi-macros",
    description := "Macros for scala-tsi",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    // Disable publishing
    publish := {},
    publishLocal := {},
    skip in publish := true
  )

lazy val `scala-tsi` = (project in file("."))
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(scalatsiSettings)
  // Depend and include the macro project, instead of having to publish a separate macro project
  .dependsOn(`scala-tsi-macros` % "compile-internal, test-internal")
  .settings(macroDependencies)

lazy val scalaTsiPublishLocal = (`scala-tsi` / publishLocal).scopedKey
val pub                       = taskKey[Unit]("publish locally for testing")
pub := (`sbt-scala-tsi` / publishLocal).value
scripted := (`sbt-scala-tsi` / scripted).evaluated

lazy val scalatsiSettings = Seq(
  name := "scala-tsi",
  description := "Generate Typescript interfaces from your scala classes",
  libraryDependencies ++= Seq(
    // To support @nowarn in 2.12
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.2",
    // testing framework
    "org.scalatest" %% "scalatest" % "3.2.7" % "test"
  )
)

lazy val macroDependencies = Seq(
  // Add dependencies from the macro project
  libraryDependencies ++= (`scala-tsi-macros` / libraryDependencies).value,
  // include the macro classes and resources in the main jar
  mappings in (Compile, packageBin) ++= mappings
    .in(`scala-tsi-macros`, Compile, packageBin)
    .value,
  // include the macro sources in the main source jar
  mappings in (Compile, packageSrc) ++= mappings
    .in(`scala-tsi-macros`, Compile, packageSrc)
    .value
)

// For publishing under old group id
lazy val `scala-tsi-codestar` = (project in file("codestar/scala-tsi"))
  .settings(codestarSettings)
  .settings(publishSettings)
  .settings(scalatsiSettings)
  // Depend and include the macro project, instead of having to publish a separate macro project
  .dependsOn(`scala-tsi-macros` % "compile-internal, test-internal")
  .settings(macroDependencies)
  .settings(
    sourceDirectory := (sourceDirectory in (`scala-tsi`, Compile)).value,
    resourceDirectory := (resourceDirectory in (`scala-tsi`, Compile)).value
  )

/** ***************
  * sbt-scala-tsi
  * **************
  */

lazy val `sbt-scala-tsi` = (project in file("plugin"))
  .enablePlugins(SbtTwirl, BuildInfoPlugin, SbtPlugin)
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(pluginSettings)
  .settings(
    publishLocal := publishLocal.dependsOn(scalaTsiPublishLocal).value
  )
  .settings(
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false,
    // Make sure to publish the library locally first
    scripted := scripted.dependsOn(publishLocal).evaluated
  )

lazy val pluginSettings = Seq(
  name := "sbt-scala-tsi",
  description := "SBT plugin to generate Typescript interfaces from your scala classes as part of your build",
  sbtPlugin := true,
  buildInfoKeys := Seq[BuildInfoKey](version),
  buildInfoPackage := "sbt.info",
  // sbt 1 uses scala 2.12
  scalaVersion := scala212,
  crossScalaVersions := Seq(scala212)
)

lazy val `sbt-scala-tsi-codestar` = (project in file("codestar/sbt-scala-tsi"))
  .enablePlugins(SbtTwirl, BuildInfoPlugin, SbtPlugin)
  .settings(codestarSettings)
  .settings(publishSettings)
  .settings(pluginSettings)
  .settings(
    sourceDirectory := (sourceDirectory in (`scala-tsi-codestar`, Compile)).value,
    resourceDirectory := (resourceDirectory in (`scala-tsi-codestar`, Compile)).value
  )
