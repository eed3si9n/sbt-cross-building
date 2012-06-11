/* sbt -- Simple Build Tool
 * Copyright 2011, 2012 Mark Harrah, Johannes Rudolph
 *
 * I copied and adapted this from xsbt/main/Defaults.scala
 */

package sbt

import Keys._

/**
 * The idea here is to be able to define a "sbtVersion in sbtPlugin" which
 * directs the dependencies of the plugin to build to the specified sbt plugin
 * version.
 *
 * More work is needed to make that work properly for sbt >= 0.12.
 */
object CrossBuilding {
  val pluginSbtVersion = sbtVersion in sbtPlugin

  val crossSbtVersions = SettingKey[Seq[String]]("cross-sbt-versions", "The versions of Sbt used when cross-building an sbt plugin.")

  def settings = seq(
    crossTarget <<= (target, scalaVersion, pluginSbtVersion, sbtPlugin, crossPaths)(Defaults.makeCrossTarget),
    allDependencies <<= (projectDependencies, libraryDependencies, sbtPlugin, sbtDependency in sbtPlugin) map {
      (projDeps, libDeps, isPlugin, sbtDep) =>
        val base = projDeps ++ libDeps
        if (isPlugin) sbtDep.copy(configurations = Some(Provided.name)) +: base else base
    },
    sbtDependency in sbtPlugin <<= (appConfiguration, pluginSbtVersion)(sbtDependencyForVersion),
    projectID <<= pluginProjectID,
    scalaVersion <<= (pluginSbtVersion)(scalaVersionByVersion),
    crossSbtVersions <<= pluginSbtVersion (Seq(_)),
    unmanagedSourceDirectories in Compile <++=
      (pluginSbtVersion, sourceDirectory in Compile)(extraSourceFolders),

    commands ++= Seq(SbtPluginCross.switchVersion, SbtPluginCross.crossBuild)
  )

  def scriptedSettings = SbtScriptedSupport.scriptedSettings

  def sbtDependencyForVersion(app: xsbti.AppConfiguration, version: String): ModuleID = {
    val id = app.provider.id
    val cross = usesCrossBuilding(version)
    val groupId = groupIdByVersion(version)

    val base = ModuleID(groupId, id.name, currentCompatibleSbtVersion(version), crossVersion = cross)
    IvySbt.substituteCross(base, app.provider.scalaProvider.version).copy(crossVersion = false)
  }

  val Version = """0\.(\d+)(?:\.(\d+))?(?:-(.*))?""".r
  def groupIdByVersion(version: String): String = version match {
    case Version("11", fix, _) if fix.toInt <= 2 =>
      "org.scala-tools.sbt"
    case Version(major, _, _) if major.toInt < 11 =>
      "org.scala-tools.sbt"
    case _ =>
      "org.scala-sbt"
  }
  def scalaVersionByVersion(version: String): String =
    byMajorVersion(version) { major =>
      if (major >= 12) "2.9.2" else "2.9.1"
    }
  def usesCrossBuilding(version: String): Boolean =
    byMajorVersion(version)(_ < 12)

  def byMajorVersion[T](version: String)(f: Int => T): T = version match {
    case Version(m, _, _) => f(m.toInt)
  }
  def currentCompatibleSbtVersion(version: String): String = version match {
    case "0.12" => "0.12.0-RC1"
    case _ => version
  }

  def extraSourceFolders(version: String, sourceFolder: File): Seq[File] = version match {
    case Version(major, minor, _) =>
      Seq(sourceFolder / ("scala-sbt-0."+major), sourceFolder / "scala-sbt-0.%s.%s".format(major, minor))
  }

  def pluginProjectID = (sbtVersion in sbtPlugin, scalaVersion, projectID, sbtPlugin) {
    (sbtV, scalaV, pid, isPlugin) =>
      if (isPlugin) Defaults.sbtPluginExtra(pid, sbtV, scalaV) else pid
  }
}
