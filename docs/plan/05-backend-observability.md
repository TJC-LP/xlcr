# Backend Observability

**Priority**: High (Critical Pain Point)
**Effort**: 3-4 days
**Phase**: Backend Observability

## Current State

### The Problem

Backend selection is **opaque and difficult to debug**:

1. **No Visibility**: Users don't know which backend is being used for a conversion
2. **Discovery Gap**: No way to list available backends and their capabilities
3. **Debugging Difficulty**: When conversion fails, unclear if it's backend-specific
4. **Testing Challenge**: Hard to test with specific backends
5. **String-Based Detection**: Backend name inferred from class name using string matching (`BridgeRegistry.scala:200-214`)

### Current Backend Selection

The priority system works well internally:
- **HIGH Priority**: Aspose bridges (preferred)
- **DEFAULT Priority**: LibreOffice bridges (fallback)
- **LOW Priority**: Core/minimal implementations

But users have no visibility into:
- Which backends are available?
- Which backend was selected for a conversion?
- Why a specific backend was chosen?
- What formats does each backend support?

### Impact

For production service deployment:
- ❌ Difficult to diagnose conversion failures
- ❌ Can't verify Aspose license is working
- ❌ Hard to test LibreOffice fallback behavior
- ❌ No metrics on backend usage
- ❌ Users confused about which converter is being used

## Proposed Solution

**Make backend selection observable and controllable**:
1. CLI command to list available backends (`xlcr backends`)
2. Enhanced logging at bridge selection time
3. Availability checks for backends
4. Explicit backend declaration (not string matching)
5. Backend usage tracking for metrics

### Design Principles

1. **Discoverability**: Easy to see what's available
2. **Transparency**: Clear logging of selection decisions
3. **Diagnosability**: Helpful errors when backends unavailable
4. **Testability**: Easy to force specific backend for testing
5. **Monitoring**: Track backend usage in production

## Implementation Details

### Step 1: Explicit Backend Declaration

**Modify**: `core/src/main/scala/bridges/BaseBridge.scala`

Add backend name to bridge interface:

```scala
trait Bridge[I <: MimeType, O <: MimeType] {
  def inputMimeType: I
  def outputMimeType: O
  def priority: Priority

  /** Backend name for this bridge (e.g., "aspose", "libreoffice", "core") */
  def backend: String

  /** Check if this bridge is available and ready to use */
  def isAvailable: Boolean = true // Default: always available

  /** Human-readable description of this bridge */
  def description: String = s"Converts ${inputMimeType} to ${outputMimeType}"

  def convert(input: FileContent[I], config: Option[BridgeConfig]): Try[FileContent[O]]
}
```

**Update trait helpers**:

```scala
// core-aspose/src/main/scala/bridges/HighPriorityBridge.scala
trait HighPriorityBridge extends Bridge[_, _] {
  override def priority: Priority = Priority.HIGH
  override def backend: String = "aspose"

  override def isAvailable: Boolean = {
    // Check if Aspose license is valid
    try {
      AsposeLicense.initializeIfNeeded()
      true
    } catch {
      case _: Exception => false
    }
  }
}

// core-libreoffice/src/main/scala/bridges/LibreOfficeBridge.scala (new trait)
trait LibreOfficeBridge extends Bridge[_, _] {
  override def backend: String = "libreoffice"

  override def isAvailable: Boolean = {
    LibreOfficeConfig.isAvailable()
  }
}

// core/src/main/scala/bridges/CoreBridge.scala (new trait)
trait CoreBridge extends Bridge[_, _] {
  override def backend: String = "core"
  override def priority: Priority = Priority.LOW
}
```

**Update**: `core-libreoffice/src/main/scala/config/LibreOfficeConfig.scala`

Add availability check (addressing review finding):

```scala
object LibreOfficeConfig {

  /**
   * Check if LibreOffice is available without initializing.
   * Useful for pre-flight checks and backend discovery.
   */
  def isAvailable(): Boolean = {
    detectLibreOfficeHome().isDefined
  }

  /**
   * Get detailed availability status.
   */
  def availabilityStatus(): String = {
    detectLibreOfficeHome() match {
      case Some(home) =>
        if (new File(home).exists()) s"Available at $home"
        else s"Configured but not found at $home"
      case None =>
        "Not found - set LIBREOFFICE_HOME or install in standard location"
    }
  }

  // ... existing code
}
```

### Step 2: Backend Registry Enhancement

**Update**: `core/src/main/scala/bridges/BridgeRegistry.scala`

