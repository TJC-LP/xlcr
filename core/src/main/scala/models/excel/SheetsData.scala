package com.tjclp.xlcr
package models.excel

import models.Model
import types.MimeType
import types.MimeType.*

/**
 * SheetsData is a container for multiple SheetData instances.
 * It implements the Model trait so it can load (fromMimeType) or
 * export (toMimeType) data as JSON, Excel, or Markdown.
 */
final case class SheetsData(sheets: List[SheetData]) extends Model

object SheetsData extends Model
