package com.tjclp.xlcr
package parsers.excel

import models.Content
import parsers.Parser
import types.MimeType

import java.nio.file.Path
import scala.util.Try

/**
 * Parser implementation that converts JSON files (containing SheetData)
 * into Excel (.xlsx) files using JsonToExcelWriter.
 */
object JsonToExcelParser extends Parser:
  def extractContent(input: Path): Try[Content] = Try:
    // Create a temporary file for the Excel output
    val tempOutput = java.nio.file.Files.createTempFile("xlcr_output", ".xlsx")

    // Use JsonToExcelWriter to convert the JSON to Excel
    JsonToExcelWriter.jsonToExcelFile(input, tempOutput) match
      case failure@scala.util.Failure(ex) =>
        tempOutput.toFile.delete() // Clean up the temp file
        throw ex
      case scala.util.Success(_) =>
        // Read the generated Excel file into a byte array
        val bytes = java.nio.file.Files.readAllBytes(tempOutput)
        tempOutput.toFile.delete() // Clean up the temp file

        Content(
          data = bytes,
          contentType = outputType.mimeType,
          metadata = Map(
            "Format" -> "Excel",
            "Parser" -> "JsonToExcelParser"
          )
        )

  def outputType: MimeType = MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet

  override def supportedInputTypes: Set[MimeType] = Set(
    MimeType.ApplicationJson
  )