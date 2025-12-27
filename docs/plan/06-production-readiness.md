# Production Readiness

**Priority**: High (Production Service Context)
**Effort**: 4-5 days
**Phase**: Production Service Features

## Current State

### The Problem

The codebase lacks **production-grade observability and diagnostics**:

1. **No Metrics**: No instrumentation for tracking:
   - Conversion success/failure rates
   - Latency percentiles (p50, p95, p99)
   - Backend usage distribution
   - Throughput (conversions/second)

2. **Unstructured Logging**: Current logging:
   - Human-readable but hard to parse
   - No structured fields (JSON)
   - No correlation IDs for tracing
   - Missing conversion metadata

3. **No Health Checks**: No way to verify:
   - Service is healthy and ready
   - Dependencies are available (Aspose, LibreOffice)
   - System resources are adequate

4. **Limited Diagnostics**: Hard to troubleshoot production issues:
   - No request tracing
   - No performance profiling hooks
   - Limited visibility into system state

### Impact on Production Service

For production deployment, this causes:
- ❌ Can't monitor service health
- ❌ No alerting on degradation
- ❌ Difficult to troubleshoot issues
- ❌ No capacity planning data
- ❌ Can't measure SLAs/SLOs
- ❌ No distributed tracing

## Proposed Solution

**Production-grade observability**:
1. Metrics abstraction with common providers (Micrometer, Prometheus)
2. Structured JSON logging for production
3. Health check system (readiness, liveness)
4. Diagnostic commands and endpoints
5. Request correlation and tracing

### Design Principles

1. **Observable**: Rich metrics and structured logs
2. **Debuggable**: Correlation IDs, request tracing
3. **Reliable**: Health checks, dependency validation
4. **Performant**: Low-overhead instrumentation
5. **Standard**: Use industry-standard formats and protocols

## Implementation Details

### Step 1: Metrics Abstraction

**New File**: `core/src/main/scala/metrics/Metrics.scala`

