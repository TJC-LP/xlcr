package com.tjclp.xlcr
package bridges.tika

import org.scalatest.BeforeAndAfter

import base.BridgeSpec
import models.FileContent
import types.MimeType
import types.MimeType.ApplicationXml

class TikaXmlBridgeSpec extends BridgeSpec with BeforeAndAfter {

  "TikaXmlBridge" should "convert input to XML" in {
    val data   = """{"test":"json"}""".getBytes
    val input  = FileContent[MimeType](data, MimeType.ApplicationJson)
    val result = TikaXmlBridge.convert(input)
    result.mimeType shouldBe ApplicationXml
    new String(result.data) should include("<")
  }
}
