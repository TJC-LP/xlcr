package com.tjclp.xlcr
package parsers

import excel.{ExcelJsonParser, ExcelMarkdownParser, JsonToExcelParser}
import tika.{StandardTikaParser, XMLTikaParser}
import types.MimeType

import org.slf4j.LoggerFactory

/**
 * Handles the discovery and matching of parsers based on
 * input and output MIME types. Supports parser registration
 * and selection by priority.
 */
object ParserMatcher:
  private val logger = LoggerFactory.getLogger(getClass)
  private val parsers = Set[Parser](
    ExcelJsonParser,
    ExcelMarkdownParser,
    JsonToExcelParser,
    StandardTikaParser,
    XMLTikaParser
  )

  /**
   * Find the best matching parser for the given input and output MIME types
   * by comparing each parser's supported inputs/outputs and returning the
   * highest priority match.
   *
   * @param inputType  The detected input MIME type
   * @param outputType The requested output MIME type
   * @return An optional Parser if a match is found
   */
  def findParser(inputType: MimeType, outputType: MimeType): Option[Parser] =
    logger.debug(s"Finding parser for input: $inputType, output: $outputType")

    val matches = parsers.filter { parser =>
      parser.supportedInputTypes.contains(inputType) &&
        parser.outputType == outputType
    }

    matches.toList
      .sortBy(_.priority)(Ordering[Int].reverse) // Higher priority first
      .headOption
      .map { parser =>
        logger.info(s"Selected parser: ${parser.getClass.getSimpleName} with priority ${parser.priority}")
        parser
      }