```scala
package com.tjclp.xlcr.metrics

import java.util.concurrent.atomic.LongAdder
import scala.concurrent.duration._

/**
 * Metrics abstraction for tracking conversion operations.
 * Designed for easy integration with monitoring systems.
 */
trait MetricsProvider {
  /** Increment a counter */
  def incrementCounter(name: String, tags: Map[String, String] = Map.empty): Unit

  /** Record a timing */
  def recordTiming(name: String, duration: Duration, tags: Map[String, String] = Map.empty): Unit

  /** Set a gauge value */
  def setGauge(name: String, value: Double, tags: Map[String, String] = Map.empty): Unit

  /** Record a histogram value */
  def recordValue(name: String, value: Long, tags: Map[String, String] = Map.empty): Unit
}

/**
 * Default in-memory metrics provider.
 * For production, use PrometheusMetrics or MicrometerMetrics.
 */
class InMemoryMetrics extends MetricsProvider {
  import scala.jdk.CollectionConverters._

  private val counters = new java.util.concurrent.ConcurrentHashMap[String, LongAdder]()
  private val timings = new java.util.concurrent.ConcurrentHashMap[String, List[Long]]()
  private val gauges = new java.util.concurrent.ConcurrentHashMap[String, Double]()

  override def incrementCounter(name: String, tags: Map[String, String] = Map.empty): Unit = {
    val key = metricKey(name, tags)
    counters.computeIfAbsent(key, _ => new LongAdder()).increment()
  }

  override def recordTiming(name: String, duration: Duration, tags: Map[String, String] = Map.empty): Unit = {
    val key = metricKey(name, tags)
    val millis = duration.toMillis
    timings.compute(key, (_, existing) => {
      val list = Option(existing).getOrElse(List.empty)
      (millis :: list).take(1000) // Keep last 1000 samples
    })
  }

  override def setGauge(name: String, value: Double, tags: Map[String, String] = Map.empty): Unit = {
    val key = metricKey(name, tags)
    gauges.put(key, value)
  }

  override def recordValue(name: String, value: Long, tags: Map[String, String] = Map.empty): Unit = {
    // Same as timing but without duration conversion
    val key = metricKey(name, tags)
    timings.compute(key, (_, existing) => {
      val list = Option(existing).getOrElse(List.empty)
      (value :: list).take(1000)
    })
  }

  def getCounters: Map[String, Long] = {
    counters.asScala.view.mapValues(_.sum()).toMap
  }

  def getTimingStats(name: String): Option[TimingStats] = {
    timings.asScala.find(_._1.startsWith(name)).map { case (_, values) =>
      if (values.isEmpty) TimingStats(0, 0, 0, 0, 0)
      else {
        val sorted = values.sorted
        TimingStats(
          count = values.size,
          min = sorted.head,
          max = sorted.last,
          p50 = sorted(sorted.size / 2),
          p95 = sorted((sorted.size * 0.95).toInt)
        )
      }
    }
  }

  private def metricKey(name: String, tags: Map[String, String]): String = {
    if (tags.isEmpty) name
    else s"$name{${tags.map { case (k, v) => s"$k=$v" }.mkString(",")}}"
  }

  case class TimingStats(count: Int, min: Long, max: Long, p50: Long, p95: Long)
}

/**
 * Global metrics instance.
 * In production, inject via dependency injection or replace with Prometheus/Micrometer.
 */
object Metrics {
  @volatile private var provider: MetricsProvider = new InMemoryMetrics()

  def setProvider(p: MetricsProvider): Unit = {
    provider = p
  }

  def counter(name: String, tags: Map[String, String] = Map.empty): Unit = {
    provider.incrementCounter(name, tags)
  }

  def timer[T](name: String, tags: Map[String, String] = Map.empty)(block: => T): T = {
    val start = System.nanoTime()
    try {
      block
    } finally {
      val duration = (System.nanoTime() - start).nanos
      provider.recordTiming(name, duration, tags)
    }
  }

  def gauge(name: String, value: Double, tags: Map[String, String] = Map.empty): Unit = {
    provider.setGauge(name, value, tags)
  }

  def histogram(name: String, value: Long, tags: Map[String, String] = Map.empty): Unit = {
    provider.recordValue(name, value, tags)
  }
}
```

**Key Metrics to Track**:
```scala
// In Pipeline.scala
Metrics.counter("xlcr.conversions.started", Map("inputType" -> input, "outputType" -> output))
Metrics.counter("xlcr.conversions.succeeded", Map("backend" -> backend))
Metrics.counter("xlcr.conversions.failed", Map("backend" -> backend, "error" -> errorType))
Metrics.timer("xlcr.conversions.duration", Map("backend" -> backend)) { /* conversion code */ }
Metrics.histogram("xlcr.conversions.input_size", inputSize, Map("inputType" -> input))
Metrics.histogram("xlcr.conversions.output_size", outputSize, Map("outputType" -> output))
```

### Step 2: Structured Logging

**New File**: `core/src/main/scala/logging/StructuredLogger.scala`

