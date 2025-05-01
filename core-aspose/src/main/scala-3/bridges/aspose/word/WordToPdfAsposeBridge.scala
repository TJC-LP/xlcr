package com.tjclp.xlcr
package bridges.aspose.word

import bridges.SimpleBridge
import models.FileContent
import types.MimeType
import types.MimeType.{ApplicationMsWord, ApplicationPdf}

import com.aspose.words.{Document as AsposeDocument, SaveFormat}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/**
 * Scala 3 implementation of WordToPdfAsposeBridge.
 * Extends the common implementation with Scala 3 specific syntax.
 */
object WordToPdfAsposeBridge extends SimpleBridge[ApplicationMsWord.type, ApplicationPdf.type] with WordToPdfAsposeBridgeImpl {
  /**
   * Scala 3 specific implementation of the document conversion using Aspose
   */
  override protected def convertDocToPdf(inputStream: ByteArrayInputStream): ByteArrayOutputStream = {
    val asposeDoc = new AsposeDocument(inputStream)
    val pdfOutput = new ByteArrayOutputStream()
    asposeDoc.save(pdfOutput, SaveFormat.PDF)
    pdfOutput
  }
}