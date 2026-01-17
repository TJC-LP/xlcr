package com.tjclp.xlcr
package registration

import bridges.excel.ExcelToOdsBridge
import bridges.tika._
import spi.{ BridgeInfo, BridgeProvider, SplitterInfo, SplitterProvider }
import splitters.archive.ZipEntrySplitter
import splitters.email.{ EmailAttachmentSplitter, OutlookMsgSplitter }
import splitters.excel.{ ExcelXlsSheetSplitter, ExcelXlsxSheetSplitter, OdsSheetSplitter }
import splitters.pdf.PdfPageSplitter
import splitters.powerpoint.{ PowerPointPptSlideSplitter, PowerPointPptxSlideSplitter }
import splitters.text.{ CsvSplitter, TextSplitter }
import splitters.word.{ WordDocRouterSplitter, WordDocxRouterSplitter }
import types.MimeType

/**
 * Provides the core bridges and splitters for registration via ServiceLoader.
 */
class CoreRegistrations extends BridgeProvider with SplitterProvider {

  override def getBridges: Iterable[BridgeInfo[_ <: MimeType, _ <: MimeType]] =
    Seq(
      // Excel → ODS (well-defined format conversion)
      BridgeInfo(
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        MimeType.ApplicationVndOasisOpendocumentSpreadsheet,
        ExcelToOdsBridge
      ),
      // Tika bridging (catch-all, low priority)
      BridgeInfo(MimeType.Wildcard, MimeType.TextPlain, TikaPlainTextBridge),
      BridgeInfo(MimeType.Wildcard, MimeType.ApplicationXml, TikaXmlBridge)
    )

  override def getSplitters: Iterable[SplitterInfo[_ <: MimeType]] = Seq(
    // PDF
    SplitterInfo(MimeType.ApplicationPdf, PdfPageSplitter),
    // Excel
    SplitterInfo(MimeType.ApplicationVndMsExcel, ExcelXlsSheetSplitter),
    SplitterInfo(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ExcelXlsxSheetSplitter),
    SplitterInfo(MimeType.ApplicationVndOasisOpendocumentSpreadsheet, OdsSheetSplitter),
    // PowerPoint
    SplitterInfo(MimeType.ApplicationVndMsPowerpoint, PowerPointPptSlideSplitter),
    SplitterInfo(
      MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation,
      PowerPointPptxSlideSplitter
    ),
    // Word
    SplitterInfo(MimeType.ApplicationMsWord, WordDocRouterSplitter),
    SplitterInfo(
      MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument,
      WordDocxRouterSplitter
    ),
    // Email
    SplitterInfo(MimeType.MessageRfc822, EmailAttachmentSplitter),
    SplitterInfo(MimeType.ApplicationVndMsOutlook, OutlookMsgSplitter),
    // Archives
    SplitterInfo(MimeType.ApplicationZip, ZipEntrySplitter),
    // Text/CSV
    SplitterInfo(MimeType.TextPlain, TextSplitter),
    SplitterInfo(MimeType.TextCsv, CsvSplitter)
  )
}
