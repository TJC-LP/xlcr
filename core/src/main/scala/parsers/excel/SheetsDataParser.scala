package com.tjclp.xlcr
package parsers.excel

import models.excel.SheetsData
import parsers.Parser
import types.MimeType

trait SheetsDataParser[I <: MimeType] extends Parser[I, SheetsData]
