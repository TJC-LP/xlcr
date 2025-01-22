package com.tjclp.xlcr
package parsers.excel

import parsers.Parser
import types.MimeType
import models.excel.SheetsData

trait SheetsDataParser[I <: MimeType] extends Parser[I, SheetsData]
