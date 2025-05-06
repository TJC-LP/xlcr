package com.tjclp.xlcr
package parsers.excel

import java.nio.charset.StandardCharsets

import models.FileContent
import models.excel.{ SheetData, SheetsData }
import types.MimeType

class SheetsDataJsonParser
    extends SheetsDataSimpleParser[MimeType.ApplicationJson.type] {
  override def parse(
    input: FileContent[MimeType.ApplicationJson.type]
  ): SheetsData = {
    val jsonString = new String(input.data, StandardCharsets.UTF_8)
    SheetData.fromJsonMultiple(jsonString) match {
      case Left(err) =>
        throw new RuntimeException(
          s"Failed to parse SheetsData from JSON: ${err.getMessage}"
        )
      case Right(sheets) =>
        SheetsData(sheets)
    }
  }
}
