package com.tjclp.xlcr
package parsers.tika

import base.ParserSpec
import models.FileContent
import models.tika.TikaModel
import types.MimeType

class TikaParserSpec extends ParserSpec {

  // We can't instantiate TikaParser directly, so we test a minimal extension
  "TikaParser" should "fail on invalid input" in {
    val parser = new TikaParser[MimeType, MimeType.TextPlain.type] {
      protected def contentHandler = null // invalid for demonstration
    }
    val fc = FileContent[MimeType](Array.emptyByteArray, MimeType.TextPlain)

    val ex = intercept[TikaParseError] {
      parser.parse(fc)
    }
    ex.message should include("Failed to parse")
  }
}