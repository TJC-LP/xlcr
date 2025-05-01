package com.tjclp.xlcr
package bridges.aspose.word

import bridges.SimpleBridge
import models.FileContent
import types.MimeType
import types.MimeType.{ApplicationMsWord, ApplicationPdf}

import com.aspose.words.Document
import com.aspose.words.SaveFormat

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.reflect.ClassTag

/**
 * Scala 2.12 implementation of WordToPdfAsposeBridge.
 * Extends the common implementation with Scala 2 specific syntax.
 */
object WordToPdfAsposeBridge extends SimpleBridge[ApplicationMsWord.type, ApplicationPdf.type] with WordToPdfAsposeBridgeImpl {
  // For Scala 2.12 compatibility, provide required ClassTags
  override implicit val mTag: ClassTag[M] = implicitly[ClassTag[M]]
  implicit val iTag: ClassTag[ApplicationMsWord.type] = implicitly[ClassTag[ApplicationMsWord.type]]
  implicit val oTag: ClassTag[ApplicationPdf.type] = implicitly[ClassTag[ApplicationPdf.type]]
  
  /**
   * Scala 2 specific implementation of the document conversion using Aspose
   */
  override protected def convertDocToPdf(inputStream: ByteArrayInputStream): ByteArrayOutputStream = {
    val asposeDoc = new Document(inputStream)
    val pdfOutput = new ByteArrayOutputStream()
    asposeDoc.save(pdfOutput, SaveFormat.PDF)
    pdfOutput
  }
}