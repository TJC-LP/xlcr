package com.tjclp.xlcr.server

/**
 * Configuration for the XLCR HTTP server.
 *
 * @param host
 *   The host to bind to (default: 0.0.0.0)
 * @param port
 *   The port to listen on (default: 8080)
 * @param maxRequestSize
 *   Maximum request body size in bytes (default: 100MB)
 * @param loInstances
 *   Number of LibreOffice processes to run (default: 1)
 * @param loRestartAfter
 *   Restart LibreOffice after N conversions (default: 200)
 * @param loTaskTimeout
 *   LibreOffice task execution timeout in ms (default: 120000)
 * @param loQueueTimeout
 *   LibreOffice task queue timeout in ms (default: 30000)
 */
final case class ServerConfig(
  host: String = "0.0.0.0",
  port: Int = 8080,
  maxRequestSize: Long = 100 * 1024 * 1024, // 100MB
  loInstances: Int = 1,
  loRestartAfter: Int = 200,
  loTaskTimeout: Long = 120000L, // 2 minutes
  loQueueTimeout: Long = 30000L  // 30 seconds
)

object ServerConfig:

  /** Default configuration */
  val default: ServerConfig = ServerConfig()

  /**
   * Create configuration from environment variables.
   *
   * Environment variables:
   *   - XLCR_HOST: Server host (default: 0.0.0.0)
   *   - XLCR_PORT: Server port (default: 8080)
   *   - XLCR_MAX_REQUEST_SIZE: Max request size in bytes (default: 104857600)
   *   - XLCR_LO_INSTANCES: Number of LibreOffice processes (default: 1)
   *   - XLCR_LO_RESTART_AFTER: Restart after N conversions (default: 200)
   *   - XLCR_LO_TASK_TIMEOUT: Task execution timeout in ms (default: 120000)
   *   - XLCR_LO_QUEUE_TIMEOUT: Task queue timeout in ms (default: 30000)
   */
  def fromEnv: ServerConfig =
    ServerConfig(
      host = sys.env.getOrElse("XLCR_HOST", "0.0.0.0"),
      port = sys.env.get("XLCR_PORT").flatMap(_.toIntOption).getOrElse(8080),
      maxRequestSize = sys.env
        .get("XLCR_MAX_REQUEST_SIZE")
        .flatMap(_.toLongOption)
        .getOrElse(100 * 1024 * 1024),
      loInstances = sys.env
        .get("XLCR_LO_INSTANCES")
        .flatMap(_.toIntOption)
        .getOrElse(1),
      loRestartAfter = sys.env
        .get("XLCR_LO_RESTART_AFTER")
        .flatMap(_.toIntOption)
        .getOrElse(200),
      loTaskTimeout = sys.env
        .get("XLCR_LO_TASK_TIMEOUT")
        .flatMap(_.toLongOption)
        .getOrElse(120000L),
      loQueueTimeout = sys.env
        .get("XLCR_LO_QUEUE_TIMEOUT")
        .flatMap(_.toLongOption)
        .getOrElse(30000L)
    )

  /**
   * Create configuration from CLI arguments, with env var fallback, then defaults.
   *
   * Priority: CLI flags > environment variables > defaults.
   */
  def fromArgs(
    host: Option[String] = None,
    port: Option[Int] = None,
    maxRequestSize: Option[Long] = None,
    loInstances: Option[Int] = None,
    loRestartAfter: Option[Int] = None,
    loTaskTimeout: Option[Long] = None,
    loQueueTimeout: Option[Long] = None
  ): ServerConfig =
    val envConfig = fromEnv
    ServerConfig(
      host = host.getOrElse(envConfig.host),
      port = port.getOrElse(envConfig.port),
      maxRequestSize = maxRequestSize.getOrElse(envConfig.maxRequestSize),
      loInstances = loInstances.getOrElse(envConfig.loInstances),
      loRestartAfter = loRestartAfter.getOrElse(envConfig.loRestartAfter),
      loTaskTimeout = loTaskTimeout.getOrElse(envConfig.loTaskTimeout),
      loQueueTimeout = loQueueTimeout.getOrElse(envConfig.loQueueTimeout)
    )
  end fromArgs
end ServerConfig
