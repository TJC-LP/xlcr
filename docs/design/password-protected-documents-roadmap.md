# Password-Protected Documents: Implementation Roadmap

## Executive Summary

This document outlines the comprehensive implementation plan for adding password support to XLCR's document conversion system. The goal is to enable conversion of password-protected documents (PDF, Excel, Word, PowerPoint) through a unified, secure `--password` flag interface.

**Current Status**: PDF encryption detection and removal implemented for user-level restrictions (copy:no, change:no). Password-protected PDFs requiring user password are NOT yet supported.

**Target**: Full password support across all document formats with three secure password input methods.

## Motivation

Real-world documents are frequently password-protected for security:
- **Financial documents**: Excel workbooks with sensitive data
- **Legal documents**: PDFs with confidential information
- **Presentations**: PowerPoint decks with proprietary content
- **Contracts**: Word documents with restricted access

The Project Blue Sky CIM conversion demonstrated that XLCR can handle **restriction-based encryption** (AES-256 with copy/edit restrictions) but failed to handle **password-protected** documents where a user password is required to open the file.

## Design Principles

1. **Security First**: Multiple secure password input methods, avoid exposing passwords in process lists
2. **Format Agnostic**: Consistent password handling across all document types
3. **Backward Compatibility**: No impact on existing conversions without passwords
4. **User Experience**: Clear error messages when passwords are wrong or missing
5. **Transparency**: Log password usage (but never log actual passwords)
6. **Fail Gracefully**: Helpful errors for common password issues

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      User Input Layer                        │
│  --password "pwd" | --password-env VAR | --password-file f   │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                 Password Resolution Logic                    │
│         (Main.scala - resolvePassword method)                │
│   • Validates only one method specified                      │
│   • Resolves env var or reads file                          │
│   • Warns about security implications                        │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    BridgeContext                             │
│        Thread-local storage: password: Option[String]        │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  Bridge Implementations                       │
│  • PdfToPowerPointBridge: PdfDocument(stream, password)      │
│  • ExcelToPdfBridge: LoadOptions.setPassword(password)       │
│  • WordToPdfBridge: LoadOptions.setPassword(password)        │
│  • PowerPointBridges: LoadOptions.setPassword(password)      │
└─────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Configuration Data Model

```scala
// core-aspose/src/main/scala/utils/aspose/AsposeConfig.scala

case class AsposeConfig(
  input: String = "",
  output: String = "",
  // ... existing fields ...

  // Password support (mutually exclusive)
  password: Option[String] = None,           // Direct password
  passwordEnv: Option[String] = None,        // Environment variable name
  passwordFile: Option[String] = None,       // File path containing password

  // Existing fields
  stripMasters: Boolean = false,
  htmlExternal: Boolean = false,
  // ... other fields ...
)
```

**Design rationale**: Three separate fields for different password input methods provides clarity and enables validation that only one method is used at a time.

### 2. Bridge Context Extension

```scala
// core-aspose/src/main/scala/utils/aspose/BridgeContext.scala

/**
 * Data stored in the bridge context
 *
 * @param stripMasters
 *   Whether to remove unused master slides/templates during conversions
 * @param externalResources
 *   Whether to externalize resources (images, fonts) during HTML export
 * @param outputPath
 *   Output file path for resource generation
 * @param password
 *   Password for opening encrypted/protected documents
 */
case class BridgeContextData(
  stripMasters: Boolean = false,
  externalResources: Boolean = false,
  outputPath: Option[String] = None,
  password: Option[String] = None  // NEW: Password support
)
```

**Security considerations**:
- Passwords stored in memory only during conversion
- Thread-local storage prevents cross-thread leakage
- No password logging or echoing in error messages

### 3. CLI Interface

```scala
// core-aspose/src/main/scala/Main.scala

// Add to buildAllOptions:

opt[String]("password")
  .valueName("<password>")
  .action((x, c) => c.copy(password = Some(x)))
  .text("Password for encrypted documents (SECURITY WARNING: visible in process list)"),

opt[String]("password-env")
  .valueName("<env-var>")
  .action((x, c) => c.copy(passwordEnv = Some(x)))
  .text("Environment variable name containing password (recommended for security)"),

opt[String]("password-file")
  .valueName("<path>")
  .action((x, c) => c.copy(passwordFile = Some(x)))
  .text("Path to file containing password (recommended for security)")
```

**Usage examples**:
```bash
# Method 1: Direct password (NOT RECOMMENDED - visible in ps/top)
sbt "run -i secret.pdf -o output.pptx --password mypassword"

# Method 2: Environment variable (RECOMMENDED)
export DOCUMENT_PASSWORD="mypassword"
sbt "run -i secret.xlsx -o output.pdf --password-env DOCUMENT_PASSWORD"

# Method 3: Password file (RECOMMENDED for scripts/automation)
echo "mypassword" > ~/.xlcr-password
chmod 600 ~/.xlcr-password
sbt "run -i secret.docx -o output.pdf --password-file ~/.xlcr-password"
```

