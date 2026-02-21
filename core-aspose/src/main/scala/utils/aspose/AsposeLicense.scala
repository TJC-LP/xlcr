package com.tjclp.xlcr
package utils.aspose

import java.io.{ File, InputStream }
import java.nio.file.{ Files, Paths }
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

import scala.util.{ Try, Using }

import org.slf4j.LoggerFactory

/**
 * Thread‑safe Aspose license loader with Base64 / env‑var, file, and classpath support.
 * Compile‑error fixes: remove `Using` on `Array[Byte]` (no Releasable) and use pattern‑matching
 * `fold` instead of `recover` for `Try`.
 */
@deprecated("Use AsposeLicenseV2 (v2 per-product licensing) instead", "0.3.0")
object AsposeLicense {

  private val logger      = LoggerFactory.getLogger(getClass)
  private val initialized = new AtomicBoolean(false)

  private final val TotalLicFile = "Aspose.Total.Java.lic"
  private final val TotalEnvVar  = "ASPOSE_TOTAL_LICENSE_B64"

  // ── Testing overrides (mirrors AsposeLicenseV2) ──────────────────
  // XLCR_NO_ASPOSE_LICENSE=1     — suppress ALL license resolution (complete kill switch)
  // XLCR_NO_CLASSPATH_LICENSE=1  — suppress classpath/JAR-bundled licenses only (CWD + env still work)
  private lazy val forceNoLicense: Boolean =
    Option(System.getenv("XLCR_NO_ASPOSE_LICENSE")).exists(_.nonEmpty)
  private lazy val skipClasspath: Boolean =
    forceNoLicense || Option(System.getenv("XLCR_NO_CLASSPATH_LICENSE")).exists(_.nonEmpty)

  /* ------------------------------ Products ------------------------------------ */

  sealed abstract class Product(val name: String, val licFile: String, val envVar: String) {
    def apply(bytes: Array[Byte]): Unit
  }

  object Product {
    case object Words
        extends Product("Words", "Aspose.Words.Java.lic", "ASPOSE_WORDS_LICENSE_B64") {
      def apply(b: Array[Byte]): Unit =
        new com.aspose.words.License().setLicense(new java.io.ByteArrayInputStream(b))
    }
    case object Cells
        extends Product("Cells", "Aspose.Cells.Java.lic", "ASPOSE_CELLS_LICENSE_B64") {
      def apply(b: Array[Byte]): Unit =
        new com.aspose.cells.License().setLicense(new java.io.ByteArrayInputStream(b))
    }
    case object Email
        extends Product("Email", "Aspose.Email.Java.lic", "ASPOSE_EMAIL_LICENSE_B64") {
      def apply(b: Array[Byte]): Unit =
        new com.aspose.email.License().setLicense(new java.io.ByteArrayInputStream(b))
    }
    case object Slides
        extends Product("Slides", "Aspose.Slides.Java.lic", "ASPOSE_SLIDES_LICENSE_B64") {
      def apply(b: Array[Byte]): Unit =
        new com.aspose.slides.License().setLicense(new java.io.ByteArrayInputStream(b))
    }
    case object Pdf extends Product("Pdf", "Aspose.Pdf.Java.lic", "ASPOSE_PDF_LICENSE_B64") {
      def apply(b: Array[Byte]): Unit =
        new com.aspose.pdf.License().setLicense(new java.io.ByteArrayInputStream(b))
    }
    case object Zip extends Product("Zip", "Aspose.Zip.Java.lic", "ASPOSE_ZIP_LICENSE_B64") {
      def apply(b: Array[Byte]): Unit =
        new com.aspose.zip.License().setLicense(new java.io.ByteArrayInputStream(b))
    }

    val values: Seq[Product] = Seq(Words, Cells, Email, Slides, Pdf, Zip)
  }

  /* ------------------------------ Public API ---------------------------------- */

  def initializeIfNeeded(): Unit =
    if (initialized.compareAndSet(false, true)) {
      if (forceNoLicense) {
        logger.info("XLCR_NO_ASPOSE_LICENSE set – skipping all Aspose license resolution (v1).")
      } else if (!loadFromEnv()) {
        findLicenseStream(TotalLicFile) match {
          case Some(is) =>
            Using(is) { stream =>
              val bytes = stream.readAllBytes()
              loadForAllProducts(bytes)
            }.fold(
              ex => logger.error("Failed to load Aspose 'total' license", ex),
              _ => logger.info("Aspose 'total' license loaded (file / classpath).")
            )
          case None =>
            loadIndividualLicenses()
        }
      }
    }

  /* Load total license from explicit path */
  def loadTotal(path: String): Unit =
    Using(Files.newInputStream(Paths.get(path))) { is =>
      val bytes = is.readAllBytes()
      loadForAllProducts(bytes)
    }.fold(
      ex => logger.error(s"Failed to load Aspose 'total' license from $path", ex),
      _ => logger.info(s"Aspose 'total' license loaded from $path")
    )

  /* Load product‑specific license from explicit path */
  def loadProduct(product: Product, path: String): Unit =
    Using(Files.newInputStream(Paths.get(path))) { is =>
      product(is.readAllBytes())
    }.fold(
      ex => logger.error(s"Failed to load Aspose.${product.name} license from $path", ex),
      _ => logger.info(s"Aspose.${product.name} license loaded from $path")
    )

  /* ------------------------------ Env‑var support ----------------------------- */

  private def loadFromEnv(): Boolean = {
    if (forceNoLicense) return false
    def env(n: String) = Option(System.getenv(n)).filter(_.nonEmpty)

    env(TotalEnvVar) match {
      case Some(b64) =>
        decode(b64).foreach(loadForAllProducts)
        logger.info("Aspose 'total' license loaded from env var.")
        true
      case None =>
        var any = false
        Product.values.foreach { p =>
          env(p.envVar).flatMap(decode).foreach { bytes =>
            Try(p(bytes)).fold(
              ex => logger.error(s"Failed loading Aspose.${p.name} license from env", ex),
              _ => {
                logger.info(s"Aspose.${p.name} license loaded from env var")
                any = true
              }
            )
          }
        }
        any
    }
  }

  private def decode(b64: String): Option[Array[Byte]] = Try(Base64.getDecoder.decode(b64)).toOption

  /* ------------------------------ Helpers ------------------------------------- */

  private def loadForAllProducts(bytes: Array[Byte]): Unit =
    Product.values.foreach { p =>
      Try(p(bytes)).fold(
        ex => logger.error(s"Failed loading Aspose.${p.name} license", ex),
        _ => logger.debug(s"Aspose.${p.name} license applied")
      )
    }

  private def findLicenseStream(fileName: String): Option[InputStream] = {
    if (forceNoLicense) return None
    val file = new File(System.getProperty("user.dir"), fileName)
    if (file.isFile && file.canRead) Some(Files.newInputStream(file.toPath))
    else if (skipClasspath) None
    else Option(getClass.getResourceAsStream(s"/$fileName"))
  }

  private def loadIndividualLicenses(): Unit = {
    var any = false
    Product.values.foreach { p =>
      findLicenseStream(p.licFile).foreach { is =>
        Using(is) { stream =>
          p(stream.readAllBytes())
        }.fold(
          ex => logger.error(s"Failed loading Aspose.${p.name} license (file / classpath)", ex),
          _ => {
            logger.info(s"Aspose.${p.name} license loaded (file / classpath).")
            any = true
          }
        )
      }
    }
    if (!any) logger.warn("No Aspose license found; running in evaluation mode.")
  }
}
