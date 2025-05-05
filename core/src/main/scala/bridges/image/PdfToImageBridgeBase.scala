package com.tjclp.xlcr
package bridges.image

import bridges.{ BridgeConfig, SimpleBridge }
import models.FileContent
import types.MimeType
import types.MimeType.ApplicationPdf

import org.slf4j.LoggerFactory
import scala.reflect.ClassTag

/**
 * Base trait for PDF to image bridges that provides common auto-tuning functionality.
 *
 * This trait implements the logic for automatic tuning of rendering parameters to meet size
 * constraints, while delegating the actual rendering to concrete implementations.
 *
 * @tparam O
 *   The output MIME type (should be ImagePng or ImageJpeg)
 */
trait PdfToImageBridgeBase[O <: MimeType]
    extends SimpleBridge[ApplicationPdf.type, O] {
  // ClassTag needed for Scala 3 compatibility
  implicit val classTag: ClassTag[O]

  private val logger = LoggerFactory.getLogger(getClass)

  /** The target MIME type this bridge produces. Must be implemented by concrete classes. */
  val targetMime: O

  /**
   * Renders a specific page from a PDF to the target image format.
   *
   * This method must be implemented by concrete classes that provide the actual rendering
   * implementation (e.g., PDFBox, Aspose).
   *
   * @param pdfBytes
   *   The raw PDF bytes to render
   * @param cfg
   *   The rendering configuration
   * @return
   *   The rendered image bytes
   */
  protected def renderPage(
    pdfBytes: Array[Byte],
    cfg: ImageRenderConfig
  ): Array[Byte]

  /**
   * Converts a PDF to an image with auto-tuning to meet size constraints.
   *
   * This implementation handles the common auto-tuning logic while delegating the actual rendering
   * to the concrete implementation of renderPage.
   *
   * @param input
   *   The input PDF file content
   * @param cfgOpt
   *   Optional bridge configuration
   * @return
   *   The rendered image as file content
   */
  final override def convert(
    input: FileContent[ApplicationPdf.type],
    cfgOpt: Option[BridgeConfig] = None
  ): FileContent[O] = {
    // Extract config or use defaults
    val cfg = cfgOpt
      .collect { case c: ImageRenderConfig => c }
      .getOrElse(ImageRenderConfig(targetMime))

    // Apply auto-tuning and render
    val tuned = autoTune(input.data, cfg)

    // Return as the target MIME type
    FileContent[O](tuned, targetMime)
  }

  /**
   * Auto-tunes rendering parameters to meet size constraints.
   *
   * This method will progressively reduce quality/DPI until the rendered image meets the size
   * constraints or the maximum attempts are reached. If after all attempts the image is still too
   * large, it throws an ImageSizeException to allow for proper quarantine and further analysis.
   *
   * @param pdfBytes
   *   The PDF bytes to render
   * @param cfg
   *   The initial rendering configuration
   * @return
   *   The rendered image bytes after tuning
   * @throws ImageSizeException
   *   if the image cannot be rendered below the maximum size
   */
  private def autoTune(
    pdfBytes: Array[Byte],
    cfg: ImageRenderConfig
  ): Array[Byte] = {
    var currentDpi     = cfg.initialDpi
    var currentQuality = cfg.initialQuality
    var attempt        = 0
    var currentConfig  = cfg

    // Initial rendering
    var imgBytes = renderPage(pdfBytes, currentConfig)

    // If auto-tuning is enabled and the image is too large, try progressively lower quality/DPI
    while (
      cfg.autoTune &&
      imgBytes.length > cfg.maxBytes &&
      attempt < cfg.maxAttempts - 1 // -1 because we already did the first attempt
    ) {
      attempt += 1

      // Calculate the over-size ratio to determine how much to reduce
      val overRatio = imgBytes.length.toDouble / cfg.maxBytes

      // More intelligent quality/DPI reduction based on how far over the limit we are
      // and whether we're using JPEG or PNG
      if (targetMime == MimeType.ImageJpeg) {
        // For JPEG, focus on quality reduction first, then DPI if quality is already low
        currentQuality = Math.max(0.5f, currentQuality / Math.sqrt(overRatio).toFloat)

        // If quality is getting too low, also reduce DPI
        if (currentQuality < 0.6f) {
          currentDpi = Math.max(72, (currentDpi / 1.2).toInt)
        }
      } else {
        // For PNG, we can only adjust DPI since we can't control compression level directly
        currentDpi = Math.max(72, (currentDpi / Math.sqrt(overRatio)).toInt)
      }

      // Update the configuration
      currentConfig = cfg.copy(
        initialDpi = currentDpi,
        initialQuality = currentQuality
      )

      logger.debug(
        s"Image size (${imgBytes.length / 1024} KB) exceeds limit (${cfg.maxBytes / 1024} KB). " +
          s"Auto-tuning attempt ${attempt + 1}: DPI=$currentDpi, quality=$currentQuality"
      )

      // Try rendering with new parameters
      imgBytes = renderPage(pdfBytes, currentConfig)
    }

    // If after all attempts the image is still too large and auto-tuning is enabled,
    // throw a custom exception for quarantine
    if (imgBytes.length > cfg.maxBytes && cfg.autoTune) {
      val totalAttempts = attempt + 1

      // Log the warning first
      logger.error(
        s"Image size (${imgBytes.length / 1024} KB) still exceeds limit (${cfg.maxBytes / 1024} KB) " +
          s"after $totalAttempts auto-tuning attempts"
      )

      // Then throw the exception with detailed information
      throw new ImageSizeException(
        s"Failed to render image below ${cfg.maxBytes / 1024} KB after $totalAttempts attempts",
        imgBytes.length,
        cfg.maxBytes,
        totalAttempts
      )
    }

    imgBytes
  }
}
