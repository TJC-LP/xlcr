# Configuration Management

**Priority**: High (Critical Pain Point)
**Effort**: 2-3 days
**Phase**: Foundation & Quick Wins

## Current State

### The Problem

Configuration values are **scattered and hardcoded** throughout the codebase:

1. **LibreOffice Timeouts** (`core-libreoffice/src/main/scala/config/LibreOfficeConfig.scala` lines 37-39):
   ```scala
   private val MaxTasksPerProcess = 200
   private val TaskExecutionTimeout = 120000L // 2 minutes
   private val TaskQueueTimeout = 30000L      // 30 seconds
   ```

2. **Thread Pool Sizes**: Various implicit ExecutionContext usages without configuration

3. **Buffer Sizes**: Hardcoded in streaming operations

4. **License Paths**: Aspose license loading from fixed locations

5. **Temp Directory**: No unified temp file management

### Impact on Production Service

For production deployment, this causes:
- ❌ Can't tune performance without code changes
- ❌ Different configs for dev/staging/prod require rebuilds
- ❌ No visibility into what configuration is active
- ❌ Difficult to adjust timeouts for different workloads

### Current Workarounds

- **Environment variables**: Some places check env vars (e.g., `LIBREOFFICE_HOME`)
- **System properties**: Not consistently used
- **Config files**: None present

## Proposed Solution

**Centralized configuration with environment variable overrides**:
1. Single `ApplicationConfig` object as source of truth
2. Environment variable overrides for all settings
3. Validation on startup
4. Configuration visibility (logging, CLI command)

### Design Principles

1. **Sensible Defaults**: Works out-of-box for development
2. **Environment Overrides**: All values configurable via env vars
3. **Fail Fast**: Validate configuration on startup
4. **Type Safety**: Leverage Scala's type system
5. **Documentation**: Every setting documented

## Implementation Details

### Step 1: Create Configuration Schema

**New File**: `core/src/main/scala/config/ApplicationConfig.scala`