### 4. Password Resolution Logic

```scala
// core-aspose/src/main/scala/Main.scala

/**
 * Resolves password from CLI arguments using one of three methods:
 * 1. Direct password (--password) - warns about security
 * 2. Environment variable (--password-env) - recommended
 * 3. Password file (--password-file) - recommended
 *
 * @param config CLI configuration
 * @return Resolved password or None if not provided
 * @throws IllegalArgumentException if multiple methods specified
 */
private def resolvePassword(config: AsposeConfig): Option[String] = {
  val methods = Seq(
    config.password.map(_ => "password"),
    config.passwordEnv.map(_ => "password-env"),
    config.passwordFile.map(_ => "password-file")
  ).flatten

  if (methods.size > 1) {
    val methodsList = methods.mkString(", ")
    logger.error(s"Only one password method can be specified, found: $methodsList")
    throw new IllegalArgumentException(
      s"Multiple password methods specified: $methodsList. Use only one of --password, --password-env, or --password-file"
    )
  }

  (config.password, config.passwordEnv, config.passwordFile) match {
    case (Some(pwd), None, None) =>
      logger.warn(
        "Using --password flag exposes password in process list. " +
        "Consider using --password-env or --password-file for better security."
      )
      Some(pwd)

    case (None, Some(envVar), None) =>
      logger.debug(s"Reading password from environment variable: $envVar")
      sys.env.get(envVar) match {
        case Some(pwd) if pwd.nonEmpty => Some(pwd)
        case Some(_) =>
          logger.error(s"Environment variable $envVar is empty")
          None
        case None =>
          logger.error(s"Environment variable $envVar not found")
          None
      }

    case (None, None, Some(path)) =>
      logger.debug(s"Reading password from file: $path")
      try {
        val passwordContent = java.nio.file.Files.readString(
          java.nio.file.Paths.get(path)
        ).trim

        if (passwordContent.isEmpty) {
          logger.error(s"Password file $path is empty")
          None
        } else {
          Some(passwordContent)
        }
      } catch {
        case ex: java.nio.file.NoSuchFileException =>
          logger.error(s"Password file not found: $path")
          None
        case ex: java.io.IOException =>
          logger.error(s"Could not read password file $path: ${ex.getMessage}")
          None
        case ex: Exception =>
          logger.error(s"Error reading password file $path: ${ex.getMessage}")
          None
      }

    case (None, None, None) =>
      // No password specified - this is fine
      None

    case _ =>
      // Should never reach here due to earlier validation
      logger.error("Internal error: invalid password configuration state")
      None
  }
}
```

Update `initialize()` method:
```scala
override protected def initialize(config: AsposeConfig): Unit = {
  applyLicenses(config)

  // Resolve password from CLI arguments
  val resolvedPassword = resolvePassword(config)

  // Set bridge context for this thread
  utils.aspose.BridgeContext.set(
    utils.aspose.BridgeContextData(
      stripMasters = config.stripMasters,
      externalResources = config.htmlExternal,
      outputPath = Some(config.output),
      password = resolvedPassword  // NEW: Pass resolved password
    )
  )
}
```

## Bridge Implementations

### 5. PDF Bridge Enhancement

**File**: `core-aspose/src/main/scala/bridges/powerpoint/PdfToPowerPointAsposeBridgeImpl.scala`

Update `handleEncryptedPdf()` method to support password:

