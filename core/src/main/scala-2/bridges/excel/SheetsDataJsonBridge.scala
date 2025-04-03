package com.tjclp.xlcr
package bridges.excel

import bridges.{Bridge, MergeableSymmetricBridge}
import models.excel.SheetsData
import parsers.excel.SheetsDataJsonParser
import renderers.excel.SheetsDataJsonRenderer
import types.MimeType
import types.MimeType.ApplicationJson

import scala.reflect.ClassTag

/**
 * JsonExcelInputBridge parses JSON -> SheetsData and also renders SheetsData -> JSON.
 * This replaces JsonToExcelParser logic on input side,
 * and partially duplicates ExcelJsonOutputBridge logic for the output side.
 */
object SheetsDataJsonBridge extends MergeableSymmetricBridge[SheetsData, ApplicationJson.type] {
  implicit val mTag: ClassTag[SheetsData] = implicitly[ClassTag[SheetsData]]
  implicit val tTag: ClassTag[ApplicationJson.type] = implicitly[ClassTag[ApplicationJson.type]]
  
  override protected def parser = new SheetsDataJsonParser()

  override protected def renderer = new SheetsDataJsonRenderer()
}