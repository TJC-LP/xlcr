package com.tjclp.xlcr
package splitters.email

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

import com.aspose.email.MailMessage
import org.slf4j.LoggerFactory

import models.FileContent
import splitters.{ DocChunk, HighPrioritySplitter, SplitConfig, SplitStrategy, SplitFailureHandler, EmptyDocumentException, CorruptedDocumentException }
import types.{ FileType, MimeType }

/**
 * Splits a Microsoft Outlook .msg file into body + attachments using Aspose.Email.
 */
object OutlookMsgAsposeSplitter
    extends HighPrioritySplitter[MimeType.ApplicationVndMsOutlook.type] 
    with SplitFailureHandler {
  
  override protected val logger: org.slf4j.Logger = LoggerFactory.getLogger(getClass)

  override def split(
    content: FileContent[MimeType.ApplicationVndMsOutlook.type],
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
      // Load the .msg file using Aspose.Email
      val msg = try {
        MailMessage.load(new ByteArrayInputStream(content.data))
      } catch {
        case e: Exception =>
          throw new CorruptedDocumentException(
            content.mimeType.toString,
            s"Failed to load MSG file: ${e.getMessage}"
          )
      }
      
      // Check if message was loaded properly
      if (msg == null) {
        throw new CorruptedDocumentException(
          content.mimeType.toString,
          "Failed to load MSG file: MailMessage.load returned null"
        )
      }
      
      val chunks = ListBuffer.empty[DocChunk[_ <: MimeType]]

    // Add function to create and append chunks to our list
    def addChunk(bytes: Array[Byte], mime: MimeType, label: String): Unit =
      chunks += DocChunk(FileContent(bytes, mime), label, chunks.length, 0)

    // Extract mail body - prefer HTML if available
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
      val ext  = name.split("\\.").lastOption.getOrElse("").toLowerCase
      val mime = FileType.fromExtension(ext).map(_.getMimeType).getOrElse(MimeType.ApplicationOctet)

      addChunk(bytes, mime, name)
    }

      // If no chunks were created, throw exception
      if (chunks.isEmpty) {
        throw new EmptyDocumentException(
          content.mimeType.mimeType,
          "MSG file contains no body or attachments"
        )
      }

      // Finalize chunks with correct total count
      val total = chunks.size
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
