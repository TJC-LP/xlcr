package com.tjclp.xlcr
package renderers.spreadsheetllm

import models.FileContent
import models.spreadsheetllm.CompressedWorkbook
import renderers.Renderer
import types.MimeType

import io.circe._
import io.circe.syntax._
import org.slf4j.LoggerFactory

import scala.util.Try

/**
 * Renderer that converts a CompressedWorkbook model to a LLM-friendly JSON
 * representation with embedded markdown for formatting.
 */
class LLMJsonRenderer extends Renderer[CompressedWorkbook, MimeType.ApplicationJson.type]:
  private val logger = LoggerFactory.getLogger(getClass)
  
  /**
   * Render a CompressedWorkbook to LLM-optimized JSON.
   *
   * @param model The compressed workbook model
   * @return FileContent containing the JSON representation
   */
  override def render(model: CompressedWorkbook): FileContent[MimeType.ApplicationJson.type] =
    logger.info(s"Rendering CompressedWorkbook to LLM JSON format")
    
    // Convert the model to a JSON object
    val json = modelToJson(model)
    
    // Convert the JSON to bytes
    val jsonBytes = json.spaces2.getBytes("UTF-8")
    
    logger.info(s"Rendered ${jsonBytes.length} bytes of JSON")
    
    // Create and return the file content
    FileContent(jsonBytes, MimeType.ApplicationJson)
  
  /**
   * Convert the CompressedWorkbook model to a JSON object.
   *
   * @param workbook The CompressedWorkbook model
   * @return JSON representation
   */
  private def modelToJson(workbook: CompressedWorkbook): Json =
    // Create a JSON object for each sheet
    val sheetJsons = workbook.sheets.map { sheet =>
      val contentJson = sheet.content.map { case (key, locationsEither) =>
        val locationsJson = locationsEither match
          case Left(singleLocation) => Json.fromString(singleLocation)
          case Right(locationsList) => locationsList.asJson
        
        // Apply Markdown formatting to key where appropriate
        val formattedKey = applyMarkdownFormatting(key)
        (formattedKey, locationsJson)
      }
      
      // Create formula JSON if there are formulas
      val formulasJson = 
        if sheet.formulas.isEmpty then Json.Null
        else 
          // Format formula expressions with backticks in markdown
          val formattedFormulas = sheet.formulas.map { case (formula, cell) =>
            val markdownFormula = s"`$formula`"
            (markdownFormula, Json.fromString(cell))
          }
          Json.obj(formattedFormulas.toSeq: _*)
      
      // Create table JSON if there are tables
      val tablesJson =
        if sheet.tables.isEmpty then Json.arr()
        else
          sheet.tables.map { table =>
            Json.obj(
              "id" -> Json.fromString(table.id),
              "range" -> Json.fromString(table.range),
              "hasHeaders" -> Json.fromBoolean(table.hasHeaders),
              "headerRow" -> table.headerRow.fold(Json.Null)(row => Json.fromInt(row))
            )
          }.asJson
      
      // Create sheet JSON object
      Json.obj(
        "name" -> Json.fromString(sheet.name),
        "originalRowCount" -> Json.fromInt(sheet.originalRowCount),
        "originalColumnCount" -> Json.fromInt(sheet.originalColumnCount),
        "content" -> Json.obj(contentJson.toSeq: _*),
        "formulas" -> formulasJson,
        "tables" -> tablesJson,
        "metadata" -> sheet.compressionMetadata.asJson
      )
    }
    
    // Create the workbook JSON object
    Json.obj(
      "fileName" -> Json.fromString(workbook.fileName),
      "sheets" -> sheetJsons.asJson,
      "metadata" -> workbook.metadata.asJson,
      // Convert the map manually since there's no Encoder for Map[String, Any]
      "compressionStats" -> encodeCompressionStats(workbook.compressionStats)
    )
  
  /**
   * Manually encode the compression stats map to JSON
   * since there's no automatic encoder for Map[String, Any]
   */
  private def encodeCompressionStats(stats: Map[String, Any]): Json =
    val fields = stats.map { case (key, value) =>
      val jsonValue = value match
        case s: String => Json.fromString(s)
        case i: Int => Json.fromInt(i)
        case d: Double => Json.fromDoubleOrNull(d)
        case b: Boolean => Json.fromBoolean(b)
        case m: Map[_, _] => 
          m.asInstanceOf[Map[String, String]].asJson // Safe because metadata is String -> String
        case l: List[_] => 
          Json.fromValues(l.map(item => encodeCompressionStats(item.asInstanceOf[Map[String, Any]])))
        case _ => Json.fromString(value.toString)
        
      (key, jsonValue)
    }
    
    Json.obj(fields.toSeq: _*)
  
  /**
   * Apply Markdown formatting to values based on content type.
   * This enhances the JSON with formatting cues for LLMs.
   *
   * @param value The cell value or format descriptor
   * @return The value with Markdown formatting applied
   */
  private def applyMarkdownFormatting(value: String): String =
    // Check if this is a format descriptor
    if value.contains("<") && value.contains(">") then
      // Leave format descriptors as-is
      value
    else
      // Apply formatting based on content
      value match
        case v if v.startsWith("=") => 
          // Format formulas using backticks
          s"`$v`"
        case v if v.contains("**") || v.contains("*") || v.contains("`") =>
          // Value already has markdown, leave as-is
          v
        case v if Try(v.toDouble).isSuccess =>
          // Numeric value, leave as-is
          v
        case v =>
          // Regular text, no special formatting needed
          v

/**
 * Factory for creating LLM JSON renderers.
 */
object LLMJsonRenderer:
  /**
   * Create a new LLMJsonRenderer.
   *
   * @return A new renderer instance
   */
  def apply(): LLMJsonRenderer = new LLMJsonRenderer()