Replace string-based backend detection (lines 200-214) with explicit backend field:

```scala
// OLD (lines 200-214):
// private def detectBackendName(bridge: Bridge[_, _]): String = {
//   val className = bridge.getClass.getName.toLowerCase
//   if (className.contains("aspose")) "aspose"
//   else if (className.contains("libreoffice")) "libreoffice"
//   else if (className.contains("tika")) "tika"
//   else "core"
// }

// NEW:
private def getBackendName(bridge: Bridge[_, _]): String = {
  bridge.backend
}

// Update logging to include backend explicitly
override def findBridge[I <: MimeType, O <: MimeType](
  inputType: I,
  outputType: O,
  backend: Option[String] = None
): Option[Bridge[I, O]] = {

  val candidates = backend match {
    case Some(requestedBackend) =>
      findBridgeWithBackend(inputType, outputType, Some(requestedBackend))
        .map(b => List(b))
        .getOrElse(Nil)

    case None =>
      registry
        .get((inputType, outputType))
        .getOrElse(Nil)
        .filter(_.isAvailable) // NEW: Filter by availability
        .sortBy(v => -v.priority.value)
  }

  candidates.headOption.map { bridge =>
    logger.info(s"Selected bridge: ${bridge.getClass.getSimpleName} " +
      s"(backend=${bridge.backend}, priority=${bridge.priority}, " +
      s"available=${bridge.isAvailable})")

    // Log alternatives that were considered
    if (candidates.size > 1) {
      logger.debug(s"Other available bridges: ${
        candidates.tail.map(b => s"${b.getClass.getSimpleName}(${b.backend})").mkString(", ")
      }")
    }

    bridge
  }
}

/**
 * List all registered bridges with their metadata.
 */
def listAllBridges(): List[BridgeMetadata] = {
  registry.flatMap { case ((input, output), bridges) =>
    bridges.map { bridge =>
      BridgeMetadata(
        name = bridge.getClass.getSimpleName,
        backend = bridge.backend,
        inputType = input.toString,
        outputType = output.toString,
        priority = bridge.priority,
        available = bridge.isAvailable,
        description = bridge.description
      )
    }
  }.toList.sortBy(b => (-b.priority.value, b.backend, b.name))
}

case class BridgeMetadata(
  name: String,
  backend: String,
  inputType: String,
  outputType: String,
  priority: Priority,
  available: Boolean,
  description: String
)
```

### Step 3: Backend Discovery CLI Command

**New File**: `core/src/main/scala/cli/BackendsCommand.scala`

```scala
package com.tjclp.xlcr.cli

import com.tjclp.xlcr.bridges.BridgeRegistry
import com.tjclp.xlcr.config.Priority
import com.typesafe.scalalogging.LazyLogging

/**
 * CLI command to list available backends and bridges.
 */
object BackendsCommand extends LazyLogging {

  def execute(verbose: Boolean = false): Unit = {
    println("XLCR Available Backends")
    println("=" * 80)

    val allBridges = BridgeRegistry.listAllBridges()

    // Group by backend
    val byBackend = allBridges.groupBy(_.backend)

    byBackend.toList.sortBy { case (backend, bridges) =>
      // Sort by highest priority bridge in backend
      -bridges.map(_.priority.value).max
    }.foreach { case (backend, bridges) =>
      printBackend(backend, bridges, verbose)
    }

    // Summary
    println("\nSummary:")
    println(s"  Total bridges: ${allBridges.size}")
    println(s"  Backends: ${byBackend.keys.mkString(", ")}")
    println(s"  Available bridges: ${allBridges.count(_.available)}")
    println(s"  Unavailable bridges: ${allBridges.count(!_.available)}")

    // Warnings for unavailable backends
    val unavailableBackends = byBackend.filter { case (_, bridges) =>
      bridges.forall(!_.available)
    }

    if (unavailableBackends.nonEmpty) {
      println("\n⚠️  Warnings:")
      unavailableBackends.foreach { case (backend, _) =>
        println(s"  - Backend '$backend' is unavailable")
        printAvailabilityHelp(backend)
      }
    }
  }

  private def printBackend(
    backend: String,
    bridges: List[BridgeRegistry.BridgeMetadata],
    verbose: Boolean
  ): Unit = {
    val status = if (bridges.exists(_.available)) "✓" else "✗"
    val priorityStr = bridges.map(_.priority).max match {
      case Priority.HIGH => "HIGH"
      case Priority.DEFAULT => "DEFAULT"
      case Priority.LOW => "LOW"
    }

    println(s"\n[$status] $backend (priority: $priorityStr)")

    if (verbose) {
      bridges.sortBy(b => (b.inputType, b.outputType)).foreach { bridge =>
        val availStr = if (bridge.available) "✓" else "✗"
        println(f"    $availStr%-2s ${bridge.inputType}%-40s → ${bridge.outputType}")
        if (verbose && bridge.description.nonEmpty) {
          println(f"       ${bridge.description}")
        }
      }
    } else {
      // Compact format: just show supported conversions
      val conversions = bridges
        .map(b => s"${formatMimeType(b.inputType)}→${formatMimeType(b.outputType)}")
        .distinct
        .sorted

      println(s"    Conversions (${conversions.size}): ${conversions.mkString(", ")}")
    }
  }

  private def formatMimeType(mimeType: String): String = {
    // Simplify MIME type for display
    mimeType match {
      case t if t.contains("pdf") => "PDF"
      case t if t.contains("presentationml") => "PPTX"
      case t if t.contains("wordprocessingml") => "DOCX"
      case t if t.contains("spreadsheetml") => "XLSX"
      case t if t.contains("html") => "HTML"
      case t if t.contains("image/png") => "PNG"
      case t if t.contains("image/jpeg") => "JPG"
      case _ => mimeType.split("/").last.toUpperCase
    }
  }

  private def printAvailabilityHelp(backend: String): Unit = {
    backend match {
      case "aspose" =>
        println("      To enable Aspose: Set ASPOSE_LICENSE_PATH environment variable")
        println("      or place license file in expected location")

      case "libreoffice" =>
        println("      To enable LibreOffice: Install LibreOffice and set LIBREOFFICE_HOME")
        println("      macOS: brew install --cask libreoffice")
        println("      Linux: sudo apt-get install libreoffice")
        println("      Windows: Download from https://www.libreoffice.org/")

      case _ =>
        println(s"      Check documentation for '$backend' backend setup")
    }
  }
}
```

