import $ivy.`io.chris-kipp::mill-ci-release::0.1.1`
import $ivy.`io.github.alexarchambault.mill::mill-native-image::0.1.21`
import $ivy.`io.github.alexarchambault.mill::mill-native-image-upload:0.1.21`

import mill._
import mill.scalalib._

import io.github.alexarchambault.millnativeimage.NativeImage
import io.github.alexarchambault.millnativeimage.upload.Upload
import io.kipp.mill.ci.release.CiReleaseModule

object Versions {
  def scala213            = "2.13.8"
  def scala212            = "2.12.16"
  def coursier            = "2.1.0-M6-53-gb4f448130"
  def defaultBloopVersion = "1.5.3-sc-1"
  def jsoniterScala       = "2.15.0"

  def graalvm = "22.2.0"
}

object Deps {
  def bsp4j            = ivy"ch.epfl.scala:bsp4j:2.0.0"
  def caseApp          = ivy"com.github.alexarchambault::case-app:2.1.0-M15"
  def collectionCompat = ivy"org.scala-lang.modules::scala-collection-compat:2.8.1"
  def coursier         = ivy"io.get-coursier::coursier:${Versions.coursier}"
  def coursierJvm      = ivy"io.get-coursier::coursier-jvm:${Versions.coursier}"
  def dependency       = ivy"io.get-coursier::dependency:0.2.2"
  def expecty          = ivy"com.eed3si9n.expecty::expecty:0.15.4"
  def jsoniterCore =
    ivy"com.github.plokhotnyuk.jsoniter-scala:jsoniter-scala-core_2.13:${Versions.jsoniterScala}"
  def jsoniterMacros =
    ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:${Versions.jsoniterScala}"
  def munit        = ivy"org.scalameta::munit:0.7.29"
  def libdaemonjvm = ivy"io.github.alexarchambault.libdaemon::libdaemon:0.0.10"
  def osLib        = ivy"com.lihaoyi::os-lib:0.8.1"
  def pprint       = ivy"com.lihaoyi::pprint:0.7.3"
  def snailgun     = ivy"io.github.alexarchambault.scala-cli.snailgun::snailgun-core:0.4.1-sc2"
  def svm          = ivy"org.graalvm.nativeimage:svm:${Versions.graalvm}"

  def graalVmId = s"graalvm-java17:${Versions.graalvm}"
}

private def ghOrg  = "scala-cli"
private def ghName = "bloop-cli"

trait BloopCliModule extends ScalaModule with CiReleaseModule {
  def javacOptions = super.javacOptions() ++ Seq(
    "--release",
    "16"
  )
  def scalacOptions = T {
    val sv         = scalaVersion()
    val isScala213 = sv.startsWith("2.13.")
    val extraOptions =
      if (isScala213) Seq("-Xsource:3", "-Ytasty-reader")
      else Nil
    super.scalacOptions() ++ Seq("-Ywarn-unused") ++ extraOptions
  }

  import mill.scalalib.publish._
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "io.github.alexarchambault.bloopcli",
    url = s"https://github.com/$ghOrg/$ghName",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github(ghOrg, ghName),
    developers = Seq(
      Developer("alexarchambault", "Alex Archambault", "https://github.com/alexarchambault")
    )
  )
  override def sonatypeUri         = "https://s01.oss.sonatype.org/service/local"
  override def sonatypeSnapshotUri = "https://s01.oss.sonatype.org/content/repositories/snapshots"
}

object `bloop-rifle` extends BloopCliModule {
  def scalaVersion = Versions.scala213
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.bsp4j,
    Deps.collectionCompat,
    Deps.libdaemonjvm,
    Deps.snailgun
  )

  def constantsFile = T.persistent {
    val dir  = T.dest / "constants"
    val dest = dir / "Constants.scala"
    val code =
      s"""package bloop.rifle.internal
         |
         |/** Build-time constants. Generated by mill. */
         |object Constants {
         |  def bloopVersion = "${Versions.defaultBloopVersion}"
         |  def bloopScalaVersion = "${Versions.scala212}"
         |  def bspVersion = "${Deps.bsp4j.dep.version}"
         |}
         |""".stripMargin
    if (!os.isFile(dest) || os.read(dest) != code)
      os.write.over(dest, code, createFolders = true)
    PathRef(dir)
  }
  def generatedSources = super.generatedSources() ++ Seq(constantsFile())

  object test extends Tests {
    def testFramework = "munit.Framework"
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.expecty,
      Deps.munit
    )
  }
}

