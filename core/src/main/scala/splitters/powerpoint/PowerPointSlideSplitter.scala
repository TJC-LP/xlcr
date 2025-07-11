package com.tjclp.xlcr
package splitters
package powerpoint

import java.io.{ File, FileInputStream, FileOutputStream }

import scala.jdk.CollectionConverters._
import scala.util.{ Try, Using }

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.slf4j.LoggerFactory

import models.FileContent
import types.MimeType
import utils.resource.ResourceWrappers._

trait PowerPointSlideSplitter[T <: MimeType] extends DocumentSplitter[T]
    with SplitFailureHandler {

  override protected val logger: org.slf4j.Logger = LoggerFactory.getLogger(getClass)

  override def split(
    content: FileContent[T],
    cfg: SplitConfig
  ): Seq[DocChunk[T]] = {

    // Check for valid strategy
    if (!cfg.hasStrategy(SplitStrategy.Slide)) {
      return handleInvalidStrategy(
        content,
        cfg,
        cfg.strategy.map(_.displayName).getOrElse("none"),
        Seq("slide")
      ).asInstanceOf[Seq[DocChunk[T]]]
    }

    // Wrap main logic with failure handling
    withFailureHandling(content, cfg) {
      val result = Using.Manager { use =>
        // To avoid ZipEntry issues, write the content to a temporary file first
        val tempFile = File.createTempFile("presentation", ".pptx")
        tempFile.deleteOnExit()
        use(autoCloseable(Try(tempFile.delete())))
        
        // Write the content to the temporary file
        val fos = use(new FileOutputStream(tempFile))
        fos.write(content.data)
        fos.close() // Close early so XMLSlideShow can read it
        
        // Open the presentation from the file instead of from a stream
        // This avoids the "Cannot retrieve data from Zip Entry" issue
        val src = use(new XMLSlideShow(new FileInputStream(tempFile)))

        val slides = src.getSlides.asScala.toList
        val total  = slides.size

        if (total == 0) {
          throw new EmptyDocumentException(
            content.mimeType.toString,
            "Presentation contains no slides"
          )
        }

          // Determine which slides to extract based on configuration
          val slidesToExtract = cfg.chunkRange match {
            case Some(range) =>
              // Filter to valid slide indices
              range.filter(i => i >= 0 && i < total)
            case None =>
              0 until total
          }

          // Process each slide - we'll use a separate temporary file for each destination
          val chunks = slidesToExtract.flatMap { idx =>
            val originalSlide = slides(idx)
            try {
              // Get slide title before attempting content import
              val title = Option(originalSlide.getTitle)
                .filter(_.nonEmpty)
                .getOrElse(s"Slide ${idx + 1}")

              val slideResult = Using.Manager { slideUse =>
                // Create temp file for destination slideshow
                val destFile = File.createTempFile(s"slide_${idx + 1}", ".pptx")
                destFile.deleteOnExit()
                slideUse(autoCloseable(Try(destFile.delete())))
                
                // Create a new presentation for this slide
                val dest = slideUse(new XMLSlideShow())
                
                // Import the content from the original slide
                dest.createSlide().importContent(originalSlide)
                
                // Write to the temp file
                val destFos = slideUse(new FileOutputStream(destFile))
                dest.write(destFos)
                destFos.close() // Close to allow reading
                
                // Read the file back into a byte array
                val destBytes = java.nio.file.Files.readAllBytes(destFile.toPath)
                
                // Create file content and chunk
                val fc = FileContent(destBytes, content.mimeType)
                Some(DocChunk(fc, title, idx, total))
              }
              slideResult.get
            } catch {
              case ex: Exception =>
                logger.error(s"Error processing slide ${idx + 1}: ${ex.getMessage}", ex)
                None
            }
          }

        if (chunks.isEmpty) {
          // If all slide imports failed, throw exception
          throw new CorruptedDocumentException(
            content.mimeType.toString,
            "Failed to extract any slides from presentation"
          )
        } else {
          chunks
        }
      }
      result.get
    }.asInstanceOf[Seq[DocChunk[T]]]
  }
}