```scala
package com.tjclp.xlcr.logging

import com.typesafe.scalalogging.Logger
import org.slf4j.{LoggerFactory, MDC}

/**
 * Structured logging support with JSON output.
 * Compatible with log aggregation systems (ELK, Splunk, etc.)
 */
object StructuredLogger {

  /**
   * Add context to MDC for current thread.
   * All log statements in this thread will include these fields.
   */
  def withContext[T](context: Map[String, String])(block: => T): T = {
    context.foreach { case (key, value) => MDC.put(key, value) }
    try {
      block
    } finally {
      context.keys.foreach(MDC.remove)
    }
  }

  /**
   * Generate a correlation ID for request tracing.
   */
  def generateCorrelationId(): String = {
    java.util.UUID.randomUUID().toString
  }

  /**
   * Log a conversion event with structured data.
   */
  def logConversion(
    logger: Logger,
    level: String,
    message: String,
    inputPath: String,
    outputPath: String,
    inputType: String,
    outputType: String,
    backend: String,
    durationMs: Long,
    inputSize: Long,
    outputSize: Long,
    correlationId: Option[String] = None
  ): Unit = {

    val context = Map(
      "event" -> "conversion",
      "inputPath" -> inputPath,
      "outputPath" -> outputPath,
      "inputType" -> inputType,
      "outputType" -> outputType,
      "backend" -> backend,
      "durationMs" -> durationMs.toString,
      "inputSize" -> inputSize.toString,
      "outputSize" -> outputSize.toString
    ) ++ correlationId.map("correlationId" -> _)

    withContext(context) {
      level.toLowerCase match {
        case "info" => logger.info(message)
        case "warn" => logger.warn(message)
        case "error" => logger.error(message)
        case _ => logger.debug(message)
      }
    }
  }
}
```

**Configure Logback for JSON** (`core/src/main/resources/logback.xml`):

```xml
<configuration>
  <!-- Console appender for development -->
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- JSON appender for production -->
  <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeMdc>true</includeMdc>
      <includeContext>true</includeContext>
      <includeCallerData>false</includeCallerData>
      <fieldNames>
        <timestamp>timestamp</timestamp>
        <message>message</message>
        <logger>logger</logger>
        <thread>thread</thread>
        <level>level</level>
      </fieldNames>
    </encoder>
  </appender>

  <!-- File appender for production -->
  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/xlcr.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
      <fileNamePattern>logs/xlcr.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
      <maxHistory>30</maxHistory>
      <totalSizeCap>5GB</totalSizeCap>
    </rollingPolicy>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeMdc>true</includeMdc>
    </encoder>
  </appender>

  <!-- Use JSON logging in production, console in dev -->
  <if condition='property("env").contains("production")'>
    <then>
      <root level="INFO">
        <appender-ref ref="JSON" />
        <appender-ref ref="FILE" />
      </root>
    </then>
    <else>
      <root level="DEBUG">
        <appender-ref ref="CONSOLE" />
      </root>
    </else>
  </if>

  <!-- Reduce noise from dependencies -->
  <logger name="org.apache.tika" level="WARN"/>
  <logger name="org.apache.poi" level="WARN"/>
  <logger name="com.aspose" level="WARN"/>
</configuration>
```

**Add dependency** (`build.sbt`):
```scala
"net.logstash.logback" % "logstash-logback-encoder" % "7.4"
```

### Step 3: Health Check System

**New File**: `core/src/main/scala/health/HealthCheck.scala`

