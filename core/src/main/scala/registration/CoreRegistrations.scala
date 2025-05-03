package com.tjclp.xlcr
package registration

import bridges.excel._
import bridges.image._
import bridges.powerpoint._
import bridges.tika._
import spi.{BridgeInfo, BridgeProvider, SplitterInfo, SplitterProvider}
import splitters.archive.{ArchiveEntrySplitter, ZipEntrySplitter}
import splitters.email.{EmailAttachmentSplitter, OutlookMsgSplitter}
import splitters.excel.{
  ExcelXlsSheetSplitter,
  ExcelXlsxSheetSplitter,
  OdsSheetSplitter
}
import splitters.pdf.PdfPageSplitter
import splitters.powerpoint.{
  PowerPointPptSlideSplitter,
  PowerPointPptxSlideSplitter
}
import splitters.text.{CsvSplitter, TextSplitter}
import splitters.word.{WordDocxHeadingSplitter, WordDocHeadingSplitter}
import types.MimeType

/** Provides the core bridges and splitters for registration via ServiceLoader.
  */
class CoreRegistrations extends BridgeProvider with SplitterProvider {

  override def getBridges: Iterable[BridgeInfo[_ <: MimeType, _ <: MimeType]] =
    Seq(
      // SheetsData bridging
      BridgeInfo(
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        MimeType.ApplicationJson,
        SheetsDataExcelBridge.chain(SheetsDataJsonBridge)
      ),
      BridgeInfo(
        MimeType.ApplicationJson,
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        SheetsDataJsonBridge.chain(SheetsDataExcelBridge)
      ),
      BridgeInfo(
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        MimeType.TextMarkdown,
        SheetsDataExcelBridge.chain(SheetsDataMarkdownBridge)
      ),
      BridgeInfo(
        MimeType.TextMarkdown,
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        SheetsDataMarkdownBridge.chain(SheetsDataExcelBridge)
      ),
      BridgeInfo(
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        MimeType.ImageSvgXml,
        SheetsDataExcelBridge.chain(SheetsDataSvgBridge)
      ),
      BridgeInfo(
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        MimeType.ApplicationVndOasisOpendocumentSpreadsheet,
        ExcelToOdsBridge
      ),
      // Image bridging
      BridgeInfo(
        MimeType.ImageSvgXml,
        MimeType.ImagePng,
        SvgToPngBridge
      ),
      BridgeInfo(
        MimeType.ApplicationPdf,
        MimeType.ImagePng,
        PdfToPngBridge
      ),
      BridgeInfo(
        MimeType.ApplicationPdf,
        MimeType.ImageJpeg,
        PdfToJpegBridge
      ),
      // SlidesData bridging
      BridgeInfo(
        MimeType.ApplicationVndMsPowerpoint,
        MimeType.ApplicationJson,
        SlidesDataPowerPointBridge.chain(SlidesDataJsonBridge)
      ),
      BridgeInfo(
        MimeType.ApplicationJson,
        MimeType.ApplicationVndMsPowerpoint,
        SlidesDataJsonBridge.chain(SlidesDataPowerPointBridge)
      ),
      // Tika bridging (catch-all, low priority)
      // Register Tika bridges as wildcard bridges to handle any mime type
      // These will be used when no specific bridge is found for an input->output pair
      BridgeInfo(
        MimeType.Wildcard,
        MimeType.TextPlain,
        TikaPlainTextBridge
      ),
      BridgeInfo(
        MimeType.Wildcard,
        MimeType.ApplicationXml,
        TikaXmlBridge
      )
    )

  override def getSplitters: Iterable[SplitterInfo[_ <: MimeType]] = Seq(
    // PDF
    SplitterInfo(MimeType.ApplicationPdf, PdfPageSplitter),
    // Excel
    SplitterInfo(MimeType.ApplicationVndMsExcel, ExcelXlsSheetSplitter),
    SplitterInfo(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      ExcelXlsxSheetSplitter
    ),
    SplitterInfo(
      MimeType.ApplicationVndOasisOpendocumentSpreadsheet,
      OdsSheetSplitter
    ),
    // PowerPoint
    SplitterInfo(
      MimeType.ApplicationVndMsPowerpoint,
      PowerPointPptSlideSplitter
    ),
    SplitterInfo(
      MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation,
      PowerPointPptxSlideSplitter
    ),
    // Word
    SplitterInfo(
      MimeType.ApplicationMsWord,
      WordDocHeadingSplitter
    ),
    SplitterInfo(
      MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument,
      WordDocxHeadingSplitter
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
