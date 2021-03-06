package de.gccc.jib

import java.nio.file.Files

import com.google.cloud.tools.jib.image.ImageReference
import sbt._
import sbt.Keys._

object JibPlugin extends AutoPlugin {

  object autoImport {
    val Jib: Configuration = config("jib")

    sealed trait JibImageFormat
    object JibImageFormat {
      case object Docker extends JibImageFormat
      case object OCI    extends JibImageFormat
    }

    val jibBaseImage                   = settingKey[String]("jib base image")
    val jibBaseImageCredentialHelper   = settingKey[Option[String]]("jib base image credential helper")
    val jibJvmFlags                    = settingKey[List[String]]("jib default jvm flags")
    val jibArgs                        = settingKey[List[String]]("jib default args")
    val jibImageFormat                 = settingKey[JibImageFormat]("jib default image format")
    val jibDockerBuild                 = taskKey[Unit]("jib build docker image")
    val jibImageBuild                  = taskKey[Unit]("jib build image (does not need docker)")
    val jibTargetImageCredentialHelper = settingKey[Option[String]]("jib base image credential helper")
    val jibRegistry                    = settingKey[String]("jib target image registry (defaults to docker hub)")
    val jibOrganization                = settingKey[String]("jib docker organization (defaults to organization)")
    val jibName                        = settingKey[String]("jib image name (defaults to project name)")
    val jibVersion                     = settingKey[String]("jib version (defaults to version)")
    val jibEnvironment                 = settingKey[Map[String, String]]("jib docker env variables")
    val jibMappings                    = taskKey[Seq[(File, String)]]("jib additional resource mappings")

    private[jib] object Private {
      val sbtSourceFilesConfiguration = {
        taskKey[SbtSourceFilesConfiguration]("jib source file settings")
      }
      val sbtConfiguration = taskKey[SbtConfiguration]("jib sbt configuration")
    }
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    // public values
    jibBaseImage := "registry.hub.docker.com/schmitch/graalvm:latest",
    jibBaseImageCredentialHelper := None,
    jibTargetImageCredentialHelper := None,
    jibJvmFlags := Nil,
    jibArgs := Nil,
    jibImageFormat := JibImageFormat.Docker,
    jibRegistry := "registry.hub.docker.com",
    jibOrganization := organization.value,
    jibName := name.value,
    jibVersion := version.value,
    jibEnvironment := Map.empty,
    mappings in Jib := Nil,
    jibMappings := (mappings in Jib).value,
    // private values
    Private.sbtSourceFilesConfiguration := {
      val internal           = (internalDependencyClasspath or (internalDependencyClasspath in Runtime)).value
      val external           = (externalDependencyClasspath or (externalDependencyClasspath in Runtime)).value
      val stageDirectory     = target.value / "jib" / "stage"
      val stageDirectoryPath = stageDirectory.toPath
      if (Files.notExists(stageDirectoryPath)) {
        Files.createDirectories(stageDirectoryPath)
      }
      val staged = Stager.stage(Jib.name)(streams.value, stageDirectory, jibMappings.value)

      new SbtSourceFilesConfiguration(
        internal.map(_.data).toList,
        external.map(_.data.toPath).toList,
        staged
      )
    },
    Private.sbtConfiguration := {
      val baseImage = ImageReference.parse(jibBaseImage.value)

      new SbtConfiguration(
        sLog.value,
        Private.sbtSourceFilesConfiguration.value,
        (mainClass in (Compile, packageBin)).value,
        target.value / "jib" / "internal",
        credentials.value,
        baseImage,
        jibRegistry.value,
        jibOrganization.value,
        jibName.value,
        jibVersion.value
      )
    },
    jibDockerBuild := SbtDockerBuild.task(
      Private.sbtConfiguration.value,
      jibBaseImageCredentialHelper.value,
      jibBaseImage.value,
      jibJvmFlags.value,
      jibArgs.value
    ),
    jibImageBuild := SbtImageBuild.task(
      Private.sbtConfiguration.value,
      jibBaseImageCredentialHelper.value,
      jibTargetImageCredentialHelper.value,
      jibJvmFlags.value,
      jibArgs.value,
      jibImageFormat.value,
      jibEnvironment.value
    ),
    jibDockerBuild := jibDockerBuild.dependsOn(compile in Compile).value,
    jibImageBuild := jibImageBuild.dependsOn(compile in Compile).value,
  )

}
