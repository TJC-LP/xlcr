package com.tjclp.xlcr
package splitters.email

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

import com.aspose.email.MailMessage

import models.FileContent
import splitters.{
  DocChunk,
  EmptyDocumentException,
  HighPrioritySplitter,
  SplitConfig,
  SplitFailureHandler,
  SplitStrategy
}
import types.{ FileType, MimeType }

/**
 * Splits an .eml (RFC-822) email into a body chunk + one chunk per attachment using Aspose.Email.
 */
object EmailAttachmentAsposeSplitter
    extends HighPrioritySplitter[MimeType.MessageRfc822.type]
    with SplitFailureHandler {

  override def split(
    content: FileContent[MimeType.MessageRfc822.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    // Check for valid strategy
    if (!cfg.hasStrategy(SplitStrategy.Attachment)) {
      return handleInvalidStrategy(
        content,
        cfg,
        cfg.strategy.map(_.displayName).getOrElse("none"),
        Seq("attachment")
      )
    }

    // Wrap main logic with failure handling
    withFailureHandling(content, cfg) {
      val msg    = MailMessage.load(new ByteArrayInputStream(content.data))
      val chunks = ListBuffer.empty[DocChunk[_ <: MimeType]]

      // Add function to create and append chunks to our list
      def addChunk(bytes: Array[Byte], mime: MimeType, label: String): Unit =
        chunks += DocChunk(FileContent(bytes, mime), label, chunks.length, 0)

      // Extract mail body
      if (msg.getHtmlBody != null && msg.getHtmlBody.nonEmpty) {
        val htmlBytes = msg.getHtmlBody.getBytes("UTF-8")
        val subject   = Option(msg.getSubject).getOrElse("email_body")
        addChunk(htmlBytes, MimeType.TextHtml, subject)
      } else if (msg.getBody != null && msg.getBody.nonEmpty) {
        val textBytes = msg.getBody.getBytes("UTF-8")
        val subject   = Option(msg.getSubject).getOrElse("email_body")
        addChunk(textBytes, MimeType.TextPlain, subject)
      }

      // Process all attachments
      for (attachment <- msg.getAttachments.asScala) {
        val name          = Option(attachment.getName).getOrElse(s"attachment_${chunks.length}")
        val contentStream = new ByteArrayOutputStream()
        attachment.save(contentStream)
        val bytes = contentStream.toByteArray

        // Determine MIME type from file extension
        val ext = name.split("\\.").lastOption.getOrElse("").toLowerCase
        val mime =
          FileType.fromExtension(ext).map(_.getMimeType).getOrElse(MimeType.ApplicationOctet)

        addChunk(bytes, mime, name)
      }

      // If no chunks were created, throw exception
      if (chunks.isEmpty) {
        throw new EmptyDocumentException(
          content.mimeType.mimeType,
          "Email contains no body or attachments"
        )
      }

      // Finalize chunks with correct total count
      val total     = chunks.size
      val allChunks = chunks.map(c => c.copy(total = total)).toSeq.sortBy(_.index)

      // Apply chunk range filtering if specified
      cfg.chunkRange match {
        case Some(range) =>
          range.filter(i => i >= 0 && i < total).map(allChunks(_)).toSeq
        case None =>
          allChunks
      }
    }
  }
}