```scala
package com.tjclp.xlcr.health

import com.tjclp.xlcr.bridges.BridgeRegistry
import com.tjclp.xlcr.config.ApplicationContext
import scala.util.{Try, Success, Failure}

/**
 * Health check system for production readiness.
 * Implements liveness and readiness probes for Kubernetes/container orchestration.
 */
object HealthCheck {

  sealed trait HealthStatus {
    def isHealthy: Boolean
    def httpCode: Int
  }

  case object Healthy extends HealthStatus {
    val isHealthy = true
    val httpCode = 200
  }

  case class Unhealthy(reason: String) extends HealthStatus {
    val isHealthy = false
    val httpCode = 503
  }

  case class HealthReport(
    status: HealthStatus,
    checks: Map[String, CheckResult],
    timestamp: Long = System.currentTimeMillis()
  ) {
    def isHealthy: Boolean = status.isHealthy

    def toJson: String = {
      val checkResults = checks.map { case (name, result) =>
        s""""$name": {"status": "${if (result.passed) "pass" else "fail"}", "message": "${result.message}"}"""
      }.mkString(",\n    ")

      s"""{
         |  "status": "${if (isHealthy) "healthy" else "unhealthy"}",
         |  "timestamp": $timestamp,
         |  "checks": {
         |    $checkResults
         |  }
         |}""".stripMargin
    }
  }

  case class CheckResult(passed: Boolean, message: String)

  /**
   * Liveness probe: Is the service running?
   * Should be lightweight, only checks if process is alive.
   */
  def liveness(): HealthReport = {
    HealthReport(
      status = Healthy,
      checks = Map(
        "process" -> CheckResult(true, "Process is running")
      )
    )
  }

  /**
   * Readiness probe: Is the service ready to accept traffic?
   * Checks dependencies, configuration, backends.
   */
  def readiness(): HealthReport = {
    val checks = Map(
      "configuration" -> checkConfiguration(),
      "backends" -> checkBackends(),
      "disk_space" -> checkDiskSpace(),
      "memory" -> checkMemory()
    )

    val overallStatus = if (checks.values.forall(_.passed)) Healthy
    else Unhealthy("One or more checks failed")

    HealthReport(overallStatus, checks)
  }

  /**
   * Detailed health check with all diagnostics.
   */
  def detailed(): HealthReport = {
    val readinessChecks = readiness().checks

    val additionalChecks = Map(
      "aspose_license" -> checkAsposeLicense(),
      "libreoffice" -> checkLibreOffice(),
      "temp_directory" -> checkTempDirectory()
    )

    val allChecks = readinessChecks ++ additionalChecks

    val overallStatus = if (allChecks.values.forall(_.passed)) Healthy
    else Unhealthy("One or more checks failed")

    HealthReport(overallStatus, allChecks)
  }

  private def checkConfiguration(): CheckResult = {
    Try {
      val config = ApplicationContext.config
      ApplicationConfig.validate(config)
      CheckResult(true, "Configuration valid")
    }.recover {
      case ex: Exception =>
        CheckResult(false, s"Configuration invalid: ${ex.getMessage}")
    }.get
  }

  private def checkBackends(): CheckResult = {
    val bridges = BridgeRegistry.listAllBridges()
    val availableCount = bridges.count(_.available)
    val totalCount = bridges.size

    if (availableCount == 0) {
      CheckResult(false, "No backends available")
    } else if (availableCount < totalCount / 2) {
      CheckResult(false, s"Only $availableCount/$totalCount backends available")
    } else {
      CheckResult(true, s"$availableCount/$totalCount backends available")
    }
  }

  private def checkDiskSpace(): CheckResult = {
    val tempDir = new java.io.File(ApplicationContext.paths.tempDir)
    val freeSpace = tempDir.getFreeSpace
    val totalSpace = tempDir.getTotalSpace
    val freePercent = (freeSpace.toDouble / totalSpace * 100).toInt

    if (freePercent < 10) {
      CheckResult(false, s"Low disk space: $freePercent% free")
    } else {
      CheckResult(true, s"Disk space OK: $freePercent% free")
    }
  }

  private def checkMemory(): CheckResult = {
    val runtime = Runtime.getRuntime
    val maxMemory = runtime.maxMemory()
    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
    val usedPercent = (usedMemory.toDouble / maxMemory * 100).toInt

    if (usedPercent > 90) {
      CheckResult(false, s"High memory usage: $usedPercent%")
    } else {
      CheckResult(true, s"Memory OK: $usedPercent% used")
    }
  }

  private def checkAsposeLicense(): CheckResult = {
    Try {
      com.tjclp.xlcr.utils.aspose.AsposeLicense.initializeIfNeeded()
      CheckResult(true, "Aspose license valid")
    }.recover {
      case ex: Exception =>
        CheckResult(false, s"Aspose license issue: ${ex.getMessage}")
    }.getOrElse(CheckResult(false, "Aspose not available"))
  }

  private def checkLibreOffice(): CheckResult = {
    Try {
      val isAvailable = com.tjclp.xlcr.config.LibreOfficeConfig.isAvailable()
      if (isAvailable) {
        CheckResult(true, com.tjclp.xlcr.config.LibreOfficeConfig.availabilityStatus())
      } else {
        CheckResult(false, "LibreOffice not available")
      }
    }.recover {
      case ex: Exception =>
        CheckResult(false, s"LibreOffice check failed: ${ex.getMessage}")
    }.get
  }

  private def checkTempDirectory(): CheckResult = {
    val tempDir = new java.io.File(ApplicationContext.paths.tempDir)

    if (!tempDir.exists()) {
      CheckResult(false, s"Temp directory does not exist: ${tempDir.getAbsolutePath}")
    } else if (!tempDir.canWrite()) {
      CheckResult(false, s"Temp directory not writable: ${tempDir.getAbsolutePath}")
    } else {
      CheckResult(true, s"Temp directory OK: ${tempDir.getAbsolutePath}")
    }
  }
}
```

