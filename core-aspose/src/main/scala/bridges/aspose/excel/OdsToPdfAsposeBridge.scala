package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.SimpleBridge
import types.MimeType.{
  ApplicationPdf,
  ApplicationVndOasisOpendocumentSpreadsheet
}

/** Bridge that converts ODS (OpenDocument Spreadsheet) files to PDF using Aspose.Cells.
  */
object OdsToPdfAsposeBridge
    extends SimpleBridge[
      ApplicationVndOasisOpendocumentSpreadsheet.type,
      ApplicationPdf.type
    ]
    with OdsToPdfAsposeBridgeImpl
