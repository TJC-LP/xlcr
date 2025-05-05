package com.tjclp.xlcr
package bridges.image

import bridges.HighPrioritySimpleBridge
import models.FileContent
import renderers.RendererConfig
import types.MimeType
import types.MimeType.{ ApplicationPdf, ImageJpeg }
import utils.image.ImageUtils

import com.aspose.pdf.Document
import com.aspose.pdf.devices.{ JpegDevice, PngDevice, Resolution }
import org.slf4j.LoggerFactory

import java.io.ByteArrayOutputStream
import scala.reflect.ClassTag

/**
 * Base implementation for Aspose-based PDF to image conversion.
 *
 * Uses Aspose.PDF to render PDF pages as images with support for both PNG and JPEG output formats.
 * This implementation provides higher quality and better fidelity than the PDFBox-based one.
 *
 * @tparam O
 *   The output image MIME type
 */
abstract class PdfAsposeImageBridge[O <: MimeType](implicit
  override val classTag: ClassTag[O]
) extends PdfToImageBridgeBase[O] with HighPrioritySimpleBridge[ApplicationPdf.type, O] {

  private val logger = LoggerFactory.getLogger(getClass)

  // Default implementation for the output renderer - uses our renderPage method
  override private[bridges] def outputRenderer =
    (
      model: FileContent[ApplicationPdf.type],
      config: Option[RendererConfig]
    ) => {
      // Convert the RendererConfig to ImageRenderConfig or use default
      val imageConfig = ImageRenderConfig
        .fromRendererConfig(config, targetMime)
        .getOrElse(ImageRenderConfig(targetMime))

      val imageBytes = renderPage(model.data, imageConfig)
      FileContent(imageBytes, targetMime)
    }

  /**
   * Renders a specific page from a PDF to an image using Aspose.
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
  ): Array[Byte] = {
    // Load the document from bytes
    val document = new Document(pdfBytes)
    try {
      // Create an output stream for the image data
      val baos = new ByteArrayOutputStream()

      // Get the page to render (Aspose uses 1-based indexing)
      val pageNumber = cfg.pageIdx + 1
      val page       = document.getPages.get_Item(pageNumber)
      val pageInfo   = page.getPageInfo

      // Scale up by DPI
      val pageWidth  = pageInfo.getWidth * cfg.initialDpi / 72
      val pageHeight = pageInfo.getHeight * cfg.initialDpi / 72

      // Calculate dimensions that respect max width/height while preserving aspect ratio
      val (width, height) = ImageUtils.calculateOptimalDimensions(
        pageWidth.toInt,
        pageHeight.toInt,
        cfg.maxWidthPx,
        cfg.maxHeightPx
      )

      logger.debug(s"Rendering PDF page $pageNumber with dimensions ${width}x${height}")

      // Configure resolution
      val resolution = new Resolution(cfg.initialDpi)

      // Use a try-finally block to ensure resources are properly cleaned up
      try {
        if (targetMime == ImageJpeg) {
          // Create JPEG device with quality setting and dimensions
          val jpegDevice = new JpegDevice(
            width,
            height,
            resolution,
            (cfg.initialQuality * 100).toInt // Aspose uses 0-100 scale as integer
          )

          // Process the page to the output stream
          jpegDevice.process(page, baos)
        } else {
          // Create PNG device with dimensions
          val pngDevice = new PngDevice(width, height, resolution)

          // Process the page to the output stream
          pngDevice.process(page, baos)
        }

        // Return the image bytes
        baos.toByteArray
      } finally
        // Close the output stream
        baos.close()
    } finally
      // Close the document to release resources
      document.close()
  }

  // Now using the shared ImageUtils implementation for optimal dimension calculation
}
