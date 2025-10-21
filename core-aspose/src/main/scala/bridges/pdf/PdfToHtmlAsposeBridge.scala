package com.tjclp.xlcr
package bridges
package pdf

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.util.Using

import com.aspose.pdf.{ Document => PdfDocument, HtmlSaveOptions }
import org.slf4j.LoggerFactory

import models.FileContent
import renderers.{ Renderer, SimpleRenderer }
import types.MimeType.{ ApplicationPdf, TextHtml }
import utils.aspose.AsposeLicense
import utils.resource.ResourceWrappers._

/**
 * Bridge that converts PDF documents to HTML using Aspose.PDF.
 *
 * This bridge preserves document structure better than direct PDF → PowerPoint conversion,
 * enabling improved editability when used in a PDF → HTML → PowerPoint workflow.
 *
 * Features:
 *   - Automatic handling of encrypted/restricted PDFs
 *   - Table structure preservation
 *   - Text extraction with formatting
 *   - Configurable HTML output options
 *   - Supports password-protected PDFs via BridgeContext
 *
 * Usage:
 * {{{
 *   // Direct conversion
 *   sbt "run -i document.pdf -o output.html"
 *
 *   // Two-stage workflow for better editability
 *   sbt "run -i document.pdf -o intermediate.html"
 *   sbt "run -i intermediate.html -o presentation.pptx"
 * }}}
 */
object PdfToHtmlAsposeBridge extends HighPrioritySimpleBridge[ApplicationPdf.type, TextHtml.type] {

  private val logger = LoggerFactory.getLogger(getClass)

  override private[bridges] def outputRenderer: Renderer[M, TextHtml.type] =
    PdfToHtmlAsposeRenderer

  /**
   * Helper method to handle encrypted or restricted PDFs. This method attempts to decrypt PDFs
   * with user-level restrictions (copy:no, change:no) but does not handle password-protected
   * PDFs.
   *
   * Note: Password support will be added in future implementation (see password-protected-documents-roadmap.md)
   *
   * @param pdfData
   *   The original PDF data (possibly encrypted/restricted)
   * @return
   *   Unlocked PDF data ready for HTML conversion, or original data if no restrictions detected
   */
  private def handleEncryptedPdf(pdfData: Array[Byte]): Array[Byte] = {
    try {
      Using.Manager { use =>
        val inputStream = use(new ByteArrayInputStream(pdfData))
        val pdfDocument = new PdfDocument(inputStream)
        use(new DisposableWrapper(pdfDocument))

        // Check if PDF has restrictions (copy:no, change:no, etc.)
        val isEncrypted = pdfDocument.isEncrypted()

        if (isEncrypted) {
          logger.info("PDF has restrictions, creating unlocked copy for HTML conversion")

          // Create unlocked copy by re-saving without restrictions
          val outputStream = use(new ByteArrayOutputStream())
          pdfDocument.decrypt()
          pdfDocument.save(outputStream)
          val unlockedData = outputStream.toByteArray

          logger.debug(s"Created unlocked PDF copy: ${unlockedData.length} bytes")
          unlockedData
        } else {
          logger.debug("PDF has no restrictions, using original data")
          pdfData
        }
      }.get
    } catch {
      case ex: Exception =>
        // If decryption fails (e.g., password-protected), warn and try with original data
        logger.warn(
          s"Could not process PDF encryption for HTML conversion: ${ex.getMessage}. " +
            "If PDF is password-protected, password support will be added in future release."
        )
        pdfData
    }
  }

  /**
   * Renderer that performs PDF to HTML conversion via Aspose.PDF.
   */
  private object PdfToHtmlAsposeRenderer extends SimpleRenderer[M, TextHtml.type] {
    override def render(model: M): FileContent[TextHtml.type] =
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info("Converting PDF to HTML using Aspose.PDF")

        // Validate input
        if (model.data == null || model.data.isEmpty) {
          throw new IllegalArgumentException("PDF file data is null or empty")
        }

        // Handle encrypted PDFs
        val pdfData = handleEncryptedPdf(model.data)

        val htmlBytes = Using.Manager { use =>
          val inputStream = use(new ByteArrayInputStream(pdfData))
          val pdfDocument = new PdfDocument(inputStream)
          use(new DisposableWrapper(pdfDocument))

          // Validate PDF structure
          if (pdfDocument.getPages == null || pdfDocument.getPages.size() == 0) {
            throw new IllegalStateException("PDF has no pages")
          }

          val pageCount = pdfDocument.getPages.size()
          logger.debug(s"Processing PDF with $pageCount pages for HTML conversion")

          // Configure HTML save options for optimal structure preservation
          val htmlOptions = new HtmlSaveOptions()

          // Enable flowing layout for better editability (vs fixed positioning for fidelity)
          htmlOptions.setFixedLayout(false)

          // Single HTML file output (vs. split into multiple pages)
          htmlOptions.setSplitIntoPages(false)

          // Compress SVG graphics to reduce file size
          // htmlOptions.setCompressSvgGraphicsIfAny(true)

          // Export all pages
          htmlOptions.setExplicitListOfSavedPages(null)

          // Font handling - embed fonts as WOFF for best compatibility
          htmlOptions.setFontSavingMode(
            com.aspose.pdf.HtmlSaveOptions.FontSavingModes.AlwaysSaveAsWOFF
          )

          // Resource handling - embed everything into single HTML file
          htmlOptions.setPartsEmbeddingMode(
            com.aspose.pdf.HtmlSaveOptions.PartsEmbeddingModes.EmbedAllIntoHtml
          )

          // Keep text as text, not images (critical for editability)
          htmlOptions.setRenderTextAsImage(false)

          // Preserve z-ordering for correct layering
          htmlOptions.setUseZOrder(true)

          // Save to HTML
          val outputStream = use(new ByteArrayOutputStream())
          pdfDocument.save(outputStream, htmlOptions)
          outputStream.toByteArray
        }.get

        logger.info(
          s"Successfully converted PDF to HTML, output size = ${htmlBytes.length} bytes"
        )

        FileContent[TextHtml.type](htmlBytes, TextHtml)
      } catch {
        case ex: RendererError =>
          // Re-throw renderer errors without wrapping
          throw ex
        case e: IllegalArgumentException =>
          logger.error(s"Invalid PDF input: ${e.getMessage}", e)
          throw RendererError(s"Invalid PDF file: ${e.getMessage}", Some(e))
        case e: IllegalStateException =>
          logger.error(s"Invalid PDF state: ${e.getMessage}", e)
          throw RendererError(s"Corrupted or empty PDF: ${e.getMessage}", Some(e))
        case ex: Exception =>
          logger.error("Error during PDF → HTML conversion", ex)
          throw RendererError(
            s"PDF to HTML conversion failed: ${Option(ex.getMessage).getOrElse(ex.getClass.getSimpleName)}",
            Some(ex)
          )
      }
  }
}