```scala
/**
 * Helper method to handle encrypted or restricted PDFs by creating an unlocked copy.
 * This allows Aspose.Slides to successfully import the PDF content.
 *
 * Handles three types of PDF encryption:
 * 1. User password protection (requires password to open)
 * 2. Owner password protection (document can be opened but has restrictions)
 * 3. Certificate-based encryption (not yet supported)
 *
 * @param pdfData
 *   The original PDF data (possibly encrypted/restricted)
 * @return
 *   Unlocked PDF data ready for import, or original data if no restrictions detected
 */
private def handleEncryptedPdf(pdfData: Array[Byte]): Array[Byte] = {
  val password = BridgeContext.get().password

  try {
    Using.Manager { use =>
      val inputStream = use(new ByteArrayInputStream(pdfData))

      // Try to load PDF with password if provided
      val pdfDocument = try {
        password match {
          case Some(pwd) =>
            logger.debug("Attempting to load PDF with provided password")
            new PdfDocument(inputStream, pwd)
          case None =>
            new PdfDocument(inputStream)
        }
      } catch {
        case ex: Exception if ex.getMessage != null && ex.getMessage.contains("password") =>
          // PDF requires password but none provided or password is wrong
          password match {
            case Some(_) =>
              logger.error("Invalid password provided for PDF")
              throw RendererError(
                "Invalid password for encrypted PDF. Please check the password and try again.",
                Some(ex)
              )
            case None =>
              logger.error("PDF requires password but none provided")
              throw RendererError(
                "PDF is password-protected. Use --password, --password-env, or --password-file to provide the password.",
                Some(ex)
              )
          }
      }

      use(new DisposableWrapper(pdfDocument))

      // Check if PDF is encrypted or has restrictions
      val isEncrypted = pdfDocument.isEncrypted()
      val permissions = pdfDocument.getPermissions

      if (isEncrypted) {
        logger.info(
          s"PDF is encrypted, creating unlocked copy for conversion"
        )

        // Create unlocked copy by re-saving without restrictions
        val outputStream = use(new ByteArrayOutputStream())

        // Decrypt if encrypted
        pdfDocument.decrypt()

        // Save without restrictions
        pdfDocument.save(outputStream)
        val unlockedData = outputStream.toByteArray

        logger.debug(s"Created unlocked PDF copy: ${unlockedData.length} bytes")
        unlockedData
      } else {
        // No encryption, use original data
        logger.debug("PDF has no encryption, using original data")
        pdfData
      }
    }.get
  } catch {
    case ex: RendererError =>
      // Re-throw renderer errors without wrapping
      throw ex
    case ex: Exception =>
      // Wrap other exceptions
      logger.warn(s"Could not process PDF encryption, will attempt direct import: ${ex.getMessage}")
      pdfData
  }
}
```

### 6. Excel Bridge Enhancement

**File**: `core-aspose/src/main/scala/bridges/excel/ExcelToPdfAsposeBridgeImpl.scala`

Update workbook loading (around line 52):

```scala
// Load workbook from bytes
val pdfBytes = Using.Manager { use =>
  val bais = use(new ByteArrayInputStream(model.data))

  // Load workbook with password if provided
  val workbook = try {
    BridgeContext.get().password match {
      case Some(pwd) =>
        logger.debug("Loading Excel workbook with password")
        val loadOptions = new com.aspose.cells.LoadOptions()
        loadOptions.setPassword(pwd)
        new Workbook(bais, loadOptions)
      case None =>
        new Workbook(bais)
    }
  } catch {
    case ex: com.aspose.cells.CellsException
      if ex.getMessage != null && ex.getMessage.toLowerCase.contains("password") =>
      BridgeContext.get().password match {
        case Some(_) =>
          logger.error("Invalid password provided for Excel workbook")
          throw RendererError(
            "Invalid password for encrypted Excel file. Please check the password and try again.",
            Some(ex)
          )
        case None =>
          logger.error("Excel workbook requires password but none provided")
          throw RendererError(
            "Excel file is password-protected. Use --password, --password-env, or --password-file to provide the password.",
            Some(ex)
          )
      }
    case ex: Exception =>
      logger.error(s"Failed to load Excel workbook: ${ex.getMessage}", ex)
      throw RendererError(s"Failed to load Excel workbook: ${ex.getMessage}", Some(ex))
  }

  use(new DisposableWrapper(workbook))

  // ... rest of existing logic ...
}
```

### 7. Word Bridge Enhancement

**File**: `core-aspose/src/main/scala/bridges/word/WordToPdfAsposeBridgeImpl.scala`

Update `convertDocToPdf()` method (around line 73):

```scala
private def convertDocToPdf(
  inputStream: ByteArrayInputStream
): ByteArrayOutputStream = {

  val asposeDoc = try {
    BridgeContext.get().password match {
      case Some(pwd) =>
        logger.debug("Loading Word document with password")
        val loadOptions = new com.aspose.words.LoadOptions()
        loadOptions.setPassword(pwd)
        new Document(inputStream, loadOptions)
      case None =>
        new Document(inputStream)
    }
  } catch {
    case ex: com.aspose.words.IncorrectPasswordException =>
      BridgeContext.get().password match {
        case Some(_) =>
          logger.error("Invalid password provided for Word document")
          throw RendererError(
            "Invalid password for encrypted Word file. Please check the password and try again.",
            Some(ex)
          )
        case None =>
          logger.error("Word document requires password but none provided")
          throw RendererError(
            "Word file is password-protected. Use --password, --password-env, or --password-file to provide the password.",
            Some(ex)
          )
      }
    case ex: Exception =>
      logger.error(s"Failed to load Word document: ${ex.getMessage}", ex)
      throw RendererError(s"Failed to load Word document: ${ex.getMessage}", Some(ex))
  }

  Using.resource(new CleanupWrapper(asposeDoc)) { wrapper =>
    val pdfOutput = new ByteArrayOutputStream()
    wrapper.resource.save(pdfOutput, SaveFormat.PDF)
    pdfOutput
  }
}
```

### 8. PowerPoint Bridge Enhancement

**File**: `core-aspose/src/main/scala/bridges/powerpoint/PowerPointToPdfAsposeBridgeImpl.scala`

