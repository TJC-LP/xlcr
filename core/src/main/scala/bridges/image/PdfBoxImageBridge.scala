package com.tjclp.xlcr
package bridges.image

import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationPdf, ImageJpeg, ImagePng}

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.{ImageType, PDFRenderer}
import org.slf4j.LoggerFactory

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.stream.MemoryCacheImageOutputStream
import javax.imageio.{IIOImage, ImageIO, ImageWriteParam}
import scala.reflect.ClassTag

/** Base implementation for PDFBox-based PDF to image conversion.
  * 
  * Uses Apache PDFBox to render PDF pages as images with support for both
  * PNG and JPEG output formats.
  * 
  * @tparam O The output image MIME type
  */
abstract class PdfBoxImageBridge[O <: MimeType](implicit override val classTag: ClassTag[O]) extends PdfToImageBridgeBase[O] {
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  // Default implementation for the input parser - just pass through the PDF
  override private[bridges] def inputParser = 
    new parsers.Parser[ApplicationPdf.type, FileContent[ApplicationPdf.type]] {
      override def parse(input: FileContent[ApplicationPdf.type]): FileContent[ApplicationPdf.type] = input
    }
  
  // Default implementation for the output renderer - uses our renderPage method
  override private[bridges] def outputRenderer = 
    new renderers.Renderer[FileContent[ApplicationPdf.type], O] {
      override def render(model: FileContent[ApplicationPdf.type]): FileContent[O] = {
        val imageBytes = renderPage(model.data, 0, ImageRenderConfig(targetMime))
        FileContent(imageBytes, targetMime)
      }
    }

  /** Renders a specific page from a PDF to an image using PDFBox.
    * 
    * @param pdfBytes The raw PDF bytes to render
    * @param pageIdx The page index to render (0-based)
    * @param cfg The rendering configuration
    * @return The rendered image bytes
    */
  override protected def renderPage(
    pdfBytes: Array[Byte],
    pageIdx: Int,
    cfg: ImageRenderConfig
  ): Array[Byte] = {
    val document = PDDocument.load(pdfBytes)
    try {
      val renderer = new PDFRenderer(document)

      // First render at the requested DPI
      var image = renderer.renderImageWithDPI(pageIdx, cfg.initialDpi, ImageType.RGB)

      // Scale down if dimensions exceed max
      image = scaleImageIfNeeded(image, cfg.maxWidthPx, cfg.maxHeightPx)

      // Convert to bytes with appropriate format
      if (targetMime == ImageJpeg) {
        renderJpeg(image, cfg.initialQuality)
      } else {
        renderPng(image)
      }
    } finally {
      document.close()
    }
  }

  /** Scale down an image if it exceeds max dimensions.
    * 
    * @param image The image to scale
    * @param maxWidth Maximum width in pixels
    * @param maxHeight Maximum height in pixels
    * @return The scaled image (or original if no scaling needed)
    */
  private def scaleImageIfNeeded(
    image: BufferedImage,
    maxWidth: Int,
    maxHeight: Int
  ): BufferedImage = {
    val originalWidth = image.getWidth
    val originalHeight = image.getHeight

    if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
      return image // No scaling needed
    }

    // Calculate scale factor to fit within bounds while maintaining aspect ratio
    val widthScale = maxWidth.toDouble / originalWidth
    val heightScale = maxHeight.toDouble / originalHeight
    val scale = Math.min(widthScale, heightScale)

    val newWidth = (originalWidth * scale).toInt
    val newHeight = (originalHeight * scale).toInt

    logger.debug(s"Scaling image from ${originalWidth}x${originalHeight} to ${newWidth}x${newHeight}")

    // Create scaled instance
    val scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
    val g = scaledImage.createGraphics()
    g.drawImage(image, 0, 0, newWidth, newHeight, null)
    g.dispose()

    scaledImage
  }

  /** Render a BufferedImage as JPEG with specified quality.
    * 
    * @param image The image to render
    * @param quality The JPEG quality factor (0.0-1.0)
    * @return The JPEG image bytes
    */
  private def renderJpeg(
    image: BufferedImage,
    quality: Float
  ): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val outputStream = new MemoryCacheImageOutputStream(baos)

    val jpegWriter = ImageIO.getImageWritersByFormatName("jpeg").next()
    jpegWriter.setOutput(outputStream)

    val params = jpegWriter.getDefaultWriteParam()
    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
    params.setCompressionQuality(quality)

    jpegWriter.write(null, new IIOImage(image, null, null), params)
    jpegWriter.dispose()
    outputStream.close()

    baos.toByteArray()
  }

  /** Render a BufferedImage as PNG.
    * 
    * @param image The image to render
    * @return The PNG image bytes
    */
  private def renderPng(image: BufferedImage): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    ImageIO.write(image, "png", baos)
    baos.toByteArray()
  }
}

/** Concrete implementation of PDF to PNG conversion using PDFBox.
  */
object PdfToPngBridge extends PdfBoxImageBridge[ImagePng.type] {
  override val targetMime: ImagePng.type = ImagePng
}

/** Concrete implementation of PDF to JPEG conversion using PDFBox.
  */
object PdfToJpegBridge extends PdfBoxImageBridge[ImageJpeg.type] {
  override val targetMime: ImageJpeg.type = ImageJpeg
}