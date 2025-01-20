package com.tjclp.xlcr
package parsers.excel

import adapters.AdapterRegistry
import adapters.AdapterRegistry.implicits.*
import models.{Content, FileContent}
import types.MimeType
import types.MimeType.{TextMarkdown, ApplicationVndOpenXmlFormatsSpreadsheetmlSheet}

import java.nio.file.{Files, Path}
import scala.util.Try

object MarkdownToExcelParser extends ExcelParser {
  override def outputType: MimeType = ApplicationVndOpenXmlFormatsSpreadsheetmlSheet

  override def supportedInputTypes: Set[MimeType] = Set(MimeType.TextMarkdown)

  override def priority: Int = 5

  override def extractContent(input: Path, output: Option[Path]): Try[Content] = Try {
    val inputBytes = Files.readAllBytes(input)
    val fileContent = FileContent[TextMarkdown.type](inputBytes, TextMarkdown)
    val result = AdapterRegistry.convert[
      TextMarkdown.type,
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
    ](fileContent)

    val bytes = result.data
    output.foreach(out => Files.write(out, bytes))

    Content(
      data = bytes,
      contentType = outputType.mimeType,
      metadata = Map("Converter" -> "MarkdownToExcelParser")
    )
  }
}