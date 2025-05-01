package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.SimpleBridge
import types.MimeType.{ApplicationPdf, ApplicationVndMsExcel}

/** Alias for ExcelXlsToPdfAsposeBridge for backward compatibility.
  * This bridge converts legacy Excel (XLS) files to PDF.
  */
object ExcelLegacyToPdfAsposeBridge
    extends SimpleBridge[ApplicationVndMsExcel.type, ApplicationPdf.type]
    with ExcelXlsToPdfAsposeBridgeImpl
