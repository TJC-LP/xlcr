package com.tjclp.xlcr
package bridges.excel

import bridges.MergeableSymmetricBridge
import models.excel.SheetsData
import parsers.excel.SheetsDataExcelParser
import renderers.excel.SheetsDataExcelRenderer
import types.MimeType

import scala.reflect.ClassTag

/**
 * ExcelBridge can parse XLSX bytes into a List[SheetData] and render them back to XLSX.
 */
object SheetsDataExcelBridge extends MergeableSymmetricBridge[
  SheetsData,
  MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
] {
  implicit val mTag: ClassTag[SheetsData] = implicitly[ClassTag[SheetsData]]
  implicit val tTag: ClassTag[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] = 
    implicitly[ClassTag[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]]
  implicit val iTag: ClassTag[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] = 
    implicitly[ClassTag[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]]
  implicit val oTag: ClassTag[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] = 
    implicitly[ClassTag[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]]
  
  override protected def parser = new SheetsDataExcelParser()

  override protected def renderer = new SheetsDataExcelRenderer()
}