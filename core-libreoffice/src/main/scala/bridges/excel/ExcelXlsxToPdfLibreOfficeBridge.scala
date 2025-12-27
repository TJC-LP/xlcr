package com.tjclp.xlcr
package bridges.excel

import types.MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet

/**
 * Bridge that converts Excel .xlsx documents to PDF using LibreOffice.
 *
 * Priority: DEFAULT (fallback to Aspose if available)
 */
object ExcelXlsxToPdfLibreOfficeBridge
    extends ExcelToPdfLibreOfficeBridgeImpl[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]
