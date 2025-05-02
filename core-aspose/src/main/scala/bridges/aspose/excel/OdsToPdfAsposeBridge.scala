package com.tjclp.xlcr
package bridges.aspose.excel

import types.MimeType.ApplicationVndOasisOpendocumentSpreadsheet

/** Bridge that converts ODS (OpenDocument Spreadsheet) files to PDF using Aspose.Cells.
  */
object OdsToPdfAsposeBridge
    extends ExcelToPdfAsposeBridgeImpl[
      ApplicationVndOasisOpendocumentSpreadsheet.type
    ]
