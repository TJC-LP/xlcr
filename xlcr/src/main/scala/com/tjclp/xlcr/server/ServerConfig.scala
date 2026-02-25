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
 */
final case class ServerConfig(
  host: String = "0.0.0.0",
  port: Int = 8080,
  maxRequestSize: Long = 100 * 1024 * 1024 // 100MB
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
   */
  def fromEnv: ServerConfig =
    ServerConfig(
      host = sys.env.getOrElse("XLCR_HOST", "0.0.0.0"),
      port = sys.env.get("XLCR_PORT").flatMap(_.toIntOption).getOrElse(8080),
      maxRequestSize = sys.env
        .get("XLCR_MAX_REQUEST_SIZE")
        .flatMap(_.toLongOption)
        .getOrElse(100 * 1024 * 1024)
    )

  /**
   * Create configuration from CLI arguments, with env var fallback, then defaults.
   *
   * Priority: CLI flags > environment variables > defaults.
   *
   * @param host
   *   CLI --host flag value
   * @param port
   *   CLI --port flag value
   * @param maxRequestSize
   *   CLI --max-request-size flag value
   */
  def fromArgs(
    host: Option[String] = None,
    port: Option[Int] = None,
    maxRequestSize: Option[Long] = None
  ): ServerConfig =
    val envConfig = fromEnv
    ServerConfig(
      host = host.getOrElse(envConfig.host),
      port = port.getOrElse(envConfig.port),
      maxRequestSize = maxRequestSize.getOrElse(envConfig.maxRequestSize)
    )
end ServerConfig