Update presentation loading (around line 54):

```scala
val pdfBytes = Using.Manager { use =>
  val inputStream = use(new ByteArrayInputStream(model.data))

  val presentation = try {
    BridgeContext.get().password match {
      case Some(pwd) =>
        logger.debug("Loading PowerPoint presentation with password")
        val loadOptions = new com.aspose.slides.LoadOptions()
        loadOptions.setPassword(pwd)
        new Presentation(inputStream, loadOptions)
      case None =>
        new Presentation(inputStream)
    }
  } catch {
    case ex: com.aspose.slides.PptxReadException
      if ex.getMessage != null && ex.getMessage.toLowerCase.contains("password") =>
      BridgeContext.get().password match {
        case Some(_) =>
          logger.error("Invalid password provided for PowerPoint presentation")
          throw RendererError(
            "Invalid password for encrypted PowerPoint file. Please check the password and try again.",
            Some(ex)
          )
        case None =>
          logger.error("PowerPoint presentation requires password but none provided")
          throw RendererError(
            "PowerPoint file is password-protected. Use --password, --password-env, or --password-file to provide the password.",
            Some(ex)
          )
      }
    case ex: Exception =>
      logger.error(s"Failed to load PowerPoint presentation: ${ex.getMessage}", ex)
      throw RendererError(s"Failed to load presentation: ${ex.getMessage}", Some(ex))
  }

  use(new DisposableWrapper(presentation))

  // ... rest of existing logic ...
}
```

**Also update these PowerPoint bridges with similar logic:**
- `PowerPointToHtmlAsposeBridgeImpl.scala` (line ~54)
- `HtmlToPowerPointAsposeBridgeImpl.scala` (if loading existing presentation for merging)

## Testing Strategy

### Test File Creation

Create password-protected test files for each format:

```bash
# PDF (using qpdf or pdftk)
qpdf --encrypt test123 test123 256 -- input.pdf test-password.pdf

# Excel (via Python with openpyxl)
python3 << 'EOF'
from openpyxl import Workbook
from openpyxl.writer.excel import save_workbook
wb = Workbook()
ws = wb.active
ws['A1'] = 'Test Data'
wb.security.set_workbook_password('test123', already_hashed=False)
wb.save('test-password.xlsx')
EOF

# Word (via Python with python-docx - requires manual setup)
# Or create via Microsoft Word with "File > Info > Protect Document > Encrypt with Password"

# PowerPoint (similar - use Microsoft PowerPoint UI)
# Or via python-pptx with password protection
```

**Standard Test Password**: `test123`

### Unit Test Suite

**File**: `core-aspose/src/test/scala/bridges/PasswordProtectedDocumentsSpec.scala`

```scala
package com.tjclp.xlcr.bridges

import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import base.BridgeSpec
import models.FileContent
import types.MimeType._
import utils.aspose.{AsposeLicense, BridgeContext, BridgeContextData}
import bridges.powerpoint.PdfToPptxAsposeBridge
import bridges.excel.ExcelXlsxToPdfAsposeBridge
import bridges.word.WordDocxToPdfAsposeBridge
import bridges.powerpoint.PowerPointPptxToPdfAsposeBridge

class PasswordProtectedDocumentsSpec extends BridgeSpec with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    super.beforeAll()
    AsposeLicense.initializeIfNeeded()
  }

  override def afterEach(): Unit = {
    // Clear bridge context after each test
    BridgeContext.clear()
  }

  "Password-protected PDF conversion" should {
    "successfully convert with correct password" in {
      // Load test PDF encrypted with password "test123"
      val pdfBytes = loadTestFile("test-password.pdf")
      val input = FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)

      // Set password in bridge context
      BridgeContext.set(BridgeContextData(password = Some("test123")))

      // Convert to PPTX
      val result = PdfToPptxAsposeBridge.convert(input)

      result.mimeType shouldBe ApplicationVndOpenXmlFormatsPresentationmlPresentation
      result.data.length should be > 1000
    }

    "fail with wrong password" in {
      val pdfBytes = loadTestFile("test-password.pdf")
      val input = FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)

      BridgeContext.set(BridgeContextData(password = Some("wrongpassword")))

      val exception = intercept[RendererError] {
        PdfToPptxAsposeBridge.convert(input)
      }
      exception.getMessage should include("Invalid password")
    }

    "fail when password required but not provided" in {
      val pdfBytes = loadTestFile("test-password.pdf")
      val input = FileContent[ApplicationPdf.type](pdfBytes, ApplicationPdf)

      // No password set in context

      val exception = intercept[RendererError] {
        PdfToPptxAsposeBridge.convert(input)
      }
      exception.getMessage should include("password-protected")
      exception.getMessage should include("--password")
    }
  }

  "Password-protected Excel conversion" should {
    "successfully convert with correct password" in {
      val xlsxBytes = loadTestFile("test-password.xlsx")
      val input = FileContent(xlsxBytes, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

      BridgeContext.set(BridgeContextData(password = Some("test123")))

      val result = ExcelXlsxToPdfAsposeBridge.convert(input)

      result.mimeType shouldBe ApplicationPdf
      result.data.length should be > 1000
    }

    "fail with wrong password" in {
      val xlsxBytes = loadTestFile("test-password.xlsx")
      val input = FileContent(xlsxBytes, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)

      BridgeContext.set(BridgeContextData(password = Some("wrongpassword")))

      val exception = intercept[RendererError] {
        ExcelXlsxToPdfAsposeBridge.convert(input)
      }
      exception.getMessage should include("Invalid password")
    }
  }

  "Password-protected Word conversion" should {
    "successfully convert with correct password" in {
      val docxBytes = loadTestFile("test-password.docx")
      val input = FileContent(docxBytes, ApplicationVndOpenXmlFormatsWordprocessingmlDocument)

      BridgeContext.set(BridgeContextData(password = Some("test123")))

      val result = WordDocxToPdfAsposeBridge.convert(input)

      result.mimeType shouldBe ApplicationPdf
      result.data.length should be > 1000
    }
  }

  "Password-protected PowerPoint conversion" should {
    "successfully convert with correct password" in {
      val pptxBytes = loadTestFile("test-password.pptx")
      val input = FileContent(pptxBytes, ApplicationVndOpenXmlFormatsPresentationmlPresentation)

      BridgeContext.set(BridgeContextData(password = Some("test123")))

      val result = PowerPointPptxToPdfAsposeBridge.convert(input)

      result.mimeType shouldBe ApplicationPdf
      result.data.length should be > 1000
    }
  }

  // Helper to load test files
  private def loadTestFile(filename: String): Array[Byte] = {
    val resourcePath = s"/test-data/password-protected/$filename"
    val stream = getClass.getResourceAsStream(resourcePath)
    if (stream == null) {
      fail(s"Test file not found: $resourcePath")
    }
    try {
      stream.readAllBytes()
    } finally {
      stream.close()
    }
  }
}
```

