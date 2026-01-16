package com.tjclp.xlcr
package bridges.tika

import org.scalatest.BeforeAndAfter

import base.BridgeSpec
import models.FileContent
import types.MimeType
import types.MimeType.TextPlain

class TikaPlainTextBridgeSpec extends BridgeSpec with BeforeAndAfter {

  "TikaPlainTextBridge" should "convert arbitrary input to plain text" in {
    val data   = "Hello, world!".getBytes
    val input  = FileContent[MimeType](data, MimeType.TextPlain)
    val result = TikaPlainTextBridge.convert(input)
    result.mimeType shouldBe TextPlain
    new String(result.data) should include("Hello")
  }
}