```scala
package com.tjclp.xlcr.config

import scala.concurrent.duration._
import scala.util.Try

/**
 * Application-wide configuration.
 * All values can be overridden via environment variables.
 */
case class ApplicationConfig(
  processing: ProcessingConfig,
  libreOffice: LibreOfficeConfig,
  aspose: AsposeConfig,
  performance: PerformanceConfig,
  paths: PathsConfig
)

case class ProcessingConfig(
  defaultTimeout: Duration,
  maxFileSize: Long,
  enableParallel: Boolean
)

case class LibreOfficeConfig(
  enabled: Boolean,
  home: Option[String],
  maxTasksPerProcess: Int,
  taskExecutionTimeout: Duration,
  taskQueueTimeout: Duration
)

case class AsposeConfig(
  enabled: Boolean,
  licensePath: Option[String],
  strictLicenseCheck: Boolean
)

case class PerformanceConfig(
  threadPoolSize: Int,
  bufferSize: Int,
  maxConcurrentConversions: Int
)

case class PathsConfig(
  tempDir: String,
  cacheDir: Option[String]
)

object ApplicationConfig {

  /**
   * Load configuration from environment variables with defaults.
   */
  def load(): ApplicationConfig = {
    ApplicationConfig(
      processing = ProcessingConfig(
        defaultTimeout = getDuration("XLCR_PROCESSING_TIMEOUT", 5.minutes),
        maxFileSize = getLong("XLCR_MAX_FILE_SIZE", 100L * 1024 * 1024), // 100MB
        enableParallel = getBoolean("XLCR_ENABLE_PARALLEL", default = true)
      ),

      libreOffice = LibreOfficeConfig(
        enabled = getBoolean("XLCR_LIBREOFFICE_ENABLED", default = true),
        home = getOptionalString("LIBREOFFICE_HOME"),
        maxTasksPerProcess = getInt("XLCR_LIBREOFFICE_MAX_TASKS", 200),
        taskExecutionTimeout = getDuration("XLCR_LIBREOFFICE_TASK_TIMEOUT", 2.minutes),
        taskQueueTimeout = getDuration("XLCR_LIBREOFFICE_QUEUE_TIMEOUT", 30.seconds)
      ),

      aspose = AsposeConfig(
        enabled = getBoolean("XLCR_ASPOSE_ENABLED", default = true),
        licensePath = getOptionalString("ASPOSE_LICENSE_PATH"),
        strictLicenseCheck = getBoolean("XLCR_ASPOSE_STRICT_LICENSE", default = false)
      ),

      performance = PerformanceConfig(
        threadPoolSize = getInt("XLCR_THREAD_POOL_SIZE", Runtime.getRuntime.availableProcessors()),
        bufferSize = getInt("XLCR_BUFFER_SIZE", 8192),
        maxConcurrentConversions = getInt("XLCR_MAX_CONCURRENT", Runtime.getRuntime.availableProcessors() * 2)
      ),

      paths = PathsConfig(
        tempDir = getString("XLCR_TEMP_DIR", System.getProperty("java.io.tmpdir")),
        cacheDir = getOptionalString("XLCR_CACHE_DIR")
      )
    )
  }

  /**
   * Validate configuration on load.
   * Throws IllegalArgumentException if invalid.
   */
  def validate(config: ApplicationConfig): Unit = {
    require(config.processing.defaultTimeout > Duration.Zero, "Timeout must be positive")
    require(config.processing.maxFileSize > 0, "Max file size must be positive")
    require(config.performance.threadPoolSize > 0, "Thread pool size must be positive")
    require(config.performance.bufferSize > 0, "Buffer size must be positive")

    // Validate paths exist or can be created
    val tempDir = new java.io.File(config.paths.tempDir)
    require(tempDir.exists() || tempDir.mkdirs(), s"Cannot create temp directory: ${config.paths.tempDir}")

    config.paths.cacheDir.foreach { cacheDirPath =>
      val cacheDir = new java.io.File(cacheDirPath)
      require(cacheDir.exists() || cacheDir.mkdirs(), s"Cannot create cache directory: $cacheDirPath")
    }

    // Validate LibreOffice if enabled
    if (config.libreOffice.enabled && config.libreOffice.home.isDefined) {
      val libreOfficeHome = new java.io.File(config.libreOffice.home.get)
      require(libreOfficeHome.exists(), s"LibreOffice home not found: ${config.libreOffice.home.get}")
    }

    // Validate Aspose license if specified
    if (config.aspose.enabled && config.aspose.licensePath.isDefined) {
      val licensePath = new java.io.File(config.aspose.licensePath.get)
      if (config.aspose.strictLicenseCheck) {
        require(licensePath.exists(), s"Aspose license not found: ${config.aspose.licensePath.get}")
      }
    }
  }

  // Helper methods for reading environment variables

  private def getString(key: String, default: String): String =
    sys.env.getOrElse(key, default)

  private def getOptionalString(key: String): Option[String] =
    sys.env.get(key).filter(_.nonEmpty)

  private def getInt(key: String, default: Int): Int =
    sys.env.get(key).flatMap(s => Try(s.toInt).toOption).getOrElse(default)

  private def getLong(key: String, default: Long): Long =
    sys.env.get(key).flatMap(s => Try(s.toLong).toOption).getOrElse(default)

  private def getBoolean(key: String, default: Boolean): Boolean =
    sys.env.get(key).map(_.toLowerCase) match {
      case Some("true" | "yes" | "1") => true
      case Some("false" | "no" | "0") => false
      case _ => default
    }

  private def getDuration(key: String, default: Duration): Duration =
    sys.env.get(key).flatMap { s =>
      Try {
        // Support formats like "30s", "5m", "1h"
        if (s.endsWith("ms")) Duration(s.dropRight(2).toInt, MILLISECONDS)
        else if (s.endsWith("s")) Duration(s.dropRight(1).toInt, SECONDS)
        else if (s.endsWith("m")) Duration(s.dropRight(1).toInt, MINUTES)
        else if (s.endsWith("h")) Duration(s.dropRight(1).toInt, HOURS)
        else Duration(s.toInt, SECONDS) // Default to seconds
      }.toOption
    }.getOrElse(default)
}
```

### Step 2: Initialize Configuration on Startup

**File**: `core/src/main/scala/Main.scala`

```scala
object Main extends App {
  // Load and validate configuration on startup
  private val config: ApplicationConfig = {
    val cfg = ApplicationConfig.load()
    ApplicationConfig.validate(cfg)
    cfg
  }

  // Log configuration on startup (non-sensitive values)
  private val logger = LoggerFactory.getLogger(getClass)
  logger.info(s"XLCR Configuration:")
  logger.info(s"  Processing timeout: ${config.processing.defaultTimeout}")
  logger.info(s"  Max file size: ${config.processing.maxFileSize / (1024 * 1024)}MB")
  logger.info(s"  Thread pool size: ${config.performance.threadPoolSize}")
  logger.info(s"  LibreOffice enabled: ${config.libreOffice.enabled}")
  logger.info(s"  Aspose enabled: ${config.aspose.enabled}")
  logger.info(s"  Temp directory: ${config.paths.tempDir}")

  // Make config available throughout application
  ApplicationContext.setConfig(config)

  // ... rest of Main implementation
}
```

### Step 3: Application Context for Config Access

**New File**: `core/src/main/scala/config/ApplicationContext.scala`

```scala
package com.tjclp.xlcr.config

import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe application context for sharing configuration.
 * Initialized once on startup.
 */
object ApplicationContext {

  private val configRef = new AtomicReference[Option[ApplicationConfig]](None)

  def setConfig(config: ApplicationConfig): Unit = {
    if (!configRef.compareAndSet(None, Some(config))) {
      throw new IllegalStateException("ApplicationConfig already set")
    }
  }

  def config: ApplicationConfig = {
    configRef.get() match {
      case Some(cfg) => cfg
      case None =>
        // Fallback for tests or standalone usage
        val cfg = ApplicationConfig.load()
        ApplicationConfig.validate(cfg)
        cfg
    }
  }

  // Convenience accessors
  def processing: ProcessingConfig = config.processing
  def libreOffice: LibreOfficeConfig = config.libreOffice
  def aspose: AsposeConfig = config.aspose
  def performance: PerformanceConfig = config.performance
  def paths: PathsConfig = config.paths
}
```

### Step 4: Update LibreOffice Config

**File**: `core-libreoffice/src/main/scala/config/LibreOfficeConfig.scala`

Replace hardcoded values (lines 37-39):

```scala
import com.tjclp.xlcr.config.ApplicationContext

// OLD (lines 37-39):
// private val MaxTasksPerProcess = 200
// private val TaskExecutionTimeout = 120000L
// private val TaskQueueTimeout = 30000L

// NEW:
private def maxTasksPerProcess: Int = ApplicationContext.libreOffice.maxTasksPerProcess
private def taskExecutionTimeout: Long = ApplicationContext.libreOffice.taskExecutionTimeout.toMillis
private def taskQueueTimeout: Long = ApplicationContext.libreOffice.taskQueueTimeout.toMillis

// Update usage throughout the file
private lazy val managerConfig: OfficeManager = {
  if (!ApplicationContext.libreOffice.enabled) {
    throw new IllegalStateException("LibreOffice backend is disabled")
  }

  val builder = LocalOfficeManager.builder()

  ApplicationContext.libreOffice.home.foreach { home =>
    builder.officeHome(new File(home))
  }

  builder
    .maxTasksPerProcess(maxTasksPerProcess)
    .taskExecutionTimeout(taskExecutionTimeout)
    .taskQueueTimeout(taskQueueTimeout)
    .build()
}
```

### Step 5: Configuration CLI Command

**Add to**: `core/src/main/scala/cli/CommonCLI.scala`

