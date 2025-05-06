package com.tjclp.xlcr
package splitters
package email

import java.io.ByteArrayInputStream
import java.util.Properties

import scala.collection.mutable.ListBuffer

import jakarta.mail.internet.MimeMessage
import jakarta.mail.{ Multipart, Part, Session }

import models.FileContent
import types.MimeType

/**
 * Splits an .eml (RFC‑822) email into a body chunk + one chunk per attachment.
 */
object EmailAttachmentSplitter
    extends DocumentSplitter[MimeType.MessageRfc822.type] {

  override def split(
    content: FileContent[MimeType.MessageRfc822.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {

    if (!cfg.hasStrategy(SplitStrategy.Attachment))
      return Seq(DocChunk(content, "email", 0, 1))

    val session = Session.getDefaultInstance(new Properties())
    val msg     = new MimeMessage(session, new ByteArrayInputStream(content.data))

    val chunks = ListBuffer.empty[DocChunk[_ <: MimeType]]

    def addChunk(bytes: Array[Byte], mime: MimeType, label: String): Unit =
      chunks += DocChunk(FileContent(bytes, mime), label, chunks.length, 0)

    // Prefer plain text body, fall back to html or first non‑attachment part
    var bodyCaptured = false

    def harvest(part: Part): Unit =
      if (part.isMimeType("multipart/*")) {
        val mp = part.getContent.asInstanceOf[Multipart]
        (0 until mp.getCount).foreach(i => harvest(mp.getBodyPart(i)))
      } else {
        val disposition = Option(part.getDisposition).getOrElse("")
        val ctype       = part.getContentType.toLowerCase

        if (
          Part.ATTACHMENT.equalsIgnoreCase(
            disposition
          ) || disposition == "inline" && ctype.startsWith("image/")
        ) {
          val bytes = part.getInputStream.readAllBytes()
          val mime =
            MimeType.fromString(ctype.split(";")(0), MimeType.ApplicationOctet)
          val name =
            Option(part.getFileName).getOrElse(s"attachment_${chunks.length}")
          addChunk(bytes, mime, name)
        } else if (
          !bodyCaptured && (part
            .isMimeType("text/plain") || part.isMimeType("text/html"))
        ) {
          val bytes = part.getInputStream.readAllBytes()
          val mime =
            if (part.isMimeType("text/html")) MimeType.TextHtml
            else MimeType.TextPlain
          val subj = Option(msg.getSubject).getOrElse("body")
          addChunk(bytes, mime, subj)
          bodyCaptured = true
        }
      }

    harvest(msg)

    if (chunks.isEmpty) {
      // fallback: treat entire message as single chunk
      Seq(DocChunk(content, "email", 0, 1))
    } else {
      val total = chunks.size
      chunks.map(c => c.copy(total = total)).toSeq.sortBy(_.index)
    }
  }
}