**Add to**: `core/src/main/scala/cli/CommonCLI.scala`

```scala
def backendsCommand: Opts[Unit] = Opts.subcommand("backends", "List available conversion backends") {
  (
    Opts.flag("verbose", "Show detailed bridge information").orFalse
  ).map { verbose =>
    BackendsCommand.execute(verbose)
  }
}

// Update main parser to include backends command
def parser: Opts[Config] = {
  convertCommand orElse configCommand orElse backendsCommand
}
```

### Step 4: Enhanced Logging

**Update**: `core/src/main/scala/Pipeline.scala`

Add detailed logging at key decision points:

```scala
import com.tjclp.xlcr.config.ApplicationContext

def convert(
  content: FileContent[MimeType],
  outputType: MimeType,
  config: ConversionConfig
): Try[FileContent[MimeType]] = {

  logger.info(s"Converting ${content.mimeType} → $outputType " +
    s"(requested backend: ${config.backend.getOrElse("auto")})")

  bridgeRegistry.findBridge(content.mimeType, outputType, config.backend) match {
    case Some(bridge) =>
      logger.info(s"Using bridge: ${bridge.getClass.getSimpleName} " +
        s"(backend=${bridge.backend}, priority=${bridge.priority})")

      val startTime = System.nanoTime()

      val result = Try {
        bridge.convert(content, config)
      }.flatten

      val duration = (System.nanoTime() - startTime) / 1_000_000 // ms

      result match {
        case Success(output) =>
          logger.info(s"Conversion successful (${duration}ms, output size: ${output.data.length} bytes)")
          Success(output)

        case Failure(ex) =>
          logger.error(s"Conversion failed with ${bridge.backend} backend after ${duration}ms", ex)
          Failure(ex)
      }

    case None =>
      logger.warn(s"No bridge found for ${content.mimeType} → $outputType " +
        s"(backend=${config.backend.getOrElse("any")})")

      // Log available alternatives
      val availableBridges = bridgeRegistry.listAllBridges()
        .filter(b => b.inputType == content.mimeType.toString)

      if (availableBridges.nonEmpty) {
        logger.info(s"Available conversions from ${content.mimeType}: " +
          availableBridges.map(_.outputType).mkString(", "))
      }

      Failure(BridgeNotFoundException(
        s"No bridge found for ${content.mimeType} → $outputType",
        content.mimeType,
        outputType,
        context = Map("availableBackends" -> availableBridges.map(_.backend).distinct.mkString(", "))
      ))
  }
}
```

### Step 5: Backend Usage Metrics

