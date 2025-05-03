package com.tjclp.xlcr
package splitters

import models.FileContent
import types.MimeType

/** Metadata‑enriched chunk produced by a DocumentSplitter. */
case class DocChunk[T <: MimeType](
    content: FileContent[T],
    label: String, // human readable (e.g. Sheet name, "Page 3")
    index: Int, // 0‑based position within parent
    total: Int, // total number of chunks produced
    attrs: Map[String, String] = Map.empty
) extends Serializable