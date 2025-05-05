package com.tjclp.xlcr
package bridges.image

import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType
import types.MimeType.{ApplicationPdf, ImageJpeg, ImagePng}
import utils.aspose.AsposeLicense

import com.aspose.pdf.{Document, SaveFormat}
import com.aspose.pdf.devices.{JpegDevice, PngDevice, Resolution}
import org.slf4j.LoggerFactory
import scala.reflect.ClassTag

import java.io.ByteArrayOutputStream

/** Base implementation for Aspose-based PDF to image conversion.
  * 
  * Uses Aspose.PDF to render PDF pages as images with support for both
  * PNG and JPEG output formats. This implementation provides higher quality
  * and better fidelity than the PDFBox-based one.
  * 
  * @tparam O The output image MIME type
  */
abstract class PdfAsposeImageBridge[O <: MimeType](implicit override val classTag: ClassTag[O]) extends PdfToImageBridgeBase[O] {
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  // Initialize Aspose license if available
  AsposeLicense.initializeIfNeeded()
  
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

  /** Renders a specific page from a PDF to an image using Aspose.
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
    // Load the document from bytes
    val document = new Document(pdfBytes)
    try {
      // Create an output stream for the image data
      val baos = new ByteArrayOutputStream()
      
      // Configure resolution
      val resolution = new Resolution(cfg.initialDpi)
      
      // Get the page to render (Aspose uses 1-based indexing)
      val pageNumber = pageIdx + 1
      
      // Use a try-finally block to ensure resources are properly cleaned up
      try {
        if (targetMime == ImageJpeg) {
          // Create JPEG device with quality setting
          val jpegDevice = new JpegDevice(resolution, (cfg.initialQuality * 100).toInt) // Aspose uses 0-100 scale as integer
          
          // Process the page to the output stream
          jpegDevice.process(document.getPages().get_Item(pageNumber), baos)
        } else {
          // Create PNG device
          val pngDevice = new PngDevice(resolution)
          
          // Process the page to the output stream
          pngDevice.process(document.getPages().get_Item(pageNumber), baos)
        }
        
        // Return the image bytes
        baos.toByteArray()
      } finally {
        // Close the output stream
        baos.close()
      }
    } finally {
      // Close the document to release resources
      document.close()
    }
  }
}

/** Concrete implementation of PDF to PNG conversion using Aspose.
  */
object PdfToPngAsposeBridge extends PdfAsposeImageBridge[ImagePng.type] {
  override val targetMime: ImagePng.type = ImagePng
}

/** Concrete implementation of PDF to JPEG conversion using Aspose.
  */
object PdfToJpegAsposeBridge extends PdfAsposeImageBridge[ImageJpeg.type] {
  override val targetMime: ImageJpeg.type = ImageJpeg
}