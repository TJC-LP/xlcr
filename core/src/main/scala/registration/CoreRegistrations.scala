package com.tjclp.xlcr
package registration

import bridges.excel._
import bridges.image._
import bridges.powerpoint._
import bridges.tika._
import spi.{BridgeInfo, BridgeProvider, SplitterInfo, SplitterProvider}
import splitters.archive.{ArchiveEntrySplitter, ZipEntrySplitter}
import splitters.email.{EmailAttachmentSplitter, OutlookMsgSplitter}
import splitters.excel.{ExcelSheetSplitter, OdsSheetSplitter}
import splitters.pdf.PdfPageSplitter
import splitters.powerpoint.PowerPointSlideSplitter
import splitters.text.{CsvSplitter, TextSplitter}
import splitters.word.WordHeadingSplitter
import types.MimeType

/** Provides the core bridges and splitters for registration via ServiceLoader.
  */
class CoreRegistrations extends BridgeProvider with SplitterProvider {

  override def getBridges: Iterable[BridgeInfo[_ <: MimeType, _ <: MimeType]] =
    Seq(
      // SheetsData bridging
      BridgeInfo[
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
        MimeType.ApplicationJson.type
      ](
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        MimeType.ApplicationJson,
        SheetsDataExcelBridge.chain(SheetsDataJsonBridge)
      ),
      BridgeInfo[
        MimeType.ApplicationJson.type,
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
      ](
        MimeType.ApplicationJson,
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        SheetsDataJsonBridge.chain(SheetsDataExcelBridge)
      ),
      BridgeInfo[
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
        MimeType.TextMarkdown.type
      ](
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        MimeType.TextMarkdown,
        SheetsDataExcelBridge.chain(SheetsDataMarkdownBridge)
      ),
      BridgeInfo[
        MimeType.TextMarkdown.type,
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type
      ](
        MimeType.TextMarkdown,
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        SheetsDataMarkdownBridge.chain(SheetsDataExcelBridge)
      ),
      BridgeInfo[
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
        MimeType.ImageSvgXml.type
      ](
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        MimeType.ImageSvgXml,
        SheetsDataExcelBridge.chain(SheetsDataSvgBridge)
      ),
      BridgeInfo[
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
        MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type
      ](
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        MimeType.ApplicationVndOasisOpendocumentSpreadsheet,
        ExcelToOdsBridge
      ),
      // Image bridging
      BridgeInfo[MimeType.ImageSvgXml.type, MimeType.ImagePng.type](
        MimeType.ImageSvgXml,
        MimeType.ImagePng,
        SvgToPngBridge
      ),
      BridgeInfo[MimeType.ApplicationPdf.type, MimeType.ImagePng.type](
        MimeType.ApplicationPdf,
        MimeType.ImagePng,
        PdfToPngBridge
      ),
      BridgeInfo[MimeType.ApplicationPdf.type, MimeType.ImageJpeg.type](
        MimeType.ApplicationPdf,
        MimeType.ImageJpeg,
        PdfToJpegBridge
      ),
      // SlidesData bridging
      BridgeInfo[
        MimeType.ApplicationVndMsPowerpoint.type,
        MimeType.ApplicationJson.type
      ](
        MimeType.ApplicationVndMsPowerpoint,
        MimeType.ApplicationJson,
        SlidesDataPowerPointBridge.chain(SlidesDataJsonBridge)
      ),
      BridgeInfo[
        MimeType.ApplicationJson.type,
        MimeType.ApplicationVndMsPowerpoint.type
      ](
        MimeType.ApplicationJson,
        MimeType.ApplicationVndMsPowerpoint,
        SlidesDataJsonBridge.chain(SlidesDataPowerPointBridge)
      ),
      // Tika bridging (catch-all, low priority)
      // Register Tika bridges as wildcard bridges to handle any mime type
      // These will be used when no specific bridge is found for an input->output pair
      BridgeInfo[MimeType, MimeType.TextPlain.type](
        MimeType.Wildcard,
        MimeType.TextPlain,
        TikaPlainTextBridge
      ),
      BridgeInfo[MimeType, MimeType.ApplicationXml.type](
        MimeType.Wildcard,
        MimeType.ApplicationXml,
        TikaXmlBridge
      )
    )

  override def getSplitters: Iterable[SplitterInfo] = Seq(
    // PDF
    SplitterInfo(MimeType.ApplicationPdf, new PdfPageSplitter),
    // Excel
    SplitterInfo(MimeType.ApplicationVndMsExcel, new ExcelSheetSplitter),
    SplitterInfo(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      new ExcelSheetSplitter
    ),
    SplitterInfo(
      MimeType.ApplicationVndOasisOpendocumentSpreadsheet,
      new OdsSheetSplitter
    ),
    // PowerPoint
    SplitterInfo(
      MimeType.ApplicationVndMsPowerpoint,
      new PowerPointSlideSplitter
    ),
    SplitterInfo(
      MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation,
      new PowerPointSlideSplitter
    ),
    // Word
    SplitterInfo(
      MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument,
      new WordHeadingSplitter
    ),
    // Email
    SplitterInfo(MimeType.MessageRfc822, new EmailAttachmentSplitter),
    SplitterInfo(MimeType.ApplicationVndMsOutlook, new OutlookMsgSplitter),
    // Archives
    SplitterInfo(MimeType.ApplicationZip, new ArchiveEntrySplitter),
    SplitterInfo(MimeType.ApplicationZip, new ZipEntrySplitter),
    SplitterInfo(MimeType.ApplicationGzip, new ArchiveEntrySplitter),
    SplitterInfo(MimeType.ApplicationSevenz, new ArchiveEntrySplitter),
    SplitterInfo(MimeType.ApplicationTar, new ArchiveEntrySplitter),
    SplitterInfo(MimeType.ApplicationBzip2, new ArchiveEntrySplitter),
    SplitterInfo(MimeType.ApplicationXz, new ArchiveEntrySplitter),
    // Text/CSV
    SplitterInfo(MimeType.TextPlain, new TextSplitter),
    SplitterInfo(MimeType.TextCsv, new CsvSplitter)
  )
}