### Integration Tests

```bash
# Test all three password input methods

# Method 1: Direct password
sbt "coreAspose/run -i test-password.pdf -o output1.pptx --password test123"

# Method 2: Environment variable
export TEST_PASSWORD="test123"
sbt "coreAspose/run -i test-password.xlsx -o output2.pdf --password-env TEST_PASSWORD"

# Method 3: Password file
echo "test123" > /tmp/.test-password
chmod 600 /tmp/.test-password
sbt "coreAspose/run -i test-password.docx -o output3.pdf --password-file /tmp/.test-password"
rm /tmp/.test-password

# Test error cases
sbt "coreAspose/run -i test-password.pdf -o output.pptx"  # Should fail: no password
sbt "coreAspose/run -i test-password.pdf -o output.pptx --password wrong"  # Should fail: wrong password
sbt "coreAspose/run -i test-password.pdf -o output.pptx --password test123 --password-env TEST_PASSWORD"  # Should fail: multiple methods
```

## Security Considerations

### Password Handling Best Practices

1. **Never Log Passwords**
   ```scala
   // WRONG
   logger.info(s"Using password: $password")

   // CORRECT
   logger.info("Password provided via environment variable")
   ```

2. **Don't Echo Passwords in Errors**
   ```scala
   // WRONG
   throw new Exception(s"Invalid password: $password")

   // CORRECT
   throw new Exception("Invalid password provided")
   ```

3. **Clear Sensitive Data**
   ```scala
   // Consider implementing in BridgeContext.clear()
   def clear(): Unit = {
     val data = context.get()
     // Attempt to zero out password string (best effort)
     data.password.foreach { pwd =>
       // Note: String is immutable in JVM, this is best-effort only
       // Consider using char[] or SecureString for enhanced security
     }
     context.remove()
   }
   ```

4. **File Permissions**
   - Password files should have restricted permissions (600)
   - Document this in user guide
   - Consider adding warning if file is world-readable

### Security Warnings

Add to CLI help text and documentation:

```
PASSWORD SECURITY NOTES:
  --password          WARNING: Visible in process list (ps, top, etc.)
                      Only use for testing or non-sensitive documents

  --password-env      RECOMMENDED: Secure, not visible in process list
                      Set environment variable before running

  --password-file     RECOMMENDED: Secure for scripts/automation
                      Ensure file has restricted permissions (chmod 600)

NEVER:
  • Commit passwords to version control
  • Share password files with world-readable permissions
  • Use --password in production scripts or CI/CD pipelines
```

## Documentation Updates

### CLAUDE.md Section

Add comprehensive section after "HTML and PowerPoint Conversion":