**CLI Commands**:

```scala
// In CommonCLI.scala
def healthCommand: Opts[Unit] = Opts.subcommand("health", "Check service health") {
  (
    Opts.flag("liveness", "Run liveness probe").orFalse,
    Opts.flag("readiness", "Run readiness probe").orFalse,
    Opts.flag("detailed", "Run detailed health check").orFalse
  ).mapN { (liveness, readiness, detailed) =>
    val report = if (liveness) HealthCheck.liveness()
    else if (readiness) HealthCheck.readiness()
    else HealthCheck.detailed()

    println(report.toJson)

    if (!report.isHealthy) {
      System.exit(1)
    }
  }
}
```

### Step 4: Diagnostic Commands

**New File**: `core/src/main/scala/diagnostics/Diagnostics.scala`

```scala
package com.tjclp.xlcr.diagnostics

import com.tjclp.xlcr.config.ApplicationContext
import com.tjclp.xlcr.bridges.BridgeRegistry
import com.tjclp.xlcr.metrics.BackendMetrics

/**
 * Diagnostic utilities for troubleshooting.
 */
object Diagnostics {

  def printSystemInfo(): Unit = {
    println("System Information")
    println("=" * 80)

    println("\nJVM:")
    println(s"  Java version: ${System.getProperty("java.version")}")
    println(s"  Java vendor: ${System.getProperty("java.vendor")}")
    println(s"  Java home: ${System.getProperty("java.home")}")

    val runtime = Runtime.getRuntime
    println(s"\nMemory:")
    println(s"  Max: ${runtime.maxMemory() / (1024 * 1024)}MB")
    println(s"  Total: ${runtime.totalMemory() / (1024 * 1024)}MB")
    println(s"  Free: ${runtime.freeMemory() / (1024 * 1024)}MB")
    println(s"  Used: ${(runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)}MB")

    println(s"\nProcessors: ${runtime.availableProcessors()}")

    println("\nOperating System:")
    println(s"  Name: ${System.getProperty("os.name")}")
    println(s"  Version: ${System.getProperty("os.version")}")
    println(s"  Architecture: ${System.getProperty("os.arch")}")

    val tempDir = new java.io.File(ApplicationContext.paths.tempDir)
    println(s"\nDisk Space (temp dir):")
    println(s"  Path: ${tempDir.getAbsolutePath}")
    println(s"  Total: ${tempDir.getTotalSpace / (1024 * 1024 * 1024)}GB")
    println(s"  Free: ${tempDir.getFreeSpace / (1024 * 1024 * 1024)}GB")
    println(s"  Usable: ${tempDir.getUsableSpace / (1024 * 1024 * 1024)}GB")
  }

  def printConfigInfo(): Unit = {
    println("\nConfiguration:")
    println("=" * 80)

    val config = ApplicationContext.config

    println(s"\nProcessing:")
    println(s"  Timeout: ${config.processing.defaultTimeout}")
    println(s"  Max file size: ${config.processing.maxFileSize / (1024 * 1024)}MB")
    println(s"  Parallel: ${config.processing.enableParallel}")

    println(s"\nPerformance:")
    println(s"  Thread pool size: ${config.performance.threadPoolSize}")
    println(s"  Max concurrent: ${config.performance.maxConcurrentConversions}")
    println(s"  Buffer size: ${config.performance.bufferSize}")

    println(s"\nBackends:")
    println(s"  Aspose enabled: ${config.aspose.enabled}")
    println(s"  LibreOffice enabled: ${config.libreOffice.enabled}")
  }

  def printBackendInfo(): Unit = {
    println("\nBackends:")
    println("=" * 80)

    val bridges = BridgeRegistry.listAllBridges()
    val byBackend = bridges.groupBy(_.backend)

    byBackend.foreach { case (backend, bridgeList) =>
      val available = bridgeList.count(_.available)
      val status = if (available > 0) "✓" else "✗"

      println(s"\n[$status] $backend:")
      println(s"  Bridges: ${bridgeList.size}")
      println(s"  Available: $available")

      if (available > 0) {
        val conversions = bridgeList
          .filter(_.available)
          .map(b => s"${b.inputType}→${b.outputType}")
          .distinct
          .sorted

        println(s"  Conversions: ${conversions.mkString(", ")}")
      }
    }
  }

  def printMetricsInfo(): Unit = {
    println("\nMetrics:")
    println("=" * 80)

    BackendMetrics.printSummary()
  }

  def printAllDiagnostics(): Unit = {
    printSystemInfo()
    printConfigInfo()
    printBackendInfo()
    printMetricsInfo()
  }
}
```

