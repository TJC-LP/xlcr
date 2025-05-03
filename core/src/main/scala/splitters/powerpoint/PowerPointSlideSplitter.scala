package com.tjclp.xlcr
package splitters
package powerpoint

import models.FileContent
import types.MimeType

import org.apache.poi.xslf.usermodel.XMLSlideShow

import java.io.{File, FileInputStream, FileOutputStream}
import scala.jdk.CollectionConverters._
import scala.util.Try

trait PowerPointSlideSplitter[T <: MimeType] extends DocumentSplitter[T] {

  override def split(
      content: FileContent[T],
      cfg: SplitConfig
  ): Seq[DocChunk[T]] = {

    if (!cfg.hasStrategy(SplitStrategy.Slide))
      return Seq(DocChunk(content, "presentation", 0, 1))

    // To avoid ZipEntry issues, write the content to a temporary file first
    val tempFile = File.createTempFile("presentation", ".pptx")
    tempFile.deleteOnExit()

    try {
      // Write the content to the temporary file
      val fos = new FileOutputStream(tempFile)
      fos.write(content.data)
      fos.close()

      // Open the presentation from the file instead of from a stream
      // This avoids the "Cannot retrieve data from Zip Entry" issue
      val src = new XMLSlideShow(new FileInputStream(tempFile))

      try {
        val slides = src.getSlides.asScala.toList
        val total = slides.size

        // Process each slide - we'll use a separate temporary file for each destination
        val chunks = slides.zipWithIndex.flatMap { case (originalSlide, idx) =>
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
                dest.write(destFos)
                destFos.close()

                // Read the file back into a byte array
                val destBytes =
                  java.nio.file.Files.readAllBytes(destFile.toPath)

                // Create file content and chunk
                val fc = FileContent(destBytes, content.mimeType)
                Some(DocChunk(fc, title, idx, total))
              } finally {
                // Always close the destination
                dest.close()
              }
            } finally {
              // Clean up the temp file
              Try(destFile.delete())
            }
          } catch {
            case ex: Exception =>
              println(s"Error processing slide ${idx + 1}: ${ex.getMessage}")
              ex.printStackTrace() // Added for debugging
              None
          }
        }

        if (chunks.isEmpty) {
          // If all slide imports failed, return the original content
          println("All slide imports failed - returning original content")
          Seq(DocChunk(content, "presentation (failed to split)", 0, 1))
        } else {
          chunks
        }
      } finally {
        // Close source after all slides are processed
        Try(src.close())
      }
    } finally {
      // Clean up the main temp file
      Try(tempFile.delete())
    }
  }
}
