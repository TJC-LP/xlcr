package com.tjclp.xlcr.registration

import com.tjclp.xlcr.spi.{BridgeInfo, BridgeProvider, SplitterInfo, SplitterProvider}
import com.tjclp.xlcr.bridges.excel._
import com.tjclp.xlcr.bridges.image._
import com.tjclp.xlcr.bridges.powerpoint._
import com.tjclp.xlcr.bridges.tika._
import com.tjclp.xlcr.splitters.archive.ArchiveEntrySplitter
import com.tjclp.xlcr.splitters.email.{EmailAttachmentSplitter, OutlookMsgSplitter}
import com.tjclp.xlcr.splitters.excel.{ExcelSheetSplitter, OdsSheetSplitter}
import com.tjclp.xlcr.splitters.pdf.PdfPageSplitter
import com.tjclp.xlcr.splitters.powerpoint.PowerPointSlideSplitter
import com.tjclp.xlcr.splitters.text.{CsvSplitter, TextSplitter}
import com.tjclp.xlcr.splitters.word.WordHeadingSplitter
import com.tjclp.xlcr.types.MimeType
import com.tjclp.xlcr.utils._

/**
 * Provides the core bridges and splitters for registration via ServiceLoader.
 */
class CoreRegistrations extends BridgeProvider with SplitterProvider {

  override def getBridges: Iterable[BridgeInfo] = Seq(
    // SheetsData bridging
    BridgeInfo(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, MimeType.ApplicationJson, SheetsDataExcelBridge.chain(SheetsDataJsonBridge)),
    BridgeInfo(MimeType.ApplicationJson, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, SheetsDataJsonBridge.chain(SheetsDataExcelBridge)),
    BridgeInfo(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, MimeType.TextMarkdown, SheetsDataExcelBridge.chain(SheetsDataMarkdownBridge)),
    BridgeInfo(MimeType.TextMarkdown, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, SheetsDataMarkdownBridge.chain(SheetsDataExcelBridge)),
    BridgeInfo(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, MimeType.ImageSvgXml, SheetsDataExcelBridge.chain(SheetsDataSvgBridge)),
    BridgeInfo(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, MimeType.ApplicationVndOasisOpendocumentSpreadsheet, ExcelToOdsBridge),

    // Image bridging
    BridgeInfo(MimeType.ImageSvgXml, MimeType.ImagePng, SvgToPngBridge),
    BridgeInfo(MimeType.ApplicationPdf, MimeType.ImagePng, PdfToPngBridge),
    BridgeInfo(MimeType.ApplicationPdf, MimeType.ImageJpeg, PdfToJpegBridge),

    // SlidesData bridging
    BridgeInfo(MimeType.ApplicationVndMsPowerpoint, MimeType.ApplicationJson, SlidesDataPowerPointBridge.chain(SlidesDataJsonBridge)),
    BridgeInfo(MimeType.ApplicationJson, MimeType.ApplicationVndMsPowerpoint, SlidesDataJsonBridge.chain(SlidesDataPowerPointBridge)),

    // Tika bridging (catch-all, low priority)
    // Register Tika bridges for common types explicitly, ServiceLoader handles the rest if needed
    // Note: TikaBridge itself has LOW priority
    BridgeInfo(MimeType.ApplicationMsWord, MimeType.TextPlain, TikaPlainTextBridge),
    BridgeInfo(MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument, MimeType.TextPlain, TikaPlainTextBridge),
    BridgeInfo(MimeType.ApplicationPdf, MimeType.TextPlain, TikaPlainTextBridge),
    BridgeInfo(MimeType.TextHtml, MimeType.TextPlain, TikaPlainTextBridge),

    BridgeInfo(MimeType.ApplicationMsWord, MimeType.ApplicationXml, TikaXmlBridge),
    BridgeInfo(MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument, MimeType.ApplicationXml, TikaXmlBridge),
    BridgeInfo(MimeType.ApplicationPdf, MimeType.ApplicationXml, TikaXmlBridge),
    BridgeInfo(MimeType.TextHtml, MimeType.ApplicationXml, TikaXmlBridge)
  )

  override def getSplitters: Iterable[SplitterInfo] = Seq(
    // PDF
    SplitterInfo(MimeType.ApplicationPdf, new PdfPageSplitter),

    // Excel
    SplitterInfo(MimeType.ApplicationVndMsExcel, new ExcelSheetSplitter),
    SplitterInfo(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, new ExcelSheetSplitter),
    SplitterInfo(MimeType.ApplicationVndOasisOpendocumentSpreadsheet, new OdsSheetSplitter),

    // PowerPoint
    SplitterInfo(MimeType.ApplicationVndMsPowerpoint, new PowerPointSlideSplitter),
    SplitterInfo(MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation, new PowerPointSlideSplitter),

    // Word
    SplitterInfo(MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument, new WordHeadingSplitter),

    // Email
    SplitterInfo(MimeType.MessageRfc822, new EmailAttachmentSplitter),
    SplitterInfo(MimeType.ApplicationVndMsOutlook, new OutlookMsgSplitter),

    // Archives
    SplitterInfo(MimeType.ApplicationZip, new ArchiveEntrySplitter),
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