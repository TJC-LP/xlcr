package com.tjclp.xlcr
package parsers.tika

import com.tjclp.xlcr.parsers.tika.TikaContentHandler
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.xml.sax.ContentHandler

class TikaContentHandlerSpec extends AnyFlatSpec with Matchers {

  "TikaContentHandler.text" should "create a BodyContentHandler" in {
    val handler: ContentHandler = TikaContentHandler.text()
    handler.getClass.getSimpleName shouldBe "BodyContentHandler"
  }

  "TikaContentHandler.xml" should "create a WriteOutContentHandler" in {
    val handler: ContentHandler = TikaContentHandler.xml()
    handler.getClass.getSimpleName shouldBe "WriteOutContentHandler"
  }
}