package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.aspose.HighPrioritySimpleBridge
import types.MimeType.{ApplicationPdf, ApplicationVndMsExcel}

/** Scala 2.12 implementation of ExcelXlsToPdfAsposeBridge for XLS files.
  * Extends the specific implementation for legacy XLS files.
  */
object ExcelXlsToPdfAsposeBridge
    extends HighPrioritySimpleBridge[
      ApplicationVndMsExcel.type,
      ApplicationPdf.type
    ]
    with ExcelXlsToPdfAsposeBridgeImpl
