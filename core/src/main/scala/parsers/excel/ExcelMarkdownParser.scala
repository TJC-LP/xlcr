package com.tjclp.xlcr
package parsers.excel

import adapters.AdapterRegistry
import adapters.AdapterRegistry.implicits.*
import models.{Content, FileContent}
import types.MimeType
import types.MimeType.{TextMarkdown, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet}

import java.nio.file.{Files, Path}
import scala.util.Try

object ExcelMarkdownParser extends ExcelParser {
  override def outputType: MimeType = TextMarkdown

  override def extractContent(input: Path, output: Option[Path]): Try[Content] = Try {
    val inputBytes = Files.readAllBytes(input)
    val fileContent = FileContent[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type](inputBytes, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
    val result = AdapterRegistry.convert[
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      TextMarkdown.type
    ](fileContent)

    val bytes = result.data
    output.foreach(out => Files.write(out, bytes))

    Content(
      data = bytes,
      contentType = outputType.mimeType,
      metadata = Map("Converter" -> "ExcelMarkdownParser")
    )
  }
}