```markdown
## Password-Protected Documents

XLCR supports converting password-protected documents across all major office formats. Three secure methods are available for providing passwords.

### Supported Formats

- **PDF** - User password and owner password encryption (AES-128, AES-256)
- **Excel** - Workbook passwords (.xlsx, .xls, .xlsm, .xlsb, .ods)
- **Word** - Document passwords (.docx, .doc)
- **PowerPoint** - Presentation passwords (.pptx, .ppt)

### Password Input Methods

#### Method 1: Direct Password (Not Recommended)

```bash
sbt "run -i encrypted.pdf -o output.pptx --password mypassword"
```

**WARNING**: Password visible in process list (`ps aux`, `top`, shell history). Only use for testing.

#### Method 2: Environment Variable (Recommended)

```bash
export DOC_PASSWORD="mypassword"
sbt "run -i encrypted.xlsx -o output.pdf --password-env DOC_PASSWORD"
```

**Benefits**:
- Not visible in process list
- Can be set in secure credential management systems
- Automatically available to all commands in session

#### Method 3: Password File (Recommended for Scripts)

```bash
# Create password file with restricted permissions
echo "mypassword" > ~/.xlcr-password
chmod 600 ~/.xlcr-password

# Use password file
sbt "run -i encrypted.docx -o output.pdf --password-file ~/.xlcr-password"
```

**Benefits**:
- Not visible in process list or shell history
- Ideal for automation and CI/CD pipelines
- File permissions provide access control

### Usage Examples

```bash
# Convert password-protected PDF to PowerPoint
export PDF_PASS="secret123"
sbt "run -i confidential.pdf -o presentation.pptx --password-env PDF_PASS"

# Convert password-protected Excel to PDF
echo "ExcelPass456" > /secure/.excel-pwd
chmod 600 /secure/.excel-pwd
sbt "run -i financial-report.xlsx -o report.pdf --password-file /secure/.excel-pwd"

# Convert password-protected Word document
sbt "run -i contract.docx -o contract.pdf --password-env DOC_PASSWORD"

# Convert password-protected PowerPoint
sbt "run -i presentation.pptx -o slides.pdf --password MySlidePassword"  # Not recommended
```

### Error Handling

#### Password Required
```
ERROR: PowerPoint file is password-protected.
Use --password, --password-env, or --password-file to provide the password.
```

**Solution**: Provide password using one of the three methods above.

#### Invalid Password
```
ERROR: Invalid password for encrypted Excel file.
Please check the password and try again.
```

**Solution**: Verify the password is correct. Note that passwords are case-sensitive.

#### Multiple Password Methods
```
ERROR: Multiple password methods specified: password, password-env.
Use only one of --password, --password-env, or --password-file
```

**Solution**: Use only one password input method at a time.

### Security Best Practices

1. **Never commit passwords to version control**
   ```bash
   # Add to .gitignore
   echo ".xlcr-password" >> .gitignore
   echo "*.password" >> .gitignore
   ```

2. **Use environment variables in CI/CD**
   ```yaml
   # GitHub Actions example
   - name: Convert protected document
     env:
       DOC_PASSWORD: ${{ secrets.DOCUMENT_PASSWORD }}
     run: |
       sbt "run -i encrypted.xlsx -o output.pdf --password-env DOC_PASSWORD"
   ```

3. **Restrict password file permissions**
   ```bash
   chmod 600 password-file  # Owner read/write only
   chown $USER password-file
   ```

4. **Rotate passwords regularly**
   - Update document passwords periodically
   - Update stored passwords in secure credential stores

5. **Audit password usage**
   - Review logs for password-related errors
   - Monitor for authentication failures

### Automatic Encryption Handling

For PDFs, XLCR automatically handles certain types of encryption without requiring passwords:

- **User-level restrictions**: Documents with copy/edit/print restrictions but no user password
- **Owner password only**: PDFs that can be opened without password but have usage restrictions

Example of restriction-only PDF (no password needed):
```bash
# This PDF has copy:no, change:no but no user password
sbt "run -i restricted.pdf -o output.pptx"  # Works automatically
```

### Technical Details

- Passwords are stored in thread-local context during conversion
- Passwords are never logged or echoed in error messages
- Password support uses native Aspose library authentication
- Supports all encryption types supported by Aspose libraries:
  - PDF: RC4, AES-128, AES-256
  - Office: Office 97-2003, Office 2007+, Office 2010+, Office 2013+
```

## Implementation Phases

### Phase 1: Core Infrastructure (Priority: HIGH, Effort: 2 hours)

**Goal**: Add password plumbing without breaking existing functionality

**Tasks**:
1. Add `password` field to `BridgeContextData`
2. Add `password`, `passwordEnv`, `passwordFile` to `AsposeConfig`
3. Add CLI flags to `Main.scala`
4. Implement `resolvePassword()` method
5. Update `initialize()` to set password in context

