package com.tjclp.xlcr
package bridges.aspose.word

import bridges.SimpleBridge
import types.MimeType.{
  ApplicationPdf,
  ApplicationVndOpenXmlFormatsWordprocessingmlDocument
}

/** Bridge that converts DOCX (Word Open XML) documents to PDF using Aspose.Words.
  */
object WordDocxToPdfAsposeBridge
    extends SimpleBridge[
      ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type,
      ApplicationPdf.type
    ]
    with WordToPdfAsposeBridgeImpl[
      ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type
    ]
