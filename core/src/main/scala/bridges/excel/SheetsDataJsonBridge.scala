package com.tjclp.xlcr
package bridges.excel

import bridges.{Bridge, SymmetricBridge}
import models.FileContent
import models.excel.{SheetData, SheetsData}
import types.MimeType
import types.MimeType.ApplicationJson

import io.circe.Error

import java.nio.charset.StandardCharsets

/**
 * JsonExcelInputBridge parses JSON -> SheetsData and also renders SheetsData -> JSON.
 * This replaces JsonToExcelParser logic on input side,
 * and partially duplicates ExcelJsonOutputBridge logic for the output side.
 */
object SheetsDataJsonBridge extends SymmetricBridge[
  SheetsData,
  MimeType.ApplicationJson.type
] {

  override def parse(input: FileContent[ApplicationJson.type]): SheetsData = {
    val jsonString = new String(input.data, StandardCharsets.UTF_8)
    SheetData.fromJsonMultiple(jsonString) match {
      case Left(err) =>
        throw new RuntimeException(s"Failed to parse SheetsData from JSON: ${err.getMessage}")
      case Right(sheets) =>
        SheetsData(sheets)
    }
  }

  override def render(model: SheetsData): FileContent[MimeType.ApplicationJson.type] = {
    // Use existing SheetData.toJsonMultiple
    val jsonString = SheetData.toJsonMultiple(model.sheets)
    FileContent(jsonString.getBytes("UTF-8"), MimeType.ApplicationJson)
  }
}