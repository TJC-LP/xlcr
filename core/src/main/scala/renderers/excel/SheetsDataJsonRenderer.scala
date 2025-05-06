package com.tjclp.xlcr
package renderers.excel

import java.nio.charset.StandardCharsets

import scala.util.{ Failure, Success, Try }

import models.FileContent
import models.excel.SheetsData
import types.MimeType.ApplicationJson

/**
 * SheetsDataJsonRenderer produces JSON from SheetsData.
 */
class SheetsDataJsonRenderer
    extends SheetsDataSimpleRenderer[ApplicationJson.type] {
  override def render(model: SheetsData): FileContent[ApplicationJson.type] =
    Try {
      val sheets = model.sheets
      // Let's reuse the static toJsonMultiple from SheetData
      val jsonString =
        com.tjclp.xlcr.models.excel.SheetData.toJsonMultiple(sheets)
      FileContent[ApplicationJson.type](
        jsonString.getBytes(StandardCharsets.UTF_8),
        ApplicationJson
      )
    } match {
      case Failure(ex) =>
        throw new RendererError(
          s"Failed to render SheetsData to JSON: ${ex.getMessage}",
          Some(ex)
        )
      case Success(fc) => fc
    }
}
