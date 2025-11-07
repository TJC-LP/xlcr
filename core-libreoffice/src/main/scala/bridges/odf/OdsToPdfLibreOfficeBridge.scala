package com.tjclp.xlcr
package bridges.odf

import types.MimeType.ApplicationVndOasisOpendocumentSpreadsheet

/**
 * Bridge that converts ODS (OpenDocument Spreadsheet) to PDF using LibreOffice.
 *
 * ODS is LibreOffice Calc's native format, so this bridge provides excellent compatibility.
 * Priority: DEFAULT (fallback to Aspose if available)
 */
object OdsToPdfLibreOfficeBridge
    extends OdfToPdfLibreOfficeBridgeImpl[ApplicationVndOasisOpendocumentSpreadsheet.type]