object cli extends BloopCliModule with NativeImage {
  def scalaVersion = Versions.scala213
  def moduleDeps = Seq(
    `bloop-rifle`
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.caseApp,
    Deps.coursier,
    Deps.coursierJvm,
    Deps.dependency,
    Deps.jsoniterCore,
    Deps.osLib
  )
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.jsoniterMacros,
    Deps.svm
  )
  private def actualMainClass = "bloop.cli.Bloop"
  def mainClass               = Some(actualMainClass)

  def nativeImageMainClass    = actualMainClass
  def nativeImageClassPath    = runClasspath()
  def nativeImageGraalVmJvmId = Deps.graalVmId
  def nativeImageOptions = super.nativeImageOptions() ++ Seq(
    "--no-fallback",
    "--enable-url-protocols=http,https",
    "-Djdk.http.auth.tunneling.disabledSchemes="
  )
  def nativeImagePersist = System.getenv("CI") != null

  def copyToArtifacts(directory: String = "artifacts/") = T.command {
    val _ = Upload.copyLauncher(
      nativeImage().path,
      directory,
      "bloop",
      compress = true
    )
  }
}

def tmpDir = T {
  T.dest.toString
}

object integration extends BloopCliModule {
  def scalaVersion = Versions.scala213
  object test extends Tests {
    def testFramework = "munit.Framework"
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.expecty,
      Deps.munit,
      Deps.osLib,
      Deps.pprint
    )

    def forkEnv = super.forkEnv() ++ Seq(
      "BLOOP_CLI_TESTS_TMP_DIR" -> tmpDir()
    )

    private final class TestHelper(launcherTask: T[PathRef]) {
      def test(args: String*) = {
        val argsTask = T.task {
          val launcher = launcherTask().path
          val extraArgs = Seq(
            s"-Dtest.bloop-cli.path=$launcher"
          )
          args ++ extraArgs
        }
        T.command {
          testTask(argsTask, T.task(Seq.empty[String]))()
        }
      }
    }

    def jvm(args: String*) =
      new TestHelper(cli.launcher).test(args: _*)
    def native(args: String*) =
      new TestHelper(cli.nativeImage).test(args: _*)
    def test(args: String*) =
      jvm(args: _*)
  }
}

object ci extends Module {
  def upload(directory: String = "artifacts/") = T.command {
    val version = cli.publishVersion()

    val path = os.Path(directory, os.pwd)
    val launchers = os.list(path)
      .filter(os.isFile(_))
      .map(path => path -> path.last)
    val ghToken = Option(System.getenv("UPLOAD_GH_TOKEN")).getOrElse {
      sys.error("UPLOAD_GH_TOKEN not set")
    }
    val (tag, overwriteAssets) =
      if (version.endsWith("-SNAPSHOT")) ("nightly", true)
      else ("v" + version, false)

    Upload.upload(
      ghOrg,
      ghName,
      ghToken,
      tag,
      dryRun = false,
      overwrite = overwriteAssets
    )(launchers: _*)
  }

  def copyJvm(jvm: String = Deps.graalVmId, dest: String = "jvm") = T.command {
    import sys.process._
    val command = os.proc(
      "cs",
      "java-home",
      "--jvm",
      jvm,
      "--update",
      "--ttl",
      "0"
    )
    val baseJavaHome = os.Path(command.call().out.text().trim, os.pwd)
    System.err.println(s"Initial Java home $baseJavaHome")
    val destJavaHome = os.Path(dest, os.pwd)
    os.copy(baseJavaHome, destJavaHome, createFolders = true)
    System.err.println(s"New Java home $destJavaHome")
    destJavaHome
  }
}
