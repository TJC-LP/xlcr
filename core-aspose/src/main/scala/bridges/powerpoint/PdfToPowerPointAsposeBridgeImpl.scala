package com.tjclp.xlcr
package bridges
package powerpoint

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.util.Using

import com.aspose.pdf.{ Document => PdfDocument }
import com.aspose.slides.Presentation
import org.slf4j.LoggerFactory

import models.FileContent
import renderers.{ Renderer, SimpleRenderer }
import types.MimeType
import types.MimeType.ApplicationPdf
import utils.aspose.{ AsposeLicense, BridgeContext }
import utils.resource.ResourceWrappers._

/**
 * Common implementation for PDF to PowerPoint bridges that works with both Scala 2 and Scala 3.
 * This trait contains all the business logic for converting PDF documents to PowerPoint
 * presentations using Aspose.Slides.
 *
 * Each page in the PDF becomes a slide in the PowerPoint presentation.
 *
 * @tparam O
 *   The specific PowerPoint output MimeType (PPT or PPTX)
 */
trait PdfToPowerPointAsposeBridgeImpl[O <: MimeType]
    extends HighPrioritySimpleBridge[ApplicationPdf.type, O] {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * The SaveFormat to use for the output PowerPoint file (Ppt or Pptx)
   */
  protected def saveFormat: Int

  /**
   * The output MIME type for the PowerPoint file
   */
  protected def outputMimeType: O

  override private[bridges] def outputRenderer: Renderer[M, O] =
    PdfToPowerPointAsposeRenderer

  /**
   * Helper method to handle encrypted or restricted PDFs by creating an unlocked copy. This allows
   * Aspose.Slides to successfully import the PDF content.
   *
   * @param pdfData
   *   The original PDF data (possibly encrypted/restricted)
   * @return
   *   Unlocked PDF data ready for import, or original data if no restrictions detected
   */
  private def handleEncryptedPdf(pdfData: Array[Byte]): Array[Byte] =
    try
      Using.Manager { use =>
        val inputStream = use(new ByteArrayInputStream(pdfData))
        val pdfDocument = new PdfDocument(inputStream)
        use(new DisposableWrapper(pdfDocument))

        // Check if PDF is encrypted or has restrictions
        val isEncrypted     = pdfDocument.isEncrypted()
        val hasRestrictions = !pdfDocument.getPermissions.toString.contains("Print")

        if (isEncrypted || hasRestrictions) {
          logger.info(
            s"PDF has restrictions (encrypted: $isEncrypted), creating unlocked copy for conversion"
          )

          // Create unlocked copy by re-saving without restrictions
          val outputStream = use(new ByteArrayOutputStream())

          // Decrypt if encrypted
          if (isEncrypted) {
            pdfDocument.decrypt()
          }

          // Save without restrictions
          pdfDocument.save(outputStream)
          val unlockedData = outputStream.toByteArray

          logger.debug(s"Created unlocked PDF copy: ${unlockedData.length} bytes")
          unlockedData
        } else {
          // No restrictions, use original data
          logger.debug("PDF has no restrictions, using original data")
          pdfData
        }
      }.get
    catch {
      case ex: Exception =>
        // If we can't check/unlock, warn but try with original data
        logger.warn(
          s"Could not check PDF encryption status, will attempt direct import: ${ex.getMessage}"
        )
        pdfData
    }

  /**
   * Renderer that performs PDF to PowerPoint conversion via Aspose.Slides. This works for both PPT
   * and PPTX output formats.
   */
  private object PdfToPowerPointAsposeRenderer extends SimpleRenderer[M, O] {
    override def render(model: M): FileContent[O] =
      try {
        AsposeLicense.initializeIfNeeded()
        logger.info(
          s"Rendering PDF to ${saveFormat.toString} using Aspose.Slides."
        )

        // Validate input data
        if (model.data == null || model.data.isEmpty) {
          throw new IllegalArgumentException("PDF file data is null or empty")
        }

        // Handle encrypted or restricted PDFs by creating an unlocked copy
        val pdfData = handleEncryptedPdf(model.data)

        val powerPointBytes = Using.Manager { use =>
          val inputStream = use(new ByteArrayInputStream(pdfData))

          // Create a new empty presentation
          val presentation = new Presentation()
          use(new DisposableWrapper(presentation))

          // Remove the default blank slide that Aspose creates
          // Without this, the output would have an unwanted blank slide at the beginning
          if (presentation.getSlides.size() > 0) {
            presentation.getSlides.removeAt(0)
          }

          // Import PDF content into presentation slides
          // Each page in the PDF becomes one slide in the presentation
          presentation.getSlides.addFromPdf(inputStream)

          // Validate presentation structure
          if (presentation.getSlides == null) {
            throw new IllegalStateException(
              "Presentation slides collection is null after PDF import"
            )
          }

          val slideCount = presentation.getSlides.size()
          logger.debug(s"Created presentation with $slideCount slides from PDF")

          if (slideCount == 0) {
            logger.warn("PDF import resulted in a presentation with no slides")
          }

          // Remove unused master slides and layouts to create cleaner presentations
          // when --strip-masters flag is enabled. This helps ensure smoother round-trip
          // conversions by eliminating default templates that Aspose may have applied
          // during PDF import.
          val stripMasters = BridgeContext.get().stripMasters

          if (stripMasters) {
            try {
              // First remove unused layouts from each master
              var totalLayoutsRemoved = 0
              val masters             = presentation.getMasters
              for (i <- 0 until masters.size()) {
                val master        = masters.get_Item(i)
                val layoutsBefore = master.getLayoutSlides.size()
                master.getLayoutSlides.removeUnused()
                val layoutsAfter = master.getLayoutSlides.size()
                totalLayoutsRemoved += (layoutsBefore - layoutsAfter)
              }
              if (totalLayoutsRemoved > 0) {
                logger.info(
                  s"Removed $totalLayoutsRemoved unused layout slides (--strip-masters)"
                )
              }

              // Then remove unused master slides
              val mastersBeforeCleanup = presentation.getMasters.size()
              presentation.getMasters.removeUnused(true) // true = ignore preserve field
              val mastersAfterCleanup = presentation.getMasters.size()
              if (mastersBeforeCleanup > mastersAfterCleanup) {
                logger.info(
                  s"Removed ${mastersBeforeCleanup - mastersAfterCleanup} unused master slides (--strip-masters)"
                )
              }
            } catch {
              case ex: Exception =>
                // Don't fail the entire conversion if cleanup fails
                logger.warn(s"Failed to remove unused masters/layouts: ${ex.getMessage}")
            }
          }

          val outputStream = use(new ByteArrayOutputStream())

          // Save to the specified format (PPT or PPTX)
          presentation.save(outputStream, saveFormat)

          outputStream.toByteArray
        }.get

        logger.info(
          s"Successfully converted PDF to ${saveFormat.toString}, output size = ${powerPointBytes.length} bytes."
        )
        FileContent[O](powerPointBytes, outputMimeType)
      } catch {
        case e: NullPointerException =>
          logger.error(
            s"NullPointerException during PDF -> ${saveFormat.toString} conversion. This may be due to corrupted PDF content or unsupported PDF features.",
            e
          )
          throw RendererError(
            s"PDF to ${saveFormat.toString} conversion failed due to null reference: ${Option(e
                .getMessage).getOrElse("Unknown null pointer error")}",
            Some(e)
          )
        case e: IllegalArgumentException =>
          logger.error(
            s"Invalid input for PDF -> ${saveFormat.toString} conversion: ${e.getMessage}",
            e
          )
          throw RendererError(s"Invalid PDF file: ${e.getMessage}", Some(e))
        case e: IllegalStateException =>
          logger.error(s"Invalid presentation state after PDF import: ${e.getMessage}", e)
          throw RendererError(s"Corrupted PDF or import failure: ${e.getMessage}", Some(e))
        case ex: Exception =>
          logger.error(
            s"Unexpected error during PDF -> ${saveFormat.toString} conversion with Aspose.Slides.",
            ex
          )
          throw RendererError(
            s"PDF to ${saveFormat.toString} conversion failed: ${Option(ex.getMessage).getOrElse(ex.getClass.getSimpleName)}",
            Some(ex)
          )
      }
  }
}
