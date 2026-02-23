package com.tjclp.xlcr.v2.aspose

import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

import scala.compiletime.erasedValue
import scala.util.{ Try, Using }

import org.slf4j.LoggerFactory

import com.tjclp.xlcr.v2.transform.ResourceError

/** Aspose product enumeration for type-safe per-product license dispatch. */
enum AsposeProduct:
  case Words, Cells, Email, Slides, Pdf, Zip

// Package-level type aliases for clean call-site syntax: require[Words] not require[AsposeProduct.Words.type]
type Words  = AsposeProduct.Words.type
type Cells  = AsposeProduct.Cells.type
type Email  = AsposeProduct.Email.type
type Slides = AsposeProduct.Slides.type
type Pdf    = AsposeProduct.Pdf.type
type Zip    = AsposeProduct.Zip.type

/**
 * Per-product lazy Aspose license loader with license-or-fallback semantics.
 *
 * Each product license is initialized at most once, on first use. The total license bytes are
 * resolved lazily and cached by the JVM's `lazy val` thread-safety guarantee. Individual products
 * fall back to product-specific env vars / files if the total license is unavailable.
 *
 * Two call-site APIs:
 *   - `init[Words]` — apply license if available, log warning if not (evaluation mode)
 *   - `require[Words]` — apply license, throw `ResourceError.missingLicense` if unavailable
 *
 * Use `require[P]` in conversions/splitters so unlicensed products fail fast and the unified
 * dispatch can fall back to LibreOffice/Core. Use `init[P]` only when evaluation mode is
 * acceptable.
 *
 * For conversions that need multiple products (e.g. Email -> PDF needs Email + Words), use
 * `requireAll[(Email, Words)]`.
 */
