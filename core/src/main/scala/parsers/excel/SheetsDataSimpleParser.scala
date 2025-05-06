package com.tjclp.xlcr
package parsers.excel

import models.excel.SheetsData
import parsers.SimpleParser
import types.MimeType

/**
 * Base trait for parsers that create SheetsData models from various Excel file formats.
 */
trait SheetsDataSimpleParser[I <: MimeType] extends SimpleParser[I, SheetsData]
