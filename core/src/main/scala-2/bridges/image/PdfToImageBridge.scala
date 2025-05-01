package com.tjclp.xlcr
package bridges.image

import bridges.SimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationPdf, ImagePng, ImageJpeg}

import scala.reflect.ClassTag

import org.apache.pdfbox.rendering.{PDFRenderer, ImageType}
import org.apache.pdfbox.pdmodel.PDDocument

import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, ByteArrayInputStream}
import javax.imageio.{ImageIO, IIOImage, ImageWriteParam}
import javax.imageio.stream.MemoryCacheImageOutputStream

import scala.util.{Try, Success, Failure}

/** PdfToImageBridge converts a PDF document to an image format (PNG or JPEG).
  * It uses Apache PDFBox to render PDF pages as images.
  *
  * This is the Scala 2.12 version.
  */
object PdfToPngBridge extends SimpleBridge[ApplicationPdf.type, ImagePng.type] {
  // Required ClassTag implicits for Scala 2.12
  override implicit val mTag: ClassTag[FileContent[ApplicationPdf.type]] =
    implicitly[ClassTag[FileContent[ApplicationPdf.type]]]

  override private[bridges] def inputParser: Parser[ApplicationPdf.type, M] =
    PdfParser
  override private[bridges] def outputRenderer: Renderer[M, ImagePng.type] =
    PngRenderer

  /** Simple parser that wraps the PDF bytes for processing.
    */
  private object PdfParser extends Parser[ApplicationPdf.type, M] {
    override def parse(input: FileContent[ApplicationPdf.type]): M =
      input
  }

  /** Renderer that converts PDF to PNG using Apache PDFBox.
    * This implementation defaults to first page only.
    */
  private object PngRenderer extends Renderer[M, ImagePng.type] {
    override def render(model: M): FileContent[ImagePng.type] = {
      Try {
        renderPdfPage(model.data, 0, "png", 300)
      } match {
        case Success(bytes) => FileContent[ImagePng.type](bytes, ImagePng)
        case Failure(e) =>
          throw RendererError(
            s"Failed to render PDF as PNG: ${e.getMessage}",
            Some(e)
          )
      }
    }

    // Default to 300 DPI for good quality without excessive size
    private def renderPdfPage(
        pdfBytes: Array[Byte],
        pageIndex: Int,
        format: String,
        dpi: Int
    ): Array[Byte] = {
      val document = PDDocument.load(pdfBytes)
      try {
        val renderer = new PDFRenderer(document)
        // Render the specified page at the given DPI
        val image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB)

        // Convert the rendered image to bytes
        val baos = new ByteArrayOutputStream()
        ImageIO.write(image, format, baos)
        baos.toByteArray
      } finally {
        document.close()
      }
    }
  }
}

/** PDF to JPEG conversion bridge with quality control.
  */
object PdfToJpegBridge
    extends SimpleBridge[ApplicationPdf.type, ImageJpeg.type] {
  override implicit val mTag: ClassTag[FileContent[ApplicationPdf.type]] =
    implicitly[ClassTag[FileContent[ApplicationPdf.type]]]

  override private[bridges] def inputParser: Parser[ApplicationPdf.type, M] =
    PdfParser
  override private[bridges] def outputRenderer: Renderer[M, ImageJpeg.type] =
    JpegRenderer

  /** Simple parser that wraps the PDF bytes for processing.
    */
  private object PdfParser extends Parser[ApplicationPdf.type, M] {
    override def parse(input: FileContent[ApplicationPdf.type]): M =
      input
  }

  /** Renderer that converts PDF to JPEG using Apache PDFBox.
    */
  private object JpegRenderer extends Renderer[M, ImageJpeg.type] {
    override def render(model: M): FileContent[ImageJpeg.type] = {
      Try {
        renderPdfPage(model.data, 0, 300, 0.85f)
      } match {
        case Success(bytes) => FileContent[ImageJpeg.type](bytes, ImageJpeg)
        case Failure(e) =>
          throw RendererError(
            s"Failed to render PDF as JPEG: ${e.getMessage}",
            Some(e)
          )
      }
    }

    // Default to 300 DPI and 85% quality for JPEG
    private def renderPdfPage(
        pdfBytes: Array[Byte],
        pageIndex: Int,
        dpi: Int,
        quality: Float
    ): Array[Byte] = {
      val document = PDDocument.load(pdfBytes)
      try {
        val renderer = new PDFRenderer(document)
        // Render the specified page at the given DPI
        val image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB)

        // Convert the rendered image to JPEG with quality control
        val baos = new ByteArrayOutputStream()
        val outputStream = new MemoryCacheImageOutputStream(baos)

        val jpegWriter = ImageIO.getImageWritersByFormatName("jpeg").next()
        jpegWriter.setOutput(outputStream)

        val params = jpegWriter.getDefaultWriteParam()
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
        params.setCompressionQuality(
          quality
        ) // Between 0 and 1, where 1 is highest quality

        jpegWriter.write(null, new IIOImage(image, null, null), params)
        jpegWriter.dispose()
        outputStream.close()

        baos.toByteArray
      } finally {
        document.close()
      }
    }
  }
}