```scala
def configCommand: Opts[Unit] = Opts.subcommand("config", "Show current configuration") {
  Opts(showConfiguration())
}

private def showConfiguration(): Unit = {
  val config = ApplicationContext.config

  println("XLCR Configuration")
  println("=" * 60)

  println("\nProcessing:")
  println(s"  Default timeout: ${config.processing.defaultTimeout}")
  println(s"  Max file size: ${config.processing.maxFileSize / (1024 * 1024)}MB")
  println(s"  Parallel processing: ${config.processing.enableParallel}")

  println("\nLibreOffice:")
  println(s"  Enabled: ${config.libreOffice.enabled}")
  config.libreOffice.home.foreach(h => println(s"  Home: $h"))
  println(s"  Max tasks per process: ${config.libreOffice.maxTasksPerProcess}")
  println(s"  Task timeout: ${config.libreOffice.taskExecutionTimeout}")
  println(s"  Queue timeout: ${config.libreOffice.taskQueueTimeout}")

  println("\nAspose:")
  println(s"  Enabled: ${config.aspose.enabled}")
  config.aspose.licensePath.foreach(p => println(s"  License path: $p"))
  println(s"  Strict license check: ${config.aspose.strictLicenseCheck}")

  println("\nPerformance:")
  println(s"  Thread pool size: ${config.performance.threadPoolSize}")
  println(s"  Buffer size: ${config.performance.bufferSize}")
  println(s"  Max concurrent conversions: ${config.performance.maxConcurrentConversions}")

  println("\nPaths:")
  println(s"  Temp directory: ${config.paths.tempDir}")
  config.paths.cacheDir.foreach(c => println(s"  Cache directory: $c"))

  println("\nEnvironment Variable Overrides:")
  println("  (Set these to customize configuration)")
  println("  XLCR_PROCESSING_TIMEOUT=5m")
  println("  XLCR_MAX_FILE_SIZE=104857600")
  println("  XLCR_THREAD_POOL_SIZE=8")
  println("  XLCR_LIBREOFFICE_ENABLED=true")
  println("  XLCR_ASPOSE_ENABLED=true")
  println("  ... (see docs/CONFIGURATION.md for full list)")
}
```

### Step 6: Documentation

**New File**: `docs/CONFIGURATION.md`

```markdown
# XLCR Configuration Guide

All configuration values can be customized via environment variables.

## Environment Variables

### Processing

| Variable | Default | Description |
|----------|---------|-------------|
| `XLCR_PROCESSING_TIMEOUT` | `5m` | Maximum time for a single conversion (supports s/m/h suffixes) |
| `XLCR_MAX_FILE_SIZE` | `104857600` | Maximum input file size in bytes (100MB) |
| `XLCR_ENABLE_PARALLEL` | `true` | Enable parallel directory processing |

### LibreOffice Backend

| Variable | Default | Description |
|----------|---------|-------------|
| `XLCR_LIBREOFFICE_ENABLED` | `true` | Enable LibreOffice backend |
| `LIBREOFFICE_HOME` | (auto-detect) | Path to LibreOffice installation |
| `XLCR_LIBREOFFICE_MAX_TASKS` | `200` | Max conversions per LibreOffice process |
| `XLCR_LIBREOFFICE_TASK_TIMEOUT` | `2m` | Timeout for single LibreOffice task |
| `XLCR_LIBREOFFICE_QUEUE_TIMEOUT` | `30s` | Timeout waiting in conversion queue |

### Aspose Backend

| Variable | Default | Description |
|----------|---------|-------------|
| `XLCR_ASPOSE_ENABLED` | `true` | Enable Aspose backend |
| `ASPOSE_LICENSE_PATH` | (none) | Path to Aspose license file |
| `XLCR_ASPOSE_STRICT_LICENSE` | `false` | Fail startup if license missing/invalid |

### Performance

| Variable | Default | Description |
|----------|---------|-------------|
| `XLCR_THREAD_POOL_SIZE` | (CPU cores) | Thread pool size for parallel processing |
| `XLCR_BUFFER_SIZE` | `8192` | Buffer size for I/O operations (bytes) |
| `XLCR_MAX_CONCURRENT` | (CPU cores × 2) | Max concurrent conversions |

### Paths

| Variable | Default | Description |
|----------|---------|-------------|
| `XLCR_TEMP_DIR` | (system temp) | Directory for temporary files |
| `XLCR_CACHE_DIR` | (none) | Directory for caching (optional) |

## Examples

### Development

```bash
# Use defaults (works out of box)
sbt run -i input.pdf -o output.pptx
```

### Production - High Throughput

```bash
export XLCR_THREAD_POOL_SIZE=16
export XLCR_MAX_CONCURRENT=32
export XLCR_LIBREOFFICE_MAX_TASKS=500
export XLCR_PROCESSING_TIMEOUT=10m

java -jar xlcr-assembly.jar -i input.pdf -o output.pptx
```

### Production - Conservative

```bash
export XLCR_THREAD_POOL_SIZE=4
export XLCR_MAX_CONCURRENT=8
export XLCR_PROCESSING_TIMEOUT=2m
export XLCR_ASPOSE_STRICT_LICENSE=true

