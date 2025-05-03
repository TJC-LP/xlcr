package com.tjclp.xlcr
package splitters

/** Splitting strategies supported for different document types. */
sealed trait SplitStrategy {
  /** Returns a clean, serialization-friendly string representation */
  def displayName: String
}

object SplitStrategy {
  case object Page extends SplitStrategy {
    override def displayName: String = "page"
  }
  case object Sheet extends SplitStrategy {
    override def displayName: String = "sheet"
  }
  case object Slide extends SplitStrategy {
    override def displayName: String = "slide"
  }
  case object Row extends SplitStrategy {
    override def displayName: String = "row"
  }
  case object Column extends SplitStrategy {
    override def displayName: String = "column"
  }
  case object Attachment extends SplitStrategy {
    override def displayName: String = "attachment"
  }
  case object Embedded extends SplitStrategy {
    override def displayName: String = "embedded"
  }
  case object Paragraph extends SplitStrategy {
    override def displayName: String = "paragraph"
  }
  case object Sentence extends SplitStrategy {
    override def displayName: String = "sentence"
  }
  case object Heading extends SplitStrategy {
    override def displayName: String = "heading"
  }
  case object Chunk extends SplitStrategy {
    override def displayName: String = "chunk"
  }

  /** Automatically selects the appropriate strategy based on the input MIME type */
  case object Auto extends SplitStrategy {
    override def displayName: String = "auto"
  }

  def fromString(s: String): Option[SplitStrategy] = s.trim.toLowerCase match {
    case "page"       => Some(Page)
    case "sheet"      => Some(Sheet)
    case "slide"      => Some(Slide)
    case "row"        => Some(Row)
    case "column"     => Some(Column)
    case "attachment" => Some(Attachment)
    case "embedded"   => Some(Embedded)
    case "paragraph"  => Some(Paragraph)
    case "sentence"   => Some(Sentence)
    case "heading"    => Some(Heading)
    case "chunk"      => Some(Chunk)
    case "auto"       => Some(Auto)
    case _            => None
  }
}