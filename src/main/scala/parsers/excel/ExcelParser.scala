package com.tjclp.xlcr
package parsers.excel

import types.MimeType
import com.tjclp.xlcr.parsers.Parser

trait ExcelParser extends Parser:
  def supportedInputTypes: Set[MimeType] = Set(
    MimeType.ApplicationVndMsExcel,
    MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
  )

  override def priority: Int = 10 // Higher priority for specialized Excel handling
  