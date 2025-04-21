package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.Loader

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class PdfPageSplitter extends DocumentSplitter[MimeType.ApplicationPdf.type] {

  override def split(
      content: FileContent[MimeType.ApplicationPdf.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    if (cfg.strategy != SplitStrategy.Page)
      return Seq(DocChunk(content, "document", 0, 1))

    val original = Loader.loadPDF(content.data)
    try {
      val total = original.getNumberOfPages
      (0 until total).map { idx =>
        val chunkDoc = new PDDocument()
        try {
          chunkDoc.addPage(original.getPage(idx))
          val baos = new ByteArrayOutputStream()
          chunkDoc.save(baos)
          val fc = FileContent(baos.toByteArray, MimeType.ApplicationPdf)
          DocChunk(fc, s"Page ${idx + 1}", idx, total)
        } finally chunkDoc.close()
      }
    } finally original.close()
  }
}