object AsposeLicenseV2:

  private val logger = LoggerFactory.getLogger(getClass)

  private final val TotalLicFile = "Aspose.Total.Java.lic"
  private final val TotalEnvVar  = "ASPOSE_TOTAL_LICENSE_B64"

  // ── Testing overrides ──────────────────────────────────────────
  // XLCR_NO_ASPOSE_LICENSE=1     — suppress ALL license resolution (complete kill switch)
  // XLCR_NO_CLASSPATH_LICENSE=1  — suppress classpath/JAR-bundled licenses only (CWD + env still work)
  private lazy val forceNoLicense: Boolean =
    Option(System.getenv("XLCR_NO_ASPOSE_LICENSE")).exists(_.nonEmpty)

  private lazy val skipClasspath: Boolean =
    forceNoLicense || Option(System.getenv("XLCR_NO_CLASSPATH_LICENSE")).exists(_.nonEmpty)

  // ── Total license bytes (resolved once, cached by JVM lazy val) ──
  private lazy val licenseBytes: Option[Array[Byte]] =
    if forceNoLicense then None
    else envBytes(TotalEnvVar).orElse(fileOrClasspathBytes(TotalLicFile))

  /** Fast check: does any total license source exist? (no side effects, no per-product check) */
  lazy val licenseAvailable: Boolean = licenseBytes.isDefined

  // ── Shared helpers ───────────────────────────────────────────────
  private def envBytes(varName: String): Option[Array[Byte]] =
    if forceNoLicense then None
    else
      Option(System.getenv(varName))
        .filter(_.nonEmpty)
        .flatMap(b64 => Try(Base64.getDecoder.decode(b64)).toOption)

  private def fileOrClasspathBytes(fileName: String): Option[Array[Byte]] =
    if forceNoLicense then None
    else
      val file = new File(System.getProperty("user.dir"), fileName)
      if file.isFile && file.canRead then
        Using(Files.newInputStream(file.toPath))(_.readAllBytes()).toOption
      else if skipClasspath then None
      else
        Option(getClass.getResourceAsStream(s"/$fileName"))
          .flatMap(is => Using(is)(_.readAllBytes()).toOption)

  // ── Per-product init guards + licensed state ─────────────────────
  // True when license initialization has completed (success or failure).
  private val wordsInit  = new AtomicBoolean(false)
  private val cellsInit  = new AtomicBoolean(false)
  private val emailInit  = new AtomicBoolean(false)
  private val slidesInit = new AtomicBoolean(false)
  private val pdfInit    = new AtomicBoolean(false)
  private val zipInit    = new AtomicBoolean(false)

  // True ONLY if setLicense succeeded for this product
  private val wordsLicensed  = new AtomicBoolean(false)
  private val cellsLicensed  = new AtomicBoolean(false)
  private val emailLicensed  = new AtomicBoolean(false)
  private val slidesLicensed = new AtomicBoolean(false)
  private val pdfLicensed    = new AtomicBoolean(false)
  private val zipLicensed    = new AtomicBoolean(false)

  private[aspose] def initOnce(guard: AtomicBoolean)(init: => Unit): Unit =
    if !guard.get() then
      guard.synchronized {
        if !guard.get() then
          try init
          finally guard.set(true)
      }

  private def initProduct(
    guard: AtomicBoolean,
    licensed: AtomicBoolean,
    name: String,
    licFile: String,
    envVar: String,
    setLicense: Array[Byte] => Unit
  ): Unit =
    initOnce(guard) {
      val bytes = licenseBytes
        .orElse(envBytes(envVar))
        .orElse(fileOrClasspathBytes(licFile))
      bytes match
        case Some(b) =>
          Try(setLicense(b)).fold(
            ex => logger.error(s"Failed to load Aspose.$name license", ex),
            _ => { licensed.set(true); logger.info(s"Aspose.$name license applied") }
          )
        case None =>
          logger.warn(s"No Aspose.$name license found; running in evaluation mode.")
    }

  // ── Per-product init methods ─────────────────────────────────────
  def initWords(): Unit = initProduct(
    wordsInit,
    wordsLicensed,
    "Words",
    "Aspose.Words.Java.lic",
    "ASPOSE_WORDS_LICENSE_B64",
    b => new com.aspose.words.License().setLicense(new java.io.ByteArrayInputStream(b))
  )

  def initCells(): Unit = initProduct(
    cellsInit,
    cellsLicensed,
    "Cells",
    "Aspose.Cells.Java.lic",
    "ASPOSE_CELLS_LICENSE_B64",
    b => new com.aspose.cells.License().setLicense(new java.io.ByteArrayInputStream(b))
  )

  def initEmail(): Unit = initProduct(
    emailInit,
    emailLicensed,
    "Email",
    "Aspose.Email.Java.lic",
    "ASPOSE_EMAIL_LICENSE_B64",
    b => new com.aspose.email.License().setLicense(new java.io.ByteArrayInputStream(b))
  )

  def initSlides(): Unit = initProduct(
    slidesInit,
    slidesLicensed,
    "Slides",
    "Aspose.Slides.Java.lic",
    "ASPOSE_SLIDES_LICENSE_B64",
    b => new com.aspose.slides.License().setLicense(new java.io.ByteArrayInputStream(b))
  )

  def initPdf(): Unit = initProduct(
    pdfInit,
    pdfLicensed,
    "Pdf",
    "Aspose.Pdf.Java.lic",
    "ASPOSE_PDF_LICENSE_B64",
    b => new com.aspose.pdf.License().setLicense(new java.io.ByteArrayInputStream(b))
  )

  def initZip(): Unit = initProduct(
    zipInit,
    zipLicensed,
    "Zip",
    "Aspose.Zip.Java.lic",
    "ASPOSE_ZIP_LICENSE_B64",
    b => new com.aspose.zip.License().setLicense(new java.io.ByteArrayInputStream(b))
  )

  // ── Type-safe compile-time dispatch (apply license, warn if missing) ──
  transparent inline def init[P <: AsposeProduct]: Unit =
    inline erasedValue[P] match
      case _: AsposeProduct.Words.type  => initWords()
      case _: AsposeProduct.Cells.type  => initCells()
      case _: AsposeProduct.Email.type  => initEmail()
      case _: AsposeProduct.Slides.type => initSlides()
      case _: AsposeProduct.Pdf.type    => initPdf()
      case _: AsposeProduct.Zip.type    => initZip()

  transparent inline def initAll[T <: Tuple]: Unit =
    inline erasedValue[T] match
      case _: EmptyTuple => ()
      case _: (h *: t) =>
        init[h & AsposeProduct]
        initAll[t]

  // ── Per-product licensed checks ──────────────────────────────────
  transparent inline def isLicensed[P <: AsposeProduct]: Boolean =
    inline erasedValue[P] match
      case _: AsposeProduct.Words.type  => wordsLicensed.get()
      case _: AsposeProduct.Cells.type  => cellsLicensed.get()
      case _: AsposeProduct.Email.type  => emailLicensed.get()
      case _: AsposeProduct.Slides.type => slidesLicensed.get()
      case _: AsposeProduct.Pdf.type    => pdfLicensed.get()
      case _: AsposeProduct.Zip.type    => zipLicensed.get()

  /**
   * Runtime product license check with lazy initialization.
   *
   * Calling this method is safe for capability probing: concurrent first-time callers block until
   * initialization completes, then all observe the same licensed state.
   */
  def isProductLicensed(product: AsposeProduct): Boolean =
    product match
      case AsposeProduct.Words =>
        initWords()
        wordsLicensed.get()
      case AsposeProduct.Cells =>
        initCells()
        cellsLicensed.get()
      case AsposeProduct.Email =>
        initEmail()
        emailLicensed.get()
      case AsposeProduct.Slides =>
        initSlides()
        slidesLicensed.get()
      case AsposeProduct.Pdf =>
        initPdf()
        pdfLicensed.get()
      case AsposeProduct.Zip =>
        initZip()
        zipLicensed.get()

  // ── Require license or throw ResourceError (for fallback dispatch) ──
  transparent inline def require[P <: AsposeProduct]: Unit =
    init[P]
    inline erasedValue[P] match
      case _: AsposeProduct.Words.type =>
        if !wordsLicensed.get() then throw ResourceError.missingLicense("Aspose.Words")
      case _: AsposeProduct.Cells.type =>
        if !cellsLicensed.get() then throw ResourceError.missingLicense("Aspose.Cells")
      case _: AsposeProduct.Email.type =>
        if !emailLicensed.get() then throw ResourceError.missingLicense("Aspose.Email")
      case _: AsposeProduct.Slides.type =>
        if !slidesLicensed.get() then throw ResourceError.missingLicense("Aspose.Slides")
      case _: AsposeProduct.Pdf.type =>
        if !pdfLicensed.get() then throw ResourceError.missingLicense("Aspose.Pdf")
      case _: AsposeProduct.Zip.type =>
        if !zipLicensed.get() then throw ResourceError.missingLicense("Aspose.Zip")

  transparent inline def requireAll[T <: Tuple]: Unit =
    inline erasedValue[T] match
      case _: EmptyTuple => ()
      case _: (h *: t) =>
        require[h & AsposeProduct]
        requireAll[t]