**Acceptance Criteria**:
- ✅ All existing tests pass
- ✅ New fields added to config
- ✅ CLI accepts password flags
- ✅ Password resolution logic works for all three methods
- ✅ Validation prevents multiple password methods

**Testing**: Unit tests for password resolution logic

### Phase 2: PDF Bridge (Priority: HIGH, Effort: 1 hour)

**Goal**: Extend existing PDF encryption handling to support user passwords

**Tasks**:
1. Update `handleEncryptedPdf()` in `PdfToPowerPointAsposeBridgeImpl`
2. Add password parameter to `PdfDocument` constructor
3. Add specific error handling for invalid passwords
4. Add helpful error message when password required but not provided

**Acceptance Criteria**:
- ✅ Password-protected PDFs convert successfully with correct password
- ✅ Invalid password produces clear error message
- ✅ Missing password produces clear error message with usage instructions
- ✅ Restriction-only PDFs still work without password (backward compatibility)

**Testing**: Integration test with password-protected PDF

### Phase 3: Office Format Bridges (Priority: HIGH, Effort: 3 hours)

**Goal**: Add password support to Excel, Word, PowerPoint bridges

**Tasks**:
1. Excel: Update `ExcelToPdfAsposeBridgeImpl` workbook loading
2. Word: Update `WordToPdfAsposeBridgeImpl` document loading
3. PowerPoint: Update all PowerPoint bridges (to/from PDF, to/from HTML)
4. Add consistent error handling across all bridges

**Acceptance Criteria**:
- ✅ Excel workbooks open with correct password
- ✅ Word documents open with correct password
- ✅ PowerPoint presentations open with correct password
- ✅ Consistent error messages across all formats

**Testing**: Integration tests for each format

### Phase 4: Comprehensive Testing (Priority: MEDIUM, Effort: 3 hours)

**Goal**: Ensure password handling works reliably across all scenarios

**Tasks**:
1. Create password-protected test files for all formats
2. Write unit test suite (`PasswordProtectedDocumentsSpec`)
3. Test all three password input methods
4. Test error cases (wrong password, missing password, multiple methods)
5. Test password files with various permissions

**Acceptance Criteria**:
- ✅ All test files created with documented passwords
- ✅ Unit tests cover all success and failure cases
- ✅ Integration tests validate end-to-end workflows
- ✅ Error messages verified for clarity

**Test Coverage Target**: >90% for password-related code paths

### Phase 5: Documentation (Priority: MEDIUM, Effort: 2 hours)

**Goal**: Comprehensive user documentation for password features

**Tasks**:
1. Update CLAUDE.md with password section
2. Add security best practices documentation
3. Add troubleshooting guide for common password issues
4. Update CLI help text
5. Add code comments for password handling logic

**Acceptance Criteria**:
- ✅ CLAUDE.md has complete password documentation
- ✅ Security warnings clearly documented
- ✅ Usage examples provided for all three methods
- ✅ Troubleshooting guide covers common errors

### Phase 6: Security Hardening (Priority: LOW, Effort: 2 hours)

**Goal**: Enhance security of password handling

**Tasks**:
1. Audit all logging to ensure passwords never logged
2. Review error messages to ensure passwords not echoed
3. Add file permission checks for password files
4. Consider implementing password zeroing in memory
5. Add security warnings to CLI output

**Acceptance Criteria**:
- ✅ No passwords in logs
- ✅ No passwords in error messages
- ✅ Password file permissions validated
- ✅ Security warnings displayed appropriately

**Security Review**: Manual code review focusing on password leakage vectors

## Estimated Timeline

| Phase | Priority | Effort | Dependencies | Status |
|-------|----------|--------|--------------|--------|
| Phase 1: Core Infrastructure | HIGH | 2h | None | Not Started |
| Phase 2: PDF Bridge | HIGH | 1h | Phase 1 | Not Started |
| Phase 3: Office Bridges | HIGH | 3h | Phase 1 | Not Started |
| Phase 4: Testing | MEDIUM | 3h | Phases 2-3 | Not Started |
| Phase 5: Documentation | MEDIUM | 2h | Phases 2-4 | Not Started |
| Phase 6: Security | LOW | 2h | All phases | Not Started |

**Total Estimated Effort**: 13 hours

**Suggested Sprint**: 2-week sprint (1-2 hours per day)

## Breaking Changes

None. This feature is purely additive:
- ✅ No changes to existing bridge interfaces
- ✅ No changes to existing CLI behavior (when password not provided)
- ✅ No changes to existing config files
- ✅ Backward compatible with all existing conversions

## Risks and Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Password exposed in logs | HIGH | MEDIUM | Comprehensive audit, code review, testing |
| Password exposed in errors | HIGH | MEDIUM | Careful error message construction, testing |
| Performance degradation | LOW | LOW | Password checking is fast, minimal overhead |
| Library API differences | MEDIUM | LOW | Test with all Aspose libraries, handle exceptions |
| User confusion about methods | LOW | MEDIUM | Clear documentation, good error messages |

