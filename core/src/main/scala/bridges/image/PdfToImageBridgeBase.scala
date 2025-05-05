package com.tjclp.xlcr
package bridges.image

import bridges.{BridgeConfig, SimpleBridge}
import models.FileContent
import types.MimeType
import types.MimeType.ApplicationPdf

import org.slf4j.LoggerFactory
import scala.reflect.ClassTag

/** Base trait for PDF to image bridges that provides common auto-tuning functionality.
  * 
  * This trait implements the logic for automatic tuning of rendering parameters to
  * meet size constraints, while delegating the actual rendering to concrete implementations.
  * 
  * @tparam O The output MIME type (should be ImagePng or ImageJpeg)
  */
trait PdfToImageBridgeBase[O <: MimeType] extends SimpleBridge[ApplicationPdf.type, O] {
  // ClassTag needed for Scala 3 compatibility
  implicit val classTag: ClassTag[O]
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  /** The target MIME type this bridge produces. Must be implemented by concrete classes. */
  val targetMime: O
  
  /** Renders a specific page from a PDF to the target image format.
    * 
    * This method must be implemented by concrete classes that provide the actual
    * rendering implementation (e.g., PDFBox, Aspose).
    * 
    * @param pdfBytes The raw PDF bytes to render
    * @param pageIdx The page index to render (0-based)
    * @param cfg The rendering configuration
    * @return The rendered image bytes
    */
  protected def renderPage(
    pdfBytes: Array[Byte],
    pageIdx: Int, 
    cfg: ImageRenderConfig
  ): Array[Byte]

  /** Converts a PDF to an image with auto-tuning to meet size constraints.
    * 
    * This implementation handles the common auto-tuning logic while delegating
    * the actual rendering to the concrete implementation of renderPage.
    * 
    * @param input The input PDF file content
    * @param cfgOpt Optional bridge configuration 
    * @return The rendered image as file content
    */
  final override def convert(
    input: FileContent[ApplicationPdf.type],
    cfgOpt: Option[BridgeConfig] = None
  ): FileContent[O] = {
    // Extract config or use defaults
    val cfg = cfgOpt.collect { case c: ImageRenderConfig => c }
      .getOrElse(ImageRenderConfig(targetMime))
    
    // Default to page 0 if not specified
    val pageIdx = cfgOpt.collect { case c: PageIndexConfig => c.pageIndex }.getOrElse(0)
    
    // Apply auto-tuning and render
    val tuned = autoTune(input.data, cfg, pageIdx)
    
    // Return as the target MIME type
    FileContent[O](tuned, targetMime)
  }

  /** Auto-tunes rendering parameters to meet size constraints.
    * 
    * This method will progressively reduce quality/DPI until the rendered
    * image meets the size constraints or the maximum attempts are reached.
    * 
    * @param pdfBytes The PDF bytes to render
    * @param cfg The initial rendering configuration
    * @param pageIdx The page index to render (0-based)
    * @return The rendered image bytes after tuning
    */
  private def autoTune(
    pdfBytes: Array[Byte], 
    cfg: ImageRenderConfig,
    pageIdx: Int
  ): Array[Byte] = {
    var curCfg = cfg
    var attempt = 0
    var imgBytes = renderPage(pdfBytes, pageIdx, curCfg)

    // If auto-tuning is enabled and the image is too large, try again with reduced quality
    while (cfg.autoTune && 
           imgBytes.length > curCfg.maxBytes && 
           attempt < curCfg.maxAttempts) {
      
      attempt += 1
      
      // Reduce DPI and quality for next attempt
      curCfg = curCfg.copy(
        initialDpi = (curCfg.initialDpi * 0.9).toInt,
        initialQuality = curCfg.initialQuality * 0.85f
      )
      
      logger.debug(
        s"Image size (${imgBytes.length} bytes) exceeds limit (${curCfg.maxBytes} bytes). " +
        s"Auto-tuning attempt $attempt: DPI=${curCfg.initialDpi}, quality=${curCfg.initialQuality}"
      )
      
      imgBytes = renderPage(pdfBytes, pageIdx, curCfg)
    }
    
    if (imgBytes.length > curCfg.maxBytes && cfg.autoTune) {
      logger.warn(
        s"Image size (${imgBytes.length} bytes) still exceeds limit (${curCfg.maxBytes} bytes) " +
        s"after ${cfg.maxAttempts} auto-tuning attempts"
      )
    }
    
    imgBytes
  }
}

/** Configuration to specify which page to render from a multi-page document. */
final case class PageIndexConfig(pageIndex: Int) extends BridgeConfig