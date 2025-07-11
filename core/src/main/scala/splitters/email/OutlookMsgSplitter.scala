package com.tjclp.xlcr
package splitters
package email

import scala.util.Using

import org.apache.poi.hsmf.MAPIMessage
import org.slf4j.LoggerFactory

import models.FileContent
import types.{ FileType, MimeType }

/**
 * Splits a Microsoft Outlook .msg file into body + attachments, mirroring the behaviour of
 * EmailAttachmentSplitter but using POI‑HSMF to read the MSG container directly.
 */
object OutlookMsgSplitter
    extends DocumentSplitter[MimeType.ApplicationVndMsOutlook.type]
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
      Using.resource(new java.io.ByteArrayInputStream(content.data)) { is =>
        val msg = new MAPIMessage(is)

        // Prepare body and attachments
        val bodyText =
          Option(msg.getTextBody).orElse(Option(msg.getHtmlBody)).getOrElse("")
        val hasBody = bodyText.nonEmpty
        val atts = Option(msg.getAttachmentFiles)
          .map(_.toIndexedSeq)
          .getOrElse(IndexedSeq.empty)
        val totalChunks = (if (hasBody) 1 else 0) + atts.size

        // Check if message has any content
        if (totalChunks == 0) {
          throw new EmptyDocumentException(
            content.mimeType.mimeType,
            "MSG file contains no body or attachments"
          )
        }

        // Build chunks with correct indices and total
        val chunks =
          scala.collection.mutable.ListBuffer.empty[DocChunk[_ <: MimeType]]
        if (hasBody) {
          val mime =
            if (msg.getHtmlBody != null) MimeType.TextHtml else MimeType.TextPlain
          val subj = Option(msg.getSubject).getOrElse("body")
          chunks += DocChunk(
            FileContent(bodyText.getBytes("UTF-8"), mime),
            subj,
            0,
            totalChunks
          )
        }
        atts.zipWithIndex.foreach { case (att, idx0) =>
          val idx = if (hasBody) idx0 + 1 else idx0
          val name = Option(att.getAttachLongFileName)
            .map(_.toString)
            .filter(_.nonEmpty)
            .orElse(Option(att.getAttachFileName).map(_.toString))
            .getOrElse(s"attachment_$idx")
          val bytes = Option(att.getEmbeddedAttachmentObject)
            .collect { case arr: Array[Byte] => arr }
            .getOrElse(Array.emptyByteArray)
          val ext = name.split("\\.").toList.lastOption.getOrElse("").toLowerCase
          val mime = FileType
            .fromExtension(ext)
            .map(_.getMimeType)
            .getOrElse(MimeType.ApplicationOctet)
          chunks += DocChunk(FileContent(bytes, mime), name, idx, totalChunks)
        }
        // Return sorted chunks
        val allChunks = chunks.toSeq.sortBy(_.index)

        // Apply chunk range filtering if specified
        cfg.chunkRange match {
          case Some(range) =>
            range.filter(i => i >= 0 && i < totalChunks).map(allChunks(_)).toSeq
          case None =>
            allChunks
        }
      }
    }
  }
}