**CLI Command**:

```scala
def diagnosticsCommand: Opts[Unit] = Opts.subcommand("diagnostics", "Show diagnostic information") {
  Opts(Diagnostics.printAllDiagnostics())
}
```

### Step 5: Update Pipeline with Instrumentation

**File**: `core/src/main/scala/Pipeline.scala`

Add comprehensive instrumentation:

```scala
import com.tjclp.xlcr.metrics.{Metrics, BackendMetrics}
import com.tjclp.xlcr.logging.StructuredLogger

def process(inputPath: String, outputPath: String, backend: Option[String] = None): Try[Unit] = {

  // Generate correlation ID for request tracing
  val correlationId = StructuredLogger.generateCorrelationId()

  StructuredLogger.withContext(Map("correlationId" -> correlationId)) {

    logger.info(s"Processing conversion request: $inputPath → $outputPath")

    Metrics.counter("xlcr.conversions.started")

    val startTime = System.nanoTime()
    val inputFile = new java.io.File(inputPath)
    val inputSize = if (inputFile.exists()) inputFile.length() else 0L

    val result = for {
      mimeType <- detectMimeType(inputPath)
      outputMimeType <- detectMimeType(outputPath)

      _ = Metrics.counter("xlcr.conversions.started.by_type",
        Map("inputType" -> mimeType.toString, "outputType" -> outputMimeType.toString))

      content <- extractContent(inputPath, mimeType)
      converted <- convert(content, outputMimeType, ConversionConfig(backend))
      _ <- writeOutput(converted, outputPath)

    } yield {
      val durationMs = (System.nanoTime() - startTime) / 1_000_000
      val outputSize = new java.io.File(outputPath).length()

      // Record metrics
      Metrics.counter("xlcr.conversions.succeeded",
        Map("backend" -> converted.metadata.getOrElse("backend", "unknown").toString))
      Metrics.histogram("xlcr.conversions.input_size", inputSize,
        Map("inputType" -> mimeType.toString))
      Metrics.histogram("xlcr.conversions.output_size", outputSize,
        Map("outputType" -> outputMimeType.toString))

      val backendUsed = converted.metadata.getOrElse("backend", "unknown").toString
      BackendMetrics.recordConversion(backendUsed, durationMs)

      // Structured logging
      StructuredLogger.logConversion(
        logger, "info", "Conversion completed successfully",
        inputPath, outputPath,
        mimeType.toString, outputMimeType.toString,
        backendUsed, durationMs,
        inputSize, outputSize,
        Some(correlationId)
      )
    }

    result.recoverWith {
      case ex: ConversionException =>
        val durationMs = (System.nanoTime() - startTime) / 1_000_000

        Metrics.counter("xlcr.conversions.failed",
          Map("error" -> ex.category.name, "backend" -> ex.context.getOrElse("backend", "unknown").toString))

        BackendMetrics.recordFailure(ex.context.getOrElse("backend", "unknown").toString)

        logger.error(s"Conversion failed: ${ex.userMessage}", ex)
        Failure(ex)

      case ex: Throwable =>
        Metrics.counter("xlcr.conversions.failed", Map("error" -> "unexpected"))
        logger.error(s"Unexpected error during conversion", ex)
        Failure(ex)
    }
  }
}
```

