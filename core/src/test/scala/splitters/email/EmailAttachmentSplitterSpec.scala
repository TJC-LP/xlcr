package com.tjclp.xlcr
package splitters
package email

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import types.MimeType

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

    val chunks = DocumentSplitter.split(
      fc,
      SplitConfig(strategy = Some(SplitStrategy.Attachment))
    )

    chunks.head.label shouldBe "Greetings"
    val body = chunks.head
    body.label shouldBe "Greetings"
    body.index shouldBe 0
    body.content.data.length should be > 0

    new String(body.content.data, "UTF-8") should include("Hello Bob!")
  }

  it should "handle multipart emails with attachments" in {
    // This is a minimal multipart email with a text body and a PDF attachment
    val eml =
      """From: alice@example.com
         |To: bob@example.com
         |Subject: Test with Attachment
         |MIME-Version: 1.0
         |Content-Type: multipart/mixed; boundary="boundary123"
         |
         |--boundary123
         |Content-Type: text/plain; charset=UTF-8
         |
         |This is the email body.
         |
         |--boundary123
         |Content-Type: application/pdf; name="test.pdf"
         |Content-Disposition: attachment; filename="test.pdf"
         |Content-Transfer-Encoding: base64
         |
         |JVBERi0xLjQKJeLjz9MKCg==
         |
         |--boundary123--
         |""".stripMargin

    val fc = FileContent(eml.getBytes("UTF-8"), MimeType.MessageRfc822)

    val chunks = DocumentSplitter.split(
      fc,
      SplitConfig(strategy = Some(SplitStrategy.Attachment))
    )

    chunks.size shouldBe 2

    // Check email body
    val body = chunks.find(_.label == "Test with Attachment").get
    body.content.mimeType shouldBe MimeType.TextPlain
    new String(body.content.data, "UTF-8").trim shouldBe "This is the email body."

    // Check PDF attachment
    val attachment = chunks.find(_.label == "test.pdf").get
    attachment.content.mimeType shouldBe MimeType.ApplicationPdf
    attachment.content.data.length should be > 0
  }

  it should "handle emails with inline images and attachments" in {
    val eml =
      """From: alice@example.com
         |To: bob@example.com
         |Subject: Email with Image
         |MIME-Version: 1.0
         |Content-Type: multipart/mixed; boundary="outer"
         |
         |--outer
         |Content-Type: multipart/related; boundary="inner"
         |
         |--inner
         |Content-Type: text/html; charset=UTF-8
         |
         |<html><body>Check out this image!</body></html>
         |
         |--inner
         |Content-Type: image/png; name="logo.png"
         |Content-Disposition: inline; filename="logo.png"
         |Content-Transfer-Encoding: base64
         |
         |iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==
         |
         |--inner--
         |
         |--outer
         |Content-Type: application/pdf; name="document.pdf"
         |Content-Disposition: attachment; filename="document.pdf"
         |Content-Transfer-Encoding: base64
         |
         |JVBERi0xLjQKJeLjz9MKCg==
         |
         |--outer--
         |""".stripMargin

    val fc = FileContent(eml.getBytes("UTF-8"), MimeType.MessageRfc822)

    val chunks = DocumentSplitter.split(
      fc,
      SplitConfig(strategy = Some(SplitStrategy.Attachment))
    )

    chunks.size shouldBe 3

    // Check HTML body
    val body = chunks.find(_.content.mimeType == MimeType.TextHtml).get
    new String(body.content.data, "UTF-8") should include("Check out this image!")

    // Check inline image
    val image = chunks.find(_.label == "logo.png").get
    image.content.mimeType shouldBe MimeType.ImagePng

    // Check PDF attachment
    val pdf = chunks.find(_.label == "document.pdf").get
    pdf.content.mimeType shouldBe MimeType.ApplicationPdf
  }

  it should "handle emails that return SharedByteArrayInputStream for multipart content" in {
    // This tests the specific issue we fixed where getContent() returns a stream instead of Multipart
    // We'll use a real email structure that triggers this behavior
    val eml =
      """From: test@example.com
         |To: recipient@example.com
         |Subject: Test Email
         |MIME-Version: 1.0
         |Content-Type: multipart/alternative; boundary="alt-boundary"
         |
         |--alt-boundary
         |Content-Type: text/plain; charset=UTF-8
         |
         |Plain text version
         |
         |--alt-boundary
         |Content-Type: text/html; charset=UTF-8
         |
         |<html><body>HTML version</body></html>
         |
         |--alt-boundary--
         |""".stripMargin

    val fc = FileContent(eml.getBytes("UTF-8"), MimeType.MessageRfc822)

    // This should not throw ClassCastException
    val chunks = DocumentSplitter.split(
      fc,
      SplitConfig(strategy = Some(SplitStrategy.Attachment))
    )

    chunks.size should be >= 1
    // Should extract at least the plain text body
    val textBody = chunks.find(_.content.mimeType == MimeType.TextPlain)
    textBody.isDefined shouldBe true
  }
}