**New File**: `core/src/main/scala/metrics/BackendMetrics.scala`

```scala
package com.tjclp.xlcr.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicLong, LongAdder}
import scala.jdk.CollectionConverters._

/**
 * Track backend usage for metrics and monitoring.
 * Thread-safe, low-overhead counters.
 */
object BackendMetrics {

  private val conversionCounts = new ConcurrentHashMap[String, LongAdder]()
  private val conversionDurations = new ConcurrentHashMap[String, LongAdder]()
  private val failureCounts = new ConcurrentHashMap[String, LongAdder]()

  /**
   * Record a successful conversion.
   */
  def recordConversion(backend: String, durationMs: Long): Unit = {
    getCounter(conversionCounts, backend).add(1)
    getCounter(conversionDurations, backend).add(durationMs)
  }

  /**
   * Record a failed conversion.
   */
  def recordFailure(backend: String): Unit = {
    getCounter(failureCounts, backend).add(1)
  }

  /**
   * Get conversion statistics.
   */
  def getStats: Map[String, BackendStats] = {
    val backends = (
      conversionCounts.keySet().asScala ++
      failureCounts.keySet().asScala
    ).toSet

    backends.map { backend =>
      val count = getCounter(conversionCounts, backend).sum()
      val duration = getCounter(conversionDurations, backend).sum()
      val failures = getCounter(failureCounts, backend).sum()

      backend -> BackendStats(
        totalConversions = count,
        totalDurationMs = duration,
        averageDurationMs = if (count > 0) duration / count else 0,
        failures = failures,
        successRate = if (count + failures > 0) {
          count.toDouble / (count + failures)
        } else 0.0
      )
    }.toMap
  }

  /**
   * Reset all metrics (for testing).
   */
  def reset(): Unit = {
    conversionCounts.clear()
    conversionDurations.clear()
    failureCounts.clear()
  }

  /**
   * Print metrics summary.
   */
  def printSummary(): Unit = {
    val stats = getStats

    if (stats.isEmpty) {
      println("No conversion metrics recorded")
      return
    }

    println("\nBackend Usage Statistics:")
    println("=" * 80)
    println(f"${"Backend"}%-15s ${"Conversions"}%12s ${"Avg Time"}%10s ${"Failures"}%10s ${"Success Rate"}%12s")
    println("-" * 80)

    stats.toList.sortBy(-_._2.totalConversions).foreach { case (backend, stats) =>
      println(f"$backend%-15s ${stats.totalConversions}%12d ${stats.averageDurationMs}%9dms " +
        f"${stats.failures}%10d ${stats.successRate * 100}%11.1f%%")
    }

    println("-" * 80)
    val totals = stats.values.foldLeft(BackendStats(0, 0, 0, 0, 0.0)) { (acc, s) =>
      BackendStats(
        acc.totalConversions + s.totalConversions,
        acc.totalDurationMs + s.totalDurationMs,
        0, // Will calculate average separately
        acc.failures + s.failures,
        0.0 // Will calculate success rate separately
      )
    }

    val avgDuration = if (totals.totalConversions > 0) {
      totals.totalDurationMs / totals.totalConversions
    } else 0

    val successRate = if (totals.totalConversions + totals.failures > 0) {
      totals.totalConversions.toDouble / (totals.totalConversions + totals.failures)
    } else 0.0

    println(f"${"TOTAL"}%-15s ${totals.totalConversions}%12d ${avgDuration}%9dms " +
      f"${totals.failures}%10d ${successRate * 100}%11.1f%%")
  }

  private def getCounter(map: ConcurrentHashMap[String, LongAdder], key: String): LongAdder = {
    map.computeIfAbsent(key, _ => new LongAdder())
  }

  case class BackendStats(
    totalConversions: Long,
    totalDurationMs: Long,
    averageDurationMs: Long,
    failures: Long,
    successRate: Double
  )
}
```

**Integrate into Pipeline**:

```scala
// In Pipeline.convert method, after successful conversion:
BackendMetrics.recordConversion(bridge.backend, duration)

// After failure:
BackendMetrics.recordFailure(bridge.backend)
```

### Step 6: Availability Checks on Startup

**Update**: `core/src/main/scala/Main.scala`

```scala
object Main extends App {
  // ... existing config initialization

  // Check backend availability on startup
  logger.info("Checking backend availability...")

  val allBridges = BridgeRegistry.listAllBridges()
  val byBackend = allBridges.groupBy(_.backend)

  byBackend.foreach { case (backend, bridges) =>
    val available = bridges.exists(_.available)
    val status = if (available) "✓" else "✗"

    logger.info(s"  [$status] $backend: ${bridges.size} bridges, " +
      s"${bridges.count(_.available)} available")

    if (!available) {
      logger.warn(s"  Backend '$backend' is unavailable - conversions using this backend will fail")
    }
  }

  // ... rest of Main
}
```

## Success Criteria

1. ✅ `backend` field added to Bridge interface
2. ✅ All bridges explicitly declare their backend
3. ✅ `isAvailable()` method on bridges and LibreOfficeConfig
4. ✅ String-based backend detection removed
5. ✅ `xlcr backends` CLI command implemented
6. ✅ Enhanced logging shows backend selection decisions
7. ✅ Backend metrics tracking implemented
8. ✅ Startup availability checks log backend status
9. ✅ `--verbose` flag shows backend details
10. ✅ Documentation updated

## Testing Strategy

### Unit Tests

**File**: `core/src/test/scala/bridges/BridgeMetadataSpec.scala`

```scala
class BridgeMetadataSpec extends AnyFlatSpec with Matchers {

  "Bridge" should "explicitly declare backend" in {
    val bridges = BridgeRegistry.listAllBridges()

    bridges should not be empty
    bridges.foreach { bridge =>
      bridge.backend should not be empty
      bridge.backend should fullyMatch regex "[a-z]+"
    }
  }

  "Aspose bridges" should "declare 'aspose' backend" in {
    val asposeBridges = BridgeRegistry.listAllBridges()
      .filter(_.name.toLowerCase.contains("aspose"))

    asposeBridges.foreach { bridge =>
      bridge.backend shouldBe "aspose"
      bridge.priority shouldBe Priority.HIGH
    }
  }

  "LibreOffice bridges" should "declare 'libreoffice' backend" in {
    val libreBridges = BridgeRegistry.listAllBridges()
      .filter(_.name.toLowerCase.contains("libreoffice"))

    libreBridges.foreach { bridge =>
      bridge.backend shouldBe "libreoffice"
      bridge.priority shouldBe Priority.DEFAULT
    }
  }
}
```

### Integration Tests

**File**: `core/src/it/scala/cli/BackendsCommandSpec.scala`

```scala
class BackendsCommandSpec extends AnyFlatSpec with Matchers {

  "backends command" should "list all available backends" in {
    // Capture stdout
    val output = new ByteArrayOutputStream()
    Console.withOut(output) {
      BackendsCommand.execute(verbose = false)
    }

    val result = output.toString

    result should include("XLCR Available Backends")
    result should include("aspose")
    result should include("Summary:")
  }

  it should "show detailed bridge info in verbose mode" in {
    val output = new ByteArrayOutputStream()
    Console.withOut(output) {
      BackendsCommand.execute(verbose = true)
    }

    val result = output.toString

    result should include("→") // Should show conversions
    result should (include("PDF") or include("PPTX"))
  }
}
```

## Rollout Plan

**Day 1**: Backend Declaration
- Add `backend` field to Bridge interface
- Update all bridge implementations to declare backend
- Remove string-based detection
- Write unit tests

**Day 2**: Availability Checks
- Add `isAvailable()` to bridges
- Implement `LibreOfficeConfig.isAvailable()`
- Add startup availability logging
- Test availability logic

**Day 3**: Backend Discovery
- Implement `BackendsCommand`
- Add `xlcr backends` CLI command
- Test with various backend configurations

**Day 4**: Metrics and Polish
- Implement `BackendMetrics`
- Integrate metrics into Pipeline
- Enhanced logging
- Documentation
- Code review

## Dependencies

- **03-configuration-management.md**: ApplicationContext used for configuration
- **02-error-handling.md**: BridgeNotFoundException used in error cases

## Risks and Mitigation

| Risk | Mitigation |
|------|------------|
| Breaking changes to Bridge interface | Add default implementations, gradual rollout |
| Availability checks slow startup | Make checks lazy, cache results |
| Metrics overhead | Use lock-free counters (LongAdder) |
| Too much logging | Use appropriate log levels (DEBUG for details) |

## Future Enhancements

1. **Health Check Endpoint**: HTTP endpoint for service health (includes backend status)
2. **Backend Switching**: Automatic fallback if primary backend unavailable
3. **Metrics Export**: Export to Prometheus, StatsD, etc.
4. **Admin UI**: Web interface showing backend status and metrics
5. **Dynamic Backend Loading**: Hot-swap backends without restart
