package com.tjclp.xlcr
package bridges.excel

import bridges.MergeableSymmetricBridge
import models.excel.SheetsData
import parsers.excel.SheetsDataJsonParser
import renderers.excel.SheetsDataJsonRenderer
import types.MimeType.ApplicationJson

/** JsonExcelInputBridge parses JSON -> SheetsData and also renders SheetsData -> JSON.
  * This replaces JsonToExcelParser logic on input side,
  * and partially duplicates ExcelJsonOutputBridge logic for the output side.
  */
object SheetsDataJsonBridge
    extends MergeableSymmetricBridge[SheetsData, ApplicationJson.type] {
  override protected def parser = new SheetsDataJsonParser()

  override protected def renderer = new SheetsDataJsonRenderer()
}
