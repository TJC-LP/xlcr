package com.tjclp.xlcr
package bridges.excel

import bridges.{Bridge, MergeableBridge, MergeableSymmetricBridge}
import models.FileContent
import models.excel.{SheetData, SheetsData}
import parsers.excel.SheetsDataJsonParser
import renderers.Renderer
import renderers.excel.SheetsDataJsonRenderer
import types.MimeType
import types.MimeType.ApplicationJson

import io.circe.Error

import java.nio.charset.StandardCharsets

/**
 * JsonExcelInputBridge parses JSON -> SheetsData and also renders SheetsData -> JSON.
 * This replaces JsonToExcelParser logic on input side,
 * and partially duplicates ExcelJsonOutputBridge logic for the output side.
 */
object SheetsDataJsonBridge extends MergeableSymmetricBridge[SheetsData, ApplicationJson.type] {
  override protected def parser = new SheetsDataJsonParser()

  override protected def renderer = new SheetsDataJsonRenderer()
}