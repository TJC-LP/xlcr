package com.tjclp.xlcr
package bridges.word

import bridges.SimpleBridge
import types.MimeType.{
  ApplicationPdf,
  ApplicationVndOpenXmlFormatsWordprocessingmlDocument
}

/** Bridge that converts DOCX (Word Open XML) documents to PDF using Aspose.Words.
  */
object WordDocxToPdfAsposeBridge
    extends WordToPdfAsposeBridgeImpl[
      ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type
    ]
