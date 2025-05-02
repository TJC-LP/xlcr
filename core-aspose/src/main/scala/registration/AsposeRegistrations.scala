package com.tjclp.xlcr.registration

import com.tjclp.xlcr.spi.{BridgeInfo, BridgeProvider, SplitterInfo, SplitterProvider}
import com.tjclp.xlcr.bridges.aspose.email._
import com.tjclp.xlcr.bridges.aspose.excel._
import com.tjclp.xlcr.bridges.aspose.powerpoint._
import com.tjclp.xlcr.bridges.aspose.word._
import com.tjclp.xlcr.types.MimeType
import com.tjclp.xlcr.utils.aspose._

/**
 * Provides the Aspose bridges and splitters for registration via ServiceLoader.
 * These have HIGH priority and will override core implementations if Aspose is available.
 */
class AsposeRegistrations extends BridgeProvider with SplitterProvider {

  override def getBridges: Iterable[BridgeInfo] = Seq(
    // Word -> PDF
    BridgeInfo(MimeType.ApplicationMsWord, MimeType.ApplicationPdf, WordDocToPdfAsposeBridge),
    BridgeInfo(MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument, MimeType.ApplicationPdf, WordDocxToPdfAsposeBridge),

    // Excel -> PDF
    BridgeInfo(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, MimeType.ApplicationPdf, ExcelXlsxToPdfAsposeBridge),
    BridgeInfo(MimeType.ApplicationVndMsExcel, MimeType.ApplicationPdf, ExcelXlsToPdfAsposeBridge),
    BridgeInfo(MimeType.ApplicationVndMsExcelSheetMacroEnabled, MimeType.ApplicationPdf, ExcelXlsmToPdfAsposeBridge),
    BridgeInfo(MimeType.ApplicationVndMsExcelSheetBinary, MimeType.ApplicationPdf, ExcelXlsbToPdfAsposeBridge),
    BridgeInfo(MimeType.ApplicationVndOasisOpendocumentSpreadsheet, MimeType.ApplicationPdf, OdsToPdfAsposeBridge),

    // Email -> PDF
    BridgeInfo(MimeType.MessageRfc822, MimeType.ApplicationPdf, EmailEmlToPdfAsposeBridge),
    BridgeInfo(MimeType.ApplicationVndMsOutlook, MimeType.ApplicationPdf, OutlookMsgToPdfAsposeBridge),

    // PowerPoint -> PDF
    BridgeInfo(MimeType.ApplicationVndMsPowerpoint, MimeType.ApplicationPdf, PowerPointPptToPdfAsposeBridge),
    BridgeInfo(MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation, MimeType.ApplicationPdf, PowerPointPptxToPdfAsposeBridge)
  )

  override def getSplitters: Iterable[SplitterInfo] = Seq(
    // Excel Splitters
    SplitterInfo(MimeType.ApplicationVndMsExcel, ExcelSheetXlsAsposeSplitter),
    SplitterInfo(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, ExcelXSheetAsposeSplitter),
    SplitterInfo(MimeType.ApplicationVndMsExcelSheetMacroEnabled, ExcelXlsmSheetAsposeSplitter),
    SplitterInfo(MimeType.ApplicationVndMsExcelSheetBinary, ExcelXlsbSheetAsposeSplitter),

    // PowerPoint Splitters
    SplitterInfo(MimeType.ApplicationVndMsPowerpoint, PowerPointSlideAsposeSplitter),
    SplitterInfo(MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation, PowerPointXSlideAsposeSplitter),

    // Email Splitters
    SplitterInfo(MimeType.MessageRfc822, EmailAttachmentAsposeSplitter),
    SplitterInfo(MimeType.ApplicationVndMsOutlook, OutlookMsgAsposeSplitter),

    // Archive Splitters
    SplitterInfo(MimeType.ApplicationZip, ZipArchiveAsposeSplitter),
    SplitterInfo(MimeType.ApplicationSevenz, SevenZipArchiveAsposeSplitter)
  )
}