package com.tjclp.xlcr.v2.types

/**
 * Flat, immutable options bag for controlling conversion and split behavior.
 *
 * Every field has a sensible default so that `ConvertOptions()` produces the same behavior as
 * today's zero-argument conversions. Options that are irrelevant to a particular conversion are
 * silently ignored.
 */
final case class ConvertOptions(
  // --- General ---
  password: Option[String] = None,

  // --- Excel options (Aspose.Cells) ---
  evaluateFormulas: Boolean = true,
  oneSheetPerPage: Boolean = false,
  landscape: Option[Boolean] = None,
  paperSize: Option[PaperSize] = None,
  sheetNames: List[String] = Nil,
  excludeHidden: Boolean = false,

  // --- PowerPoint options (Aspose.Slides) ---
  stripMasters: Boolean = false,

  // --- PDF -> HTML options (Aspose.PDF) ---
  flowingLayout: Boolean = true,
  embedResources: Boolean = true
)

/**
 * Standard paper sizes. Values correspond to com.aspose.cells.PaperSizeType constants.
 */
enum PaperSize(val asposeCellsValue: Int):
  case Letter  extends PaperSize(1)
  case Legal   extends PaperSize(5)
  case A3      extends PaperSize(8)
  case A4      extends PaperSize(9)
  case A5      extends PaperSize(11)
  case Tabloid extends PaperSize(3)

object PaperSize:
  def fromString(s: String): Option[PaperSize] =
    s.toLowerCase match
      case "letter"  => Some(PaperSize.Letter)
      case "legal"   => Some(PaperSize.Legal)
      case "a3"      => Some(PaperSize.A3)
      case "a4"      => Some(PaperSize.A4)
      case "a5"      => Some(PaperSize.A5)
      case "tabloid" => Some(PaperSize.Tabloid)
      case _         => None
