package bloop.cli.integration

object Launcher {

  lazy val launcher = sys.props.getOrElse(
    "test.bloop-cli.path",
    sys.error("BLOOP_CLI_LAUNCHER not set")
  )

}
