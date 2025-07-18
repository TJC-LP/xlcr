package com.tjclp.xlcr
package bridges.image

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.stream.MemoryCacheImageOutputStream
import javax.imageio.{ IIOImage, ImageIO, ImageWriteParam }

import scala.reflect.ClassTag
import scala.util.Using

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.{ ImageType, PDFRenderer }

import models.FileContent
import renderers.RendererConfig
import types.MimeType
import types.MimeType.{ ApplicationPdf, ImageJpeg }
import utils.image.ImageUtils
import utils.resource.ResourceWrappers._

/**
 * Base implementation for PDFBox-based PDF to image conversion.
 *
 * Uses Apache PDFBox to render PDF pages as images with support for both PNG and JPEG output
 * formats.
 *
 * @tparam O
 *   The output image MIME type
 */
abstract class PdfBoxImageBridge[O <: MimeType](implicit
  override val classTag: ClassTag[O]
) extends PdfToImageBridgeBase[O] {

  // Default implementation for the output renderer - uses our renderPage method
  override private[bridges] def outputRenderer =
    (model: FileContent[ApplicationPdf.type], config: Option[RendererConfig]) => {
      val imageBytes =
        renderPage(
          model.data,
          ImageRenderConfig
            .fromRendererConfig(config)
            .getOrElse(ImageRenderConfig())
        )
      FileContent(imageBytes, targetMime)
    }

  /**
   * Renders a specific page from a PDF to an image using PDFBox.
   *
   * @param pdfBytes
   *   The raw PDF bytes to render
   * @param cfg
   *   The rendering configuration
   * @return
   *   The rendered image bytes
   */
  override protected def renderPage(
    pdfBytes: Array[Byte],
    cfg: ImageRenderConfig
  ): Array[Byte] =
    Using.resource(PDDocument.load(pdfBytes)) { document =>
      val renderer = new PDFRenderer(document)

      // First render at the requested DPI
      var image =
        renderer.renderImageWithDPI(cfg.pageIdx, cfg.initialDpi, ImageType.RGB)

      // Scale down if dimensions exceed max
      image = scaleImageIfNeeded(image, cfg.maxWidthPx, cfg.maxHeightPx)

      // Convert to bytes with appropriate format
      if (targetMime == ImageJpeg) {
        renderJpeg(image, cfg.initialQuality)
      } else {
        renderPng(image)
      }
    }

  /**
   * Scale down an image if it exceeds max dimensions.
   *
   * @param image
   *   The image to scale
   * @param maxWidth
   *   Maximum width in pixels
   * @param maxHeight
   *   Maximum height in pixels
   * @return
   *   The scaled image (or original if no scaling needed)
   */
  private def scaleImageIfNeeded(
    image: BufferedImage,
    maxWidth: Int,
    maxHeight: Int
  ): BufferedImage =
    // Use the ImageUtils resizeImage method which applies bilinear interpolation
    // for better quality and handles all the dimension calculations
    ImageUtils.resizeImage(image, maxWidth, maxHeight)

  /**
   * Render a BufferedImage as JPEG with specified quality.
   *
   * @param image
   *   The image to render
   * @param quality
   *   The JPEG quality factor (0.0-1.0)
   * @return
   *   The JPEG image bytes
   */
  private def renderJpeg(
    image: BufferedImage,
    quality: Float
  ): Array[Byte] = {
    val baos = new ByteArrayOutputStream()

    Using.Manager { use =>
      val outputStream = use(new MemoryCacheImageOutputStream(baos))
      val jpegWriter   = ImageIO.getImageWritersByFormatName("jpeg").next()
      use(autoCloseable(jpegWriter.dispose()))

      jpegWriter.setOutput(outputStream)

      val params = jpegWriter.getDefaultWriteParam
      params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
      params.setCompressionQuality(quality)

      jpegWriter.write(null, new IIOImage(image, null, null), params)
    }.get

    baos.toByteArray
  }

  /**
   * Render a BufferedImage as PNG.
   *
   * @param image
   *   The image to render
   * @return
   *   The PNG image bytes
   */
  private def renderPng(image: BufferedImage): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    ImageIO.write(image, "png", baos)
    baos.toByteArray
  }
}