/** Utility object for rendering PDFs with size constraints.
  * This is used by the PDF page splitter when converting to images.
  */
object PdfImageRenderer {

  /** Configuration for PDF to image rendering.
    *
    * @param maxWidthPixels Maximum width in pixels (will scale down if larger)
    * @param maxHeightPixels Maximum height in pixels (will scale down if larger)
    * @param maxSizeBytes Maximum size in bytes for the image (will reduce quality if larger)
    * @param dpi DPI for rendering (higher DPI = higher quality but larger images)
    * @param jpegQuality JPEG quality factor (0.0-1.0, where 1.0 is best quality but largest size)
    */
  case class RenderConfig(
      maxWidthPixels: Int = 2000,
      maxHeightPixels: Int = 2000,
      maxSizeBytes: Long = 1024 * 1024 * 5, // 5MB default limit
      dpi: Int = 300,
      jpegQuality: Float = 0.85f
  )

  /** Render a PDF page as a PNG image.
    *
    * @param pdfBytes PDF file content
    * @param pageIndex Page number to render (0-based)
    * @param config Rendering configuration
    * @return PNG image bytes
    */
  def renderPageAsPng(
      pdfBytes: Array[Byte],
      pageIndex: Int,
      config: RenderConfig
  ): Array[Byte] = {
    renderPageAsImage(pdfBytes, pageIndex, "png", config)
  }

  /** Render a PDF page as a JPEG image.
    *
    * @param pdfBytes PDF file content
    * @param pageIndex Page number to render (0-based)
    * @param config Rendering configuration
    * @return JPEG image bytes
    */
  def renderPageAsJpeg(
      pdfBytes: Array[Byte],
      pageIndex: Int,
      config: RenderConfig
  ): Array[Byte] = {
    renderPageAsImage(pdfBytes, pageIndex, "jpeg", config)
  }

  /** Internal method to render a PDF page as an image with constraints.
    */
  private def renderPageAsImage(
      pdfBytes: Array[Byte],
      pageIndex: Int,
      format: String,
      config: RenderConfig
  ): Array[Byte] = {
    val document = PDDocument.load(pdfBytes)
    try {
      val renderer = new PDFRenderer(document)

      // First render at the requested DPI
      var image =
        renderer.renderImageWithDPI(pageIndex, config.dpi, ImageType.RGB)

      // Scale down if dimensions exceed max
      image =
        scaleImageIfNeeded(image, config.maxWidthPixels, config.maxHeightPixels)

      // Convert to bytes with appropriate format and compression
      if (format.equalsIgnoreCase("jpeg")) {
        renderJpeg(image, config.jpegQuality, config.maxSizeBytes)
      } else {
        renderPng(image, config.maxSizeBytes)
      }
    } finally {
      document.close()
    }
  }

  /** Scale down an image if it exceeds max dimensions.
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

    // Create scaled instance
    val scaledImage =
      new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
    val g = scaledImage.createGraphics()
    g.drawImage(image, 0, 0, newWidth, newHeight, null)
    g.dispose()

    scaledImage
  }

  /** Render a BufferedImage as JPEG with quality adjustment.
    */
  private def renderJpeg(
      image: BufferedImage,
      initialQuality: Float,
      maxSizeBytes: Long
  ): Array[Byte] = {
    var quality = initialQuality
    var imageBytes: Array[Byte] = null
    var attempts = 0

    // Try up to 5 times with decreasing quality if size exceeds limit
    while (
      (imageBytes == null || imageBytes.length > maxSizeBytes) && attempts < 5
    ) {
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

      imageBytes = baos.toByteArray()

      // If still too large, reduce quality for next attempt
      if (imageBytes.length > maxSizeBytes && attempts < 4) {
        quality = quality * 0.8f // Reduce quality by 20% each time
      }

      attempts += 1
    }

    imageBytes
  }

  /** Render a BufferedImage as PNG, potentially with scaling.
    */
  private def renderPng(
      image: BufferedImage,
      maxSizeBytes: Long
  ): Array[Byte] = {
    var currentImage = image
    var imageBytes: Array[Byte] = null
    var attempts = 0

    // Try up to 3 times with decreasing size if size exceeds limit
    while (
      (imageBytes == null || imageBytes.length > maxSizeBytes) && attempts < 3
    ) {
      val baos = new ByteArrayOutputStream()
      ImageIO.write(currentImage, "png", baos)
      imageBytes = baos.toByteArray()

      // If still too large, scale down image by 25% for next attempt
      if (imageBytes.length > maxSizeBytes && attempts < 2) {
        val newWidth = (currentImage.getWidth * 0.75).toInt
        val newHeight = (currentImage.getHeight * 0.75).toInt

        val scaledImage =
          new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g = scaledImage.createGraphics()
        g.drawImage(currentImage, 0, 0, newWidth, newHeight, null)
        g.dispose()

        currentImage = scaledImage
      }

      attempts += 1
    }

    imageBytes
  }
}
