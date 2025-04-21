package com.tjclp.xlcr
package utils

import models.FileContent
import types.MimeType

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EmailAttachmentSplitterSpec extends AnyFlatSpec with Matchers {

  it should "split email body even when no attachments" in {
    val eml =
      """From: alice@example.com
         |To: bob@example.com
         |Subject: Greetings
         |Content-Type: text/plain; charset=UTF-8
         |
         |Hello Bob!
         |""".stripMargin

    val fc = FileContent(eml.getBytes("UTF-8"), MimeType.MessageRfc822)

    val chunks = DocumentSplitter.split(fc, SplitConfig(strategy = SplitStrategy.Attachment))

    chunks.head.label shouldBe "Greetings"
    val body = chunks.head
    body.label shouldBe "Greetings"
    body.index shouldBe 0
    body.content.data.length should be > 0

    new String(body.content.data, "UTF-8") should include ("Hello Bob!")
  }
}
