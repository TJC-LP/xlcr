package com.tjclp.xlcr
package splitters
package email

import models.FileContent
import types.{FileType, MimeType}

import org.apache.poi.hsmf.MAPIMessage
// no extra imports needed

/** Splits a Microsoft Outlook .msg file into body + attachments, mirroring the
  * behaviour of EmailAttachmentSplitter but using POI‑HSMF to read the MSG
  * container directly.
  */
class OutlookMsgSplitter
    extends DocumentSplitter[MimeType.ApplicationVndMsOutlook.type] {

  override def split(
      content: FileContent[MimeType.ApplicationVndMsOutlook.type],
      cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    if (!cfg.hasStrategy(SplitStrategy.Attachment))
      return Seq(DocChunk(content, "msg", 0, 1))

    val msg = new MAPIMessage(new java.io.ByteArrayInputStream(content.data))

    // Prepare body and attachments
    val bodyText =
      Option(msg.getTextBody).orElse(Option(msg.getHtmlBody)).getOrElse("")
    val hasBody = bodyText.nonEmpty
    val atts = Option(msg.getAttachmentFiles)
      .map(_.toIndexedSeq)
      .getOrElse(IndexedSeq.empty)
    val totalChunks = (if (hasBody) 1 else 0) + atts.size

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
    chunks.toSeq.sortBy(_.index)
  }
}
