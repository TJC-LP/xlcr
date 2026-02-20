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
):

  /** True when every field matches the default `ConvertOptions()`. */
  def isDefault: Boolean = this == ConvertOptions()

  /** Human-readable summary of non-default options, useful for log messages. */
  def nonDefaultSummary: String =
    val parts = List.newBuilder[String]
    password.foreach(_ => parts += "password")
    if !evaluateFormulas then parts += "no-evaluate-formulas"
    if oneSheetPerPage then parts += "one-sheet-per-page"
    landscape.foreach(l => parts += (if l then "landscape" else "portrait"))
    paperSize.foreach(ps => parts += s"paper-size=$ps")
    if sheetNames.nonEmpty then parts += s"sheet=${sheetNames.mkString(",")}"
    if excludeHidden then parts += "exclude-hidden"
    if stripMasters then parts += "strip-masters"
    if !flowingLayout then parts += "fixed-layout"
    if !embedResources then parts += "no-embed-resources"
    parts.result().mkString(", ")

/**
 * Standard paper sizes.
 *
 * Values are hard-coded integers rather than Aspose constant references because this enum lives in
 * the `core` module, which has no dependency on `core-aspose`. The values correspond to stable
 * Windows DMPAPER_* constants and are unchanged across Aspose versions.
 */
enum PaperSize(val asposeCellsValue: Int):
  case Letter  extends PaperSize(1)  // PaperSizeType.PAPER_LETTER
  case Legal   extends PaperSize(5)  // PaperSizeType.PAPER_LEGAL
  case A3      extends PaperSize(8)  // PaperSizeType.PAPER_A_3
  case A4      extends PaperSize(9)  // PaperSizeType.PAPER_A_4
  case A5      extends PaperSize(11) // PaperSizeType.PAPER_A_5
  case Tabloid extends PaperSize(3)  // PaperSizeType.PAPER_TABLOID

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