java -jar xlcr-assembly.jar -i input.pdf -o output.pptx
```

### Disable LibreOffice

```bash
export XLCR_LIBREOFFICE_ENABLED=false
# Will only use Aspose backend
```

## Viewing Configuration

```bash
xlcr config
```

Shows active configuration including environment overrides.

## Configuration Validation

Configuration is validated on startup:
- Paths must exist or be creatable
- Timeout values must be positive
- Thread pool sizes must be positive
- LibreOffice home must exist if specified
- Aspose license must exist if strict mode enabled

Invalid configuration will fail fast with clear error message.
```

## Success Criteria

1. ✅ `ApplicationConfig` schema defined with all settings
2. ✅ Environment variable support for all values
3. ✅ Configuration validation on startup
4. ✅ `ApplicationContext` singleton for access
5. ✅ LibreOffice config updated to use `ApplicationContext`
6. ✅ CLI `config` command shows active configuration
7. ✅ `docs/CONFIGURATION.md` documentation created
8. ✅ All hardcoded values eliminated
9. ✅ Logging of configuration on startup
10. ✅ Tests for configuration loading and validation

## Testing Strategy

### Unit Tests

**File**: `core/src/test/scala/config/ApplicationConfigSpec.scala`

```scala
class ApplicationConfigSpec extends AnyFlatSpec with Matchers {

  "ApplicationConfig" should "load defaults without environment" in {
    val config = ApplicationConfig.load()

    config.processing.defaultTimeout should be(5.minutes)
    config.performance.threadPoolSize should be > 0
    config.libreOffice.maxTasksPerProcess should be(200)
  }

  it should "parse duration formats" in {
    withEnv("XLCR_PROCESSING_TIMEOUT" -> "30s") {
      val config = ApplicationConfig.load()
      config.processing.defaultTimeout should be(30.seconds)
    }
  }

  it should "validate positive values" in {
    val invalidConfig = ApplicationConfig.load().copy(
      performance = PerformanceConfig(
        threadPoolSize = -1,
        bufferSize = 8192,
        maxConcurrentConversions = 8
      )
    )

    an[IllegalArgumentException] should be thrownBy {
      ApplicationConfig.validate(invalidConfig)
    }
  }

  "ApplicationContext" should "initialize once" in {
    val config = ApplicationConfig.load()
    ApplicationContext.setConfig(config)

    an[IllegalStateException] should be thrownBy {
      ApplicationContext.setConfig(config) // Second call should fail
    }
  }
}
```

### Integration Tests

Test that configuration flows through to actual components:

```scala
class ConfigurationIntegrationSpec extends AnyFlatSpec with Matchers {

  "LibreOffice backend" should "use configured timeouts" in {
    withEnv("XLCR_LIBREOFFICE_TASK_TIMEOUT" -> "1m") {
      val config = ApplicationConfig.load()
      ApplicationContext.setConfig(config)

      // Verify LibreOfficeConfig picks up the value
      // (requires refactoring LibreOfficeConfig to be testable)
    }
  }
}
```

## Rollout Plan

**Day 1**: Configuration Schema
- Create `ApplicationConfig.scala`
- Create `ApplicationContext.scala`
- Write unit tests

**Day 2**: Integration
- Update `Main.scala` to load config
- Update `LibreOfficeConfig` to use `ApplicationContext`
- Add configuration logging

**Day 3**: CLI and Documentation
- Add `xlcr config` command
- Create `docs/CONFIGURATION.md`
- Update `CLAUDE.md` with configuration section

**Day 4**: Testing and Validation
- Integration tests
- Manual testing with various env vars
- CI testing with different configurations

## Dependencies

- None (independent improvement)

## Risks and Mitigation

| Risk | Mitigation |
|------|------------|
| Breaking existing setups | All defaults match current hardcoded values |
| Environment variable naming conflicts | Use `XLCR_` prefix for all vars |
| Configuration too complex | Start simple, document well, provide examples |
| Thread safety issues | Use AtomicReference, initialize once on startup |

## Future Enhancements

1. **Config Files**: Support for `.xlcrrc` or `xlcr.conf` files
2. **Config Profiles**: Dev/staging/prod profile support
3. **Hot Reload**: Reload config without restart (for specific settings)
4. **Config Export**: Export current config to file for reproducibility
5. **Secrets Management**: Integration with secrets managers (Vault, AWS Secrets)
