# Error Handling Standardization

**Priority**: High (Critical Pain Point)
**Effort**: 2-3 days
**Phase**: Foundation & Quick Wins

## Current State

### The Problem

Error handling is **inconsistent** across the codebase, making debugging difficult for users and developers:

1. **Mixed Patterns** in `core/src/main/scala/Pipeline.scala`:
   - Lines 44-48: Throws `InputFileNotFoundException` directly
   - Lines 83-92: Logs error, then wraps in `ContentExtractionException` and throws
   - Inconsistent use of `Try` vs explicit throws

2. **Error Messages Lack Context**:
   - Generic messages like "Error extracting content"
   - Missing: input file path, bridge used, conversion parameters
   - Hard to diagnose failures in production

3. **No Error Categorization**:
   - Can't distinguish between:
     - **Retryable errors** (temporary network issues, resource contention)
     - **Fatal errors** (corrupted input, missing dependencies)
     - **User errors** (invalid parameters, unsupported formats)

4. **Exception Hierarchy Exists But Underutilized**:
   - `SplitException` (lines 1-183) has excellent structure with context maps
   - Not consistently used across core pipeline
   - Bridge implementations don't leverage it

### Impact on Production Service

For a production service deployment, poor error handling causes:
- ❌ Difficult troubleshooting (unclear which conversion failed)
- ❌ No automatic retry logic (can't identify retryable errors)
- ❌ Poor user experience (generic error messages)
- ❌ Monitoring challenges (can't alert on specific error types)

## Proposed Solution

**Standardize error handling with**:
1. Consistent exception hierarchy with context
2. Error categorization for production decisions
3. Rich error messages with conversion metadata
4. Clear documentation of error handling strategy

### Design Principles

1. **Use Try for Expected Failures**: Invalid input, unsupported formats
2. **Throw for Unexpected Failures**: Programming errors, configuration issues
3. **Always Include Context**: File paths, bridge info, configuration
4. **Categorize All Errors**: RETRYABLE, FATAL, USER_ERROR
5. **Log Once**: Either catch and log, or throw (not both)

## Implementation Details

### Step 1: Define Error Categories

**New File**: `core/src/main/scala/errors/ErrorCategory.scala`

```scala
package com.tjclp.xlcr.errors

/**
 * Categories of errors for production handling decisions.
 */
sealed trait ErrorCategory {
  def name: String
  def isRetryable: Boolean
}

object ErrorCategory {

  /** Temporary error that may succeed on retry (network, resource contention) */
  case object Retryable extends ErrorCategory {
    val name = "RETRYABLE"
    val isRetryable = true
  }

  /** Permanent error that won't succeed on retry (corrupted file, missing dependency) */
  case object Fatal extends ErrorCategory {
    val name = "FATAL"
    val isRetryable = false
  }

  /** User input error (invalid parameters, unsupported format) */
  case object UserError extends ErrorCategory {
    val name = "USER_ERROR"
    val isRetryable = false
  }

  /** Configuration error (missing license, invalid settings) */
  case object ConfigError extends ErrorCategory {
    val name = "CONFIG_ERROR"
    val isRetryable = false
  }
}
```

### Step 2: Enhance Exception Hierarchy

**New File**: `core/src/main/scala/errors/ConversionException.scala`

```scala
package com.tjclp.xlcr.errors

import com.tjclp.xlcr.mime.MimeType

/**
 * Base exception for all conversion errors.
 * Includes rich context and error categorization.
 */
abstract class ConversionException(
  message: String,
  cause: Throwable = null,
  val context: Map[String, Any] = Map.empty,
  val category: ErrorCategory
) extends RuntimeException(message, cause) {

  def withContext(key: String, value: Any): ConversionException

  def userMessage: String = {
    val ctx = contextString
    if (ctx.nonEmpty) s"$message\n$ctx" else message
  }

  private def contextString: String = {
    if (context.isEmpty) ""
    else context.map { case (k, v) => s"  $k: $v" }.mkString("\n")
  }
}

/** Input file not found or not readable */
case class InputFileNotFoundException(
  message: String,
  path: String,
  cause: Throwable = null,
  context: Map[String, Any] = Map.empty
) extends ConversionException(
  message,
  cause,
  context + ("inputPath" -> path),
  ErrorCategory.UserError
) {
  override def withContext(key: String, value: Any): InputFileNotFoundException =
    copy(context = context + (key -> value))
}

/** Content extraction/parsing failed */
case class ContentExtractionException(
  message: String,
  inputType: MimeType,
  cause: Throwable = null,
  context: Map[String, Any] = Map.empty
) extends ConversionException(
  message,
  cause,
  context + ("inputType" -> inputType.toString),
  if (cause != null && cause.getMessage.contains("corrupted")) ErrorCategory.Fatal
  else ErrorCategory.Retryable
) {
  override def withContext(key: String, value: Any): ContentExtractionException =
    copy(context = context + (key -> value))
}

/** Bridge not found for requested conversion */
case class BridgeNotFoundException(
  message: String,
  inputType: MimeType,
  outputType: MimeType,
  context: Map[String, Any] = Map.empty
) extends ConversionException(
  message,
  null,
  context + ("inputType" -> inputType.toString, "outputType" -> outputType.toString),
  ErrorCategory.UserError
) {
  override def withContext(key: String, value: Any): BridgeNotFoundException =
    copy(context = context + (key -> value))
}

/** Bridge conversion failed */
case class BridgeConversionException(
  message: String,
  bridgeName: String,
  inputType: MimeType,
  outputType: MimeType,
  cause: Throwable = null,
  context: Map[String, Any] = Map.empty
) extends ConversionException(
  message,
  cause,
  context + (
    "bridge" -> bridgeName,
    "inputType" -> inputType.toString,
    "outputType" -> outputType.toString
  ),
  determineCategory(cause)
) {
  override def withContext(key: String, value: Any): BridgeConversionException =
    copy(context = context + (key -> value))
}

object BridgeConversionException {
  private def determineCategory(cause: Throwable): ErrorCategory = {
    if (cause == null) return ErrorCategory.Fatal

    val msg = cause.getMessage.toLowerCase
    if (msg.contains("license") || msg.contains("not licensed")) ErrorCategory.ConfigError
    else if (msg.contains("timeout") || msg.contains("connection")) ErrorCategory.Retryable
    else if (msg.contains("corrupted") || msg.contains("invalid format")) ErrorCategory.Fatal
    else ErrorCategory.Fatal
  }
}

/** Output writing failed */
case class OutputWriteException(
  message: String,
  path: String,
  cause: Throwable = null,
  context: Map[String, Any] = Map.empty
) extends ConversionException(
  message,
  cause,
  context + ("outputPath" -> path),
  if (cause != null && cause.getMessage.contains("space")) ErrorCategory.Fatal
  else ErrorCategory.Retryable
) {
  override def withContext(key: String, value: Any): OutputWriteException =
    copy(context = context + (key -> value))
}
```

### Step 3: Standardize Pipeline Error Handling

**File**: `core/src/main/scala/Pipeline.scala`

**Current problematic pattern** (lines 83-92):
```scala
case Failure(exception) =>
  logger.error(s"Error extracting content: ${exception.getMessage}", exception)
  throw ContentExtractionException(
    s"Error extracting content: ${exception.getMessage}",
    exception
  )
```

**New standardized pattern**:
```scala
import com.tjclp.xlcr.errors._

// In extractContent method
def extractContent(
  inputPath: String,
  mimeType: MimeType,
  config: ConversionConfig
): Try[FileContent[MimeType]] = {

  parserRegistry.getParser(mimeType) match {
    case Some(parser) =>
      Try {
        parser.parse(inputPath, config)
      }.recoverWith {
        case ex: ConversionException =>
          // Already a ConversionException, just add context
          Failure(ex.withContext("inputPath", inputPath)
                   .withContext("mimeType", mimeType.toString))

        case ex: Throwable =>
          // Wrap unexpected exceptions
          Failure(ContentExtractionException(
            s"Failed to parse input file",
            inputType = mimeType,
            cause = ex,
            context = Map(
              "inputPath" -> inputPath,
              "parser" -> parser.getClass.getSimpleName
            )
          ))
      }

    case None =>
      Failure(new UnsupportedOperationException(
        s"No parser available for MIME type: ${mimeType}"
      ))
  }
}

// In convert method
def convert(
  content: FileContent[MimeType],
  outputType: MimeType,
  config: ConversionConfig
): Try[FileContent[MimeType]] = {

  bridgeRegistry.findBridge(content.mimeType, outputType, config.backend) match {
    case Some(bridge) =>
      Try {
        bridge.convert(content, config)
      }.recoverWith {
        case ex: ConversionException =>
          Failure(ex.withContext("bridge", bridge.getClass.getSimpleName))

        case ex: Throwable =>
          Failure(BridgeConversionException(
            s"Bridge conversion failed",
            bridgeName = bridge.getClass.getSimpleName,
            inputType = content.mimeType,
            outputType = outputType,
            cause = ex,
            context = Map("bridgePriority" -> bridge.priority.toString)
          ))
      }

    case None =>
      Failure(BridgeNotFoundException(
        s"No bridge found for ${content.mimeType} → $outputType",
        inputType = content.mimeType,
        outputType = outputType,
        context = Map("requestedBackend" -> config.backend.getOrElse("auto"))
      ))
  }
}
```

### Step 4: Update Bridge Implementations

**Pattern for Bridge implementations** (apply to all bridges):

```scala
// In core-aspose bridges
override def convert(
  input: FileContent[I],
  config: Option[BridgeConfig] = None
): Try[FileContent[O]] = {

  AsposeLicense.initializeIfNeeded()

  Try {
    // Conversion logic here
    performConversion(input, config)
  }.recoverWith {
    case ex: ConversionException =>
      // Already categorized, just pass through with additional context
      Failure(ex.withContext("backend", "aspose")
               .withContext("bridgeClass", this.getClass.getSimpleName))

    case ex if ex.getMessage.contains("license") =>
      // Aspose license error - this is a config error
      Failure(BridgeConversionException(
        "Aspose license error",
        bridgeName = this.getClass.getSimpleName,
        inputType = inputMimeType,
        outputType = outputMimeType,
        cause = ex,
        context = Map("backend" -> "aspose")
      ).withContext("category", ErrorCategory.ConfigError))

    case ex: Throwable =>
      // Unexpected error
      Failure(BridgeConversionException(
        s"Unexpected error in ${this.getClass.getSimpleName}",
        bridgeName = this.getClass.getSimpleName,
        inputType = inputMimeType,
        outputType = outputMimeType,
        cause = ex,
        context = Map("backend" -> "aspose")
      ))
  }
}
```

### Step 5: CLI Error Display

**File**: `core/src/main/scala/cli/AbstractMain.scala`

Add rich error display for users:

```scala
protected def handleError(ex: Throwable): Int = {
  ex match {
    case ce: ConversionException =>
      System.err.println(s"Error [${ce.category.name}]: ${ce.userMessage}")

      // Show suggestion based on category
      ce.category match {
        case ErrorCategory.UserError =>
          System.err.println("\nPlease check your input file and parameters.")
          System.err.println("Use --help for usage information.")

        case ErrorCategory.ConfigError =>
          System.err.println("\nPlease check your configuration and licenses.")
          System.err.println("Run 'xlcr backends' to see available converters.")

        case ErrorCategory.Retryable =>
          System.err.println("\nThis error may be temporary. Please try again.")

        case ErrorCategory.Fatal =>
          System.err.println("\nThis error cannot be recovered. Please check the input file.")
      }

      if (verbose) {
        System.err.println("\nStack trace:")
        ce.printStackTrace(System.err)
      }

      1 // Exit code

    case ex: Throwable =>
      System.err.println(s"Unexpected error: ${ex.getMessage}")
      if (verbose) ex.printStackTrace(System.err)
      2 // Different exit code for unexpected errors
  }
}
```

### Step 6: Documentation

**New File**: `docs/ERROR_HANDLING.md`

```markdown
# Error Handling Strategy

## Principles

1. **Use Try for Expected Failures**: File not found, unsupported formats, validation errors
2. **Throw for Programming Errors**: Null pointer, illegal state, assertion failures
3. **Always Include Context**: File paths, bridge names, configuration
4. **Log Once**: Either catch and log, OR throw (never both)
5. **Categorize Errors**: Every ConversionException has an ErrorCategory

## Error Categories

| Category | Retryable? | Use Case | HTTP Code |
|----------|------------|----------|-----------|
| USER_ERROR | No | Invalid input, unsupported format | 400 |
| CONFIG_ERROR | No | Missing license, invalid settings | 500 |
| RETRYABLE | Yes | Network timeout, resource contention | 503 |
| FATAL | No | Corrupted file, missing dependency | 500 |

## Exception Hierarchy

```
ConversionException (abstract base)
├── InputFileNotFoundException (USER_ERROR)
├── ContentExtractionException (RETRYABLE or FATAL)
├── BridgeNotFoundException (USER_ERROR)
├── BridgeConversionException (determined by cause)
└── OutputWriteException (RETRYABLE or FATAL)
```

## For Bridge Implementers

When implementing a bridge:

```scala
override def convert(input: FileContent[I], config: Option[BridgeConfig]): Try[FileContent[O]] = {
  Try {
    // Your conversion logic
  }.recoverWith {
    case ex: ConversionException =>
      Failure(ex.withContext("bridge", this.getClass.getSimpleName))
    case ex: Throwable =>
      Failure(BridgeConversionException(...))
  }
}
```

## For Service Integration

```scala
def handleConversion(request: ConversionRequest): ConversionResponse = {
  pipeline.process(request.input, request.output) match {
    case Success(result) =>
      ConversionResponse.success(result)

    case Failure(ex: ConversionException) =>
      val statusCode = ex.category match {
        case ErrorCategory.UserError => 400
        case ErrorCategory.ConfigError => 500
        case ErrorCategory.Retryable => 503
        case ErrorCategory.Fatal => 500
      }

      ConversionResponse.error(
        statusCode = statusCode,
        message = ex.userMessage,
        category = ex.category.name,
        retryable = ex.category.isRetryable,
        context = ex.context
      )

    case Failure(ex) =>
      ConversionResponse.error(500, "Internal error", context = Map("error" -> ex.getMessage))
  }
}
```
```

## Success Criteria

1. ✅ `ErrorCategory` enum defined with 4 categories
2. ✅ `ConversionException` base class with context and categorization
3. ✅ 5+ specific exception types (Input, Content, Bridge, Output, etc.)
4. ✅ `Pipeline.scala` uses consistent Try pattern with proper recovery
5. ✅ All bridge implementations wrap errors consistently
6. ✅ CLI displays rich error messages with suggestions
7. ✅ `docs/ERROR_HANDLING.md` documentation created
8. ✅ All existing tests still pass
9. ✅ New tests for error scenarios

## Testing Strategy

### Unit Tests

**File**: `core/src/test/scala/errors/ConversionExceptionSpec.scala`

```scala
class ConversionExceptionSpec extends AnyFlatSpec with Matchers {

  "ConversionException" should "include context in user message" in {
    val ex = BridgeNotFoundException(
      "Bridge not found",
      ApplicationPdf,
      ApplicationVndOpenXmlFormats,
      Map("requestedBackend" -> "aspose")
    )

    ex.userMessage should include("Bridge not found")
    ex.userMessage should include("requestedBackend: aspose")
    ex.category shouldBe ErrorCategory.UserError
  }

  "withContext" should "add context without mutation" in {
    val ex1 = InputFileNotFoundException("File not found", "/tmp/test.pdf")
    val ex2 = ex1.withContext("size", "1024")

    ex1.context should not contain key("size")
    ex2.context should contain key("size")
  }

  "BridgeConversionException" should "determine category from cause" in {
    val licenseEx = new RuntimeException("Not licensed")
    val ex = BridgeConversionException(
      "Conversion failed",
      "TestBridge",
      ApplicationPdf,
      ApplicationVndOpenXmlFormats,
      cause = licenseEx
    )

    ex.category shouldBe ErrorCategory.ConfigError
  }
}
```

### Integration Tests

**File**: `core/src/test/scala/PipelineErrorHandlingSpec.scala`

```scala
class PipelineErrorHandlingSpec extends AnyFlatSpec with Matchers {

  "Pipeline" should "wrap parser exceptions in ContentExtractionException" in {
    val result = pipeline.process("nonexistent.pdf", "output.html")

    result shouldBe a[Failure[_]]
    result.failed.get shouldBe a[InputFileNotFoundException]

    val ex = result.failed.get.asInstanceOf[InputFileNotFoundException]
    ex.context should contain key("inputPath")
    ex.category shouldBe ErrorCategory.UserError
  }

  it should "categorize license errors correctly" in {
    // Test with unlicensed Aspose
    val result = pipeline.process("test.pdf", "output.pptx", backend = Some("aspose"))

    result shouldBe a[Failure[_]]
    val ex = result.failed.get.asInstanceOf[ConversionException]
    ex.category shouldBe ErrorCategory.ConfigError
  }
}
```

## Rollout Plan

**Day 1**: Foundation
- Create `ErrorCategory.scala`
- Create `ConversionException.scala` with all exception types
- Write unit tests for exceptions

**Day 2**: Pipeline Integration
- Update `Pipeline.scala` error handling
- Update `DirectoryPipeline.scala` error handling
- Test pipeline with various error scenarios

**Day 3**: Bridge Updates
- Update all Aspose bridges
- Update core bridges
- Test each bridge's error handling

**Day 4**: Polish
- CLI error display improvements
- Create `ERROR_HANDLING.md` documentation
- Integration tests
- Review and cleanup

## Dependencies

- None (independent improvement)

## Risks and Mitigation

| Risk | Mitigation |
|------|------------|
| Breaking existing error handling | Gradual rollout, extensive testing |
| Performance overhead of context | Context is only created on error path (not hot path) |
| Incomplete categorization | Start with broad categories, refine based on production data |

## Future Enhancements

1. **Error Codes**: Add numeric error codes for easier searching/documentation
2. **Localization**: Support multiple languages for user messages
3. **Error Reporting**: Automatic error reporting to monitoring system
4. **Recovery Strategies**: Automatic fallback to different backends on certain errors
