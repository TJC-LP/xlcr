package com.tjclp.xlcr
package parsers.excel

import models.excel.SheetsData
import parsers.Parser
import types.MimeType

/**
 * Base trait for parsers that create SheetsData models from various Excel file formats.
 */
trait SheetsDataParser[I <: MimeType] extends Parser[I, SheetsData]