## Success Criteria

1. ✅ Metrics abstraction implemented with common interface
2. ✅ Key conversion metrics instrumented (counters, timers, histograms)
3. ✅ Structured JSON logging configured for production
4. ✅ Request correlation IDs in all logs
5. ✅ Health check system with liveness and readiness probes
6. ✅ Diagnostic CLI commands implemented
7. ✅ Backend metrics tracking integrated
8. ✅ Logback configured for JSON output
9. ✅ Documentation for production deployment
10. ✅ Example Prometheus/Grafana dashboards

## Testing Strategy

### Unit Tests

```scala
class MetricsSpec extends AnyFlatSpec with Matchers {
  "Metrics" should "track conversion counts" in {
    val metrics = new InMemoryMetrics()
    Metrics.setProvider(metrics)

    Metrics.counter("test.conversions", Map("backend" -> "aspose"))
    Metrics.counter("test.conversions", Map("backend" -> "aspose"))
    Metrics.counter("test.conversions", Map("backend" -> "libreoffice"))

    val counts = metrics.getCounters
    counts("test.conversions{backend=aspose}") shouldBe 2
    counts("test.conversions{backend=libreoffice}") shouldBe 1
  }

  it should "record timing statistics" in {
    val metrics = new InMemoryMetrics()

    Metrics.timer("test.operation", Map("backend" -> "test")) {
      Thread.sleep(100)
    }

    val stats = metrics.getTimingStats("test.operation")
    stats shouldBe defined
    stats.get.count shouldBe 1
    stats.get.p50 should be >= 100L
  }
}

class HealthCheckSpec extends AnyFlatSpec with Matchers {
  "HealthCheck" should "return healthy when all checks pass" in {
    val report = HealthCheck.liveness()

    report.isHealthy shouldBe true
    report.status.httpCode shouldBe 200
  }

  it should "detect configuration issues" in {
    // Test with invalid configuration
    // ...
  }

  it should "format JSON output" in {
    val report = HealthCheck.liveness()
    val json = report.toJson

    json should include("\"status\"")
    json should include("\"checks\"")
    json should include("\"timestamp\"")
  }
}
```

### Integration Tests

```scala
class ProductionFeaturesIntegrationSpec extends AnyFlatSpec with Matchers {
  "Pipeline with instrumentation" should "record metrics" in {
    val pipeline = new Pipeline()

    // Perform conversion
    pipeline.process(TestFixtures.TestFiles.simplePdf.toString, "/tmp/output.pptx")

    // Verify metrics were recorded
    val stats = BackendMetrics.getStats
    stats should not be empty
  }

  it should "include correlation IDs in logs" in {
    // Capture logs
    // Verify correlation ID present
  }

  "Health check" should "detect unavailable backends" in {
    // Disable backends
    // Run health check
    // Verify unhealthy status
  }
}
```

## Rollout Plan

**Day 1**: Metrics Foundation
- Implement Metrics abstraction
- Add key metrics to Pipeline
- Write unit tests

**Day 2**: Structured Logging
- Configure Logback for JSON
- Implement StructuredLogger
- Add correlation IDs
- Test log output

**Day 3**: Health Checks
- Implement HealthCheck system
- Add CLI commands
- Test all health check scenarios

**Day 4**: Diagnostics and Integration
- Implement Diagnostics commands
- Integrate BackendMetrics
- Full pipeline instrumentation
- Integration tests

**Day 5**: Documentation and Examples
- Production deployment guide
- Example Prometheus config
- Example Grafana dashboards
- Runbook for common issues

## Documentation

**New File**: `docs/PRODUCTION.md`

````markdown
# Production Deployment Guide

## Observability

### Metrics

XLCR exposes metrics for monitoring conversion operations:

- `xlcr.conversions.started` - Total conversions started
- `xlcr.conversions.succeeded` - Successful conversions (tagged by backend)
- `xlcr.conversions.failed` - Failed conversions (tagged by error type)
- `xlcr.conversions.duration` - Conversion latency (histogram)
- `xlcr.conversions.input_size` - Input file sizes (histogram)
- `xlcr.conversions.output_size` - Output file sizes (histogram)

**Example Prometheus scrape config**:
```yaml
scrape_configs:
  - job_name: 'xlcr'
    static_configs:
      - targets: ['localhost:9090']
```

### Structured Logging

Set environment variable for JSON logging:
```bash
export env=production
java -jar xlcr-assembly.jar ...
```

Logs include:
- Correlation IDs for request tracing
- Conversion metadata (input/output types, backend, duration)
- Error context

### Health Checks

Kubernetes liveness and readiness probes:

```yaml
livenessProbe:
  exec:
    command: ["java", "-jar", "xlcr-assembly.jar", "health", "--liveness"]
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  exec:
    command: ["java", "-jar", "xlcr-assembly.jar", "health", "--readiness"]
  initialDelaySeconds: 5
  periodSeconds: 5
```

## Monitoring

### Key Metrics to Alert On

1. **High Error Rate**: `xlcr.conversions.failed / xlcr.conversions.started > 0.05`
2. **High Latency**: `xlcr.conversions.duration_p95 > 30000` (30 seconds)
3. **Backend Unavailable**: Health check failures
4. **Low Disk Space**: < 10% free
5. **High Memory Usage**: > 90% used

### Example Grafana Dashboard

Panels to include:
- Conversion rate (per minute)
- Success rate (%)
- Latency percentiles (p50, p95, p99)
- Backend usage distribution
- Error breakdown by type
- Active conversions (gauge)

## Troubleshooting

### Common Issues

**"No bridge found" errors**:
```bash
xlcr backends  # List available backends
xlcr diagnostics  # Check backend status
```

**High latency**:
```bash
xlcr diagnostics  # Check system resources
# Adjust thread pool size:
export XLCR_THREAD_POOL_SIZE=16
```

**Memory issues**:
```bash
# Increase JVM heap:
java -Xmx4g -jar xlcr-assembly.jar ...
```
````

## Dependencies

- **02-error-handling.md**: ConversionException used in metrics tagging
- **03-configuration-management.md**: ApplicationContext used in health checks
- **05-backend-observability.md**: BackendMetrics builds on backend observability

## Risks and Mitigation

| Risk | Mitigation |
|------|------------|
| Metrics overhead | Use lock-free counters, batch exports |
| Log volume explosion | Configure appropriate log levels, sampling |
| Health check false positives | Tune check thresholds, add grace periods |
| JSON logging performance | Use async appenders, configure buffer sizes |

## Future Enhancements

1. **OpenTelemetry**: Replace custom metrics with OTel for standardization
2. **Distributed Tracing**: Add span tracking for multi-service deployments
3. **Real-time Dashboards**: Web UI for live metrics viewing
4. **Auto-scaling Triggers**: Export metrics to enable auto-scaling
5. **SLA Tracking**: Automated SLA/SLO monitoring and reporting
