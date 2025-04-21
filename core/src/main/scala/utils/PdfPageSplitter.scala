package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType
import bridges.image.PdfImageRenderer
import bridges.image.PdfImageRenderer.RenderConfig

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.Loader
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class PdfPageSplitter extends DocumentSplitter[MimeType.ApplicationPdf.type] {
  private val logger = LoggerFactory.getLogger(getClass)

  override def split(
      content: FileContent[MimeType.ApplicationPdf.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    if (cfg.strategy != SplitStrategy.Page)
      return Seq(DocChunk(content, "document", 0, 1))
      
    // Determine output format - use PDF by default
    val outputFormat = cfg.outputFormat.getOrElse("pdf").toLowerCase
    
    // Create render config for images if needed
    val renderCfg = RenderConfig(
      maxWidthPixels = cfg.maxImageWidth,
      maxHeightPixels = cfg.maxImageHeight,
      maxSizeBytes = cfg.maxImageSizeBytes,
      dpi = cfg.imageDpi,
      jpegQuality = cfg.jpegQuality
    )

    val original = Loader.loadPDF(content.data)
    try {
      val total = original.getNumberOfPages
      
      (0 until total).map { idx =>
        outputFormat match {
          case "png" => 
            // Render as PNG
            logger.info(s"Rendering PDF page ${idx + 1} of $total as PNG")
            val imageBytes = PdfImageRenderer.renderPageAsPng(content.data, idx, renderCfg)
            val fc = FileContent(imageBytes, MimeType.ImagePng)
            DocChunk(fc, s"Page ${idx + 1}", idx, total)
            
          case "jpg" | "jpeg" =>
            // Render as JPEG
            logger.info(s"Rendering PDF page ${idx + 1} of $total as JPEG")
            val imageBytes = PdfImageRenderer.renderPageAsJpeg(content.data, idx, renderCfg)
            val fc = FileContent(imageBytes, MimeType.ImageJpeg)
            DocChunk(fc, s"Page ${idx + 1}", idx, total)
            
          case _ =>
            // Default: Extract as PDF (one page per PDF)
            val chunkDoc = new PDDocument()
            try {
              chunkDoc.addPage(original.getPage(idx))
              val baos = new ByteArrayOutputStream()
              chunkDoc.save(baos)
              val fc = FileContent(baos.toByteArray, MimeType.ApplicationPdf)
              DocChunk(fc, s"Page ${idx + 1}", idx, total)
            } finally chunkDoc.close()
        }
      }
    } finally original.close()
  }
}
