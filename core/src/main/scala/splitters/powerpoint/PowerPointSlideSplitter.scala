package com.tjclp.xlcr
package splitters
package powerpoint

import java.io.{ File, FileInputStream, FileOutputStream }

import scala.jdk.CollectionConverters._
import scala.util.Try

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.slf4j.LoggerFactory

import models.FileContent
import types.MimeType

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
      // To avoid ZipEntry issues, write the content to a temporary file first
      val tempFile = File.createTempFile("presentation", ".pptx")
      tempFile.deleteOnExit()

      try {
        // Write the content to the temporary file
        val fos = new FileOutputStream(tempFile)
        try {
          fos.write(content.data)
        } finally {
          fos.close()
        }

        // Open the presentation from the file instead of from a stream
        // This avoids the "Cannot retrieve data from Zip Entry" issue
        val src = new XMLSlideShow(new FileInputStream(tempFile))

        try {
          val slides = src.getSlides.asScala.toList
          val total  = slides.size

          if (total == 0) {
            src.close()
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

              // Create temp file for destination slideshow
              val destFile = File.createTempFile(s"slide_${idx + 1}", ".pptx")
              destFile.deleteOnExit()

              try {
                // Create a new presentation for this slide
                val dest = new XMLSlideShow()

                try {
                  // Import the content from the original slide
                  dest.createSlide().importContent(originalSlide)

                  // Write to the temp file
                  val destFos = new FileOutputStream(destFile)
                  try {
                    dest.write(destFos)
                  } finally {
                    destFos.close()
                  }

                  // Read the file back into a byte array
                  val destBytes =
                    java.nio.file.Files.readAllBytes(destFile.toPath)

                  // Create file content and chunk
                  val fc = FileContent(destBytes, content.mimeType)
                  Some(DocChunk(fc, title, idx, total))
                } finally
                  // Always close the destination
                  dest.close()
              } finally
                // Clean up the temp file
                Try(destFile.delete())
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
        } finally
          // Close source after all slides are processed
          Try(src.close())
      } finally
        // Clean up the main temp file
        Try(tempFile.delete())
    }.asInstanceOf[Seq[DocChunk[T]]]
  }
}
