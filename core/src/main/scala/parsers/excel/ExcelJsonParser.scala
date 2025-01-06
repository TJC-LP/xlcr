package com.tjclp.xlcr
package parsers.excel

import adapters.AdapterRegistry
import models.Content
import types.MimeType
import utils.FileUtils

import org.slf4j.LoggerFactory

import java.nio.file.{Files, Path}
import scala.util.Try

/**
 * This file is drastically simplified. We now just delegate
 * to our bridging approach (AdapterRegistry).
 *
 * If desired, we can remove this entirely and update references
 * in tests or code to use the new bridging approach. For now,
 * we'll keep a minimal placeholder that passes to AdapterRegistry.
 */
object ExcelJsonParser extends ExcelParser {
  private val logger = LoggerFactory.getLogger(getClass)

  override def extractContent(input: Path, output: Option[Path]): Try[Content] = Try {

    val inputBytes = Files.readAllBytes(input)
    val fromMime = FileUtils.detectMimeType(input)

    // We assume JSON output
    val toMime = MimeType.ApplicationJson

    AdapterRegistry.convert(inputBytes, fromMime, toMime) match {
      case Right(jsonBytes) =>
        // If 'output' is specified, we can write out
        output.foreach(out => Files.write(out, jsonBytes))
        Content(jsonBytes, toMime.mimeType, Map("Delegated" -> "ExcelJsonParserBridge"))
      case Left(err) =>
        throw new RuntimeException(err)
    }
  }

  override def outputType: MimeType = MimeType.ApplicationJson
}