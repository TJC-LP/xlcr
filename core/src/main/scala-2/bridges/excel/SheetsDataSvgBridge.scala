package com.tjclp.xlcr
package bridges.excel

import bridges.SymmetricBridge
import models.excel.SheetsData
import parsers.excel.SheetsDataParser
import renderers.excel.SheetsDataSvgRenderer
import types.MimeType.ImageSvgXml

import scala.reflect.ClassTag

/**
 * ExcelSvgOutputBridge produces an SVG representation of SheetsData.
 * This replaces ExcelSvgParser logic.
 */
object SheetsDataSvgBridge extends SymmetricBridge[
  SheetsData,
  ImageSvgXml.type
] {
  implicit val mTag: ClassTag[SheetsData] = implicitly[ClassTag[SheetsData]]
  implicit val tTag: ClassTag[ImageSvgXml.type] = implicitly[ClassTag[ImageSvgXml.type]]
  
  override protected def renderer = new SheetsDataSvgRenderer()

  override protected def parser: SheetsDataParser[ImageSvgXml.type] = throw new Exception("SVG parser not yet defined")
}