## Future Enhancements

### Certificate-Based Encryption (PDFs)
Some PDFs use certificate-based encryption rather than password encryption. This requires:
- Certificate file (`.cer` or `.pfx`)
- Private key
- Different Aspose.PDF API calls

**Estimated Effort**: 4 hours
**Priority**: LOW (rare use case)

### Keychain Integration
Integrate with system keystores for password management:
- macOS Keychain
- Windows Credential Manager
- Linux Secret Service

**Estimated Effort**: 8 hours
**Priority**: LOW (nice-to-have)

### Interactive Password Prompt
Add option for terminal-based password prompt (hidden input):

```bash
sbt "run -i secret.pdf -o output.pptx --password-prompt"
Password: ********
```

**Estimated Effort**: 2 hours
**Priority**: LOW

### Batch Mode with Different Passwords
Support different passwords for different files in batch processing:

```bash
sbt "run -i docs/ -o output/ --password-map passwords.json"
```

Where `passwords.json`:
```json
{
  "file1.pdf": "password1",
  "file2.xlsx": "password2"
}
```

**Estimated Effort**: 4 hours
**Priority**: LOW

## Success Criteria

The implementation will be considered successful when:

1. ✅ All password-protected formats (PDF, Excel, Word, PowerPoint) convert successfully
2. ✅ All three password input methods work reliably
3. ✅ Error messages are clear and actionable
4. ✅ Security best practices followed (no password leakage)
5. ✅ Comprehensive tests pass (unit + integration)
6. ✅ Documentation complete and accurate
7. ✅ No regression in existing functionality
8. ✅ User feedback positive (if beta tested)

## References

### Aspose Documentation

- **Aspose.PDF Password API**: https://reference.aspose.com/pdf/java/com.aspose.pdf/document/#Document-java.io.InputStream-java.lang.String-
- **Aspose.Cells Password API**: https://reference.aspose.com/cells/java/com.aspose.cells/loadoptions/#setPassword-java.lang.String-
- **Aspose.Words Password API**: https://reference.aspose.com/words/java/com.aspose.words/loadoptions/#setPassword-java.lang.String-
- **Aspose.Slides Password API**: https://reference.aspose.com/slides/java/com.aspose.slides/loadoptions/#setPassword-java.lang.String-

### Security Resources

- **OWASP Password Storage**: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- **Secure Coding Guidelines**: https://wiki.sei.cmu.edu/confluence/display/java/MSC03-J.+Never+hard+code+sensitive+information

### Related XLCR Documents

- `docs/design/splitter-failure-modes-implementation.md` - Failure handling patterns
- `CLAUDE.md` - Main user documentation

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-10-20 | Claude Code | Initial comprehensive roadmap |

## Appendix A: Aspose API Examples

### PDF Password Handling

```java
// Load password-protected PDF
Document pdfDoc = new Document(stream, "password");

// Check if encrypted
boolean isEncrypted = pdfDoc.isEncrypted();

// Decrypt PDF
pdfDoc.decrypt();

// Save without password
pdfDoc.save(outputStream);
```

### Excel Password Handling

```java
// Load password-protected Excel
LoadOptions loadOptions = new LoadOptions();
loadOptions.setPassword("password");
Workbook workbook = new Workbook(stream, loadOptions);

// Workbook is now decrypted and ready to use
workbook.save(outputStream, SaveFormat.PDF);
```

### Word Password Handling

```java
// Load password-protected Word
LoadOptions loadOptions = new LoadOptions();
loadOptions.setPassword("password");
Document doc = new Document(stream, loadOptions);

// Document is now decrypted and ready to use
doc.save(outputStream, SaveFormat.PDF);
```

### PowerPoint Password Handling

```java
// Load password-protected PowerPoint
LoadOptions loadOptions = new LoadOptions();
loadOptions.setPassword("password");
Presentation pres = new Presentation(stream, loadOptions);

// Presentation is now decrypted and ready to use
pres.save(outputStream, SaveFormat.Pdf);
```

## Appendix B: Test Data Specifications

### Test File Requirements

All test files should be created with password: `test123`

| Format | Filename | Content | Size | Notes |
|--------|----------|---------|------|-------|
| PDF | test-password.pdf | 3 pages, text + images | ~100KB | AES-256 |
| Excel | test-password.xlsx | 2 sheets, formulas | ~50KB | Office 2016+ |
| Word | test-password.docx | 2 pages, formatting | ~30KB | Office 2016+ |
| PowerPoint | test-password.pptx | 5 slides, images | ~200KB | Office 2016+ |

### Test Password Storage

Store test passwords in:
```
core-aspose/src/test/resources/test-data/README.md
```

Content:
```markdown
# Test Data

All password-protected test files use password: `test123`

DO NOT commit actual password files to repository.
Create test files locally using the scripts in this directory.
```
