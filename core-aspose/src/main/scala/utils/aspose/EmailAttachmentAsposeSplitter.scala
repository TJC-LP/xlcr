package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.{FileType, MimeType}
import utils.{DocChunk, DocumentSplitter, SplitConfig, SplitStrategy}

import com.aspose.email.MailMessage

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

/**
 * Splits an .eml (RFC-822) email into a body chunk + one chunk per attachment 
 * using Aspose.Email.
 */
object EmailAttachmentAsposeSplitter
    extends HighPrioritySplitter[MimeType.MessageRfc822.type] {

  override def split(
      content: FileContent[MimeType.MessageRfc822.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    if (!cfg.hasStrategy(SplitStrategy.Attachment))
      return Seq(DocChunk(content, "email", 0, 1))

    val msg = MailMessage.load(new ByteArrayInputStream(content.data))
    val chunks = ListBuffer.empty[DocChunk[_ <: MimeType]]

    // Add function to create and append chunks to our list
    def addChunk(bytes: Array[Byte], mime: MimeType, label: String): Unit =
      chunks += DocChunk(FileContent(bytes, mime), label, chunks.length, 0)

    // Extract mail body
    if (msg.getHtmlBody != null && msg.getHtmlBody.nonEmpty) {
      val htmlBytes = msg.getHtmlBody.getBytes("UTF-8")
      val subject = Option(msg.getSubject).getOrElse("email_body")
      addChunk(htmlBytes, MimeType.TextHtml, subject)
    } else if (msg.getBody != null && msg.getBody.nonEmpty) {
      val textBytes = msg.getBody.getBytes("UTF-8")
      val subject = Option(msg.getSubject).getOrElse("email_body")
      addChunk(textBytes, MimeType.TextPlain, subject)
    }

    // Process all attachments
    for (attachment <- msg.getAttachments.asScala) {
      val name = Option(attachment.getName).getOrElse(s"attachment_${chunks.length}")
      val contentStream = new ByteArrayOutputStream()
      attachment.save(contentStream)
      val bytes = contentStream.toByteArray

      // Determine MIME type from file extension
      val ext = name.split("\\.").lastOption.getOrElse("").toLowerCase
      val mime = FileType.fromExtension(ext).map(_.getMimeType).getOrElse(MimeType.ApplicationOctet)
      
      addChunk(bytes, mime, name)
    }

    // If no chunks were created, return original email
    if (chunks.isEmpty) {
      return Seq(DocChunk(content, "email", 0, 1))
    }

    // Finalize chunks with correct total count
    val total = chunks.size
    chunks.map(c => c.copy(total = total)).toSeq.sortBy(_.index)
  }
}