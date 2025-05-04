package com.tjclp.xlcr
package bridges.word

import types.MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument

/** Bridge that converts DOCX (Word Open XML) documents to PDF using Aspose.Words.
  */
object WordDocxToPdfAsposeBridge
    extends WordToPdfAsposeBridgeImpl[
      ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type
    ]
