package com.tjclp.xlcr
package registration

import bridges.email.{EmailEmlToPdfAsposeBridge, OutlookMsgToPdfAsposeBridge}
import bridges.excel._
import bridges.powerpoint.{
  PowerPointPptToPdfAsposeBridge,
  PowerPointPptxToPdfAsposeBridge
}
import bridges.word.{WordDocToPdfAsposeBridge, WordDocxToPdfAsposeBridge}
import spi.{BridgeInfo, BridgeProvider, SplitterInfo, SplitterProvider}
import splitters.archive._
import splitters.email._
import splitters.excel._
import splitters.pdf.PdfPageAsposeSplitter
import splitters.powerpoint._
import types.MimeType

/** Provides the Aspose bridges and splitters for registration via ServiceLoader.
  * These have HIGH priority and will override core implementations if Aspose is available.
  */
class AsposeRegistrations extends BridgeProvider with SplitterProvider {

  override def getBridges: Iterable[BridgeInfo[_ <: MimeType, _ <: MimeType]] = Seq(
    // Word -> PDF
    BridgeInfo[MimeType.ApplicationMsWord.type, MimeType.ApplicationPdf.type](
      MimeType.ApplicationMsWord,
      MimeType.ApplicationPdf,
      WordDocToPdfAsposeBridge
    ),
    BridgeInfo[MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type, MimeType.ApplicationPdf.type](
      MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument,
      MimeType.ApplicationPdf,
      WordDocxToPdfAsposeBridge
    ),
    // Excel -> PDF
    BridgeInfo[MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type, MimeType.ApplicationPdf.type](
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      MimeType.ApplicationPdf,
      ExcelXlsxToPdfAsposeBridge
    ),
    BridgeInfo[MimeType.ApplicationVndMsExcel.type, MimeType.ApplicationPdf.type](
      MimeType.ApplicationVndMsExcel,
      MimeType.ApplicationPdf,
      ExcelXlsToPdfAsposeBridge
    ),
    BridgeInfo[MimeType.ApplicationVndMsExcelSheetMacroEnabled.type, MimeType.ApplicationPdf.type](
      MimeType.ApplicationVndMsExcelSheetMacroEnabled,
      MimeType.ApplicationPdf,
      ExcelXlsmToPdfAsposeBridge
    ),
    BridgeInfo[MimeType.ApplicationVndMsExcelSheetBinary.type, MimeType.ApplicationPdf.type](
      MimeType.ApplicationVndMsExcelSheetBinary,
      MimeType.ApplicationPdf,
      ExcelXlsbToPdfAsposeBridge
    ),
    BridgeInfo[MimeType.ApplicationVndOasisOpendocumentSpreadsheet.type, MimeType.ApplicationPdf.type](
      MimeType.ApplicationVndOasisOpendocumentSpreadsheet,
      MimeType.ApplicationPdf,
      OdsToPdfAsposeBridge
    ),
    // Email -> PDF
    BridgeInfo[MimeType.MessageRfc822.type, MimeType.ApplicationPdf.type](
      MimeType.MessageRfc822,
      MimeType.ApplicationPdf,
      EmailEmlToPdfAsposeBridge
    ),
    BridgeInfo[MimeType.ApplicationVndMsOutlook.type, MimeType.ApplicationPdf.type](
      MimeType.ApplicationVndMsOutlook,
      MimeType.ApplicationPdf,
      OutlookMsgToPdfAsposeBridge
    ),
    // PowerPoint -> PDF
    BridgeInfo[MimeType.ApplicationVndMsPowerpoint.type, MimeType.ApplicationPdf.type](
      MimeType.ApplicationVndMsPowerpoint,
      MimeType.ApplicationPdf,
      PowerPointPptToPdfAsposeBridge
    ),
    BridgeInfo[MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation.type, MimeType.ApplicationPdf.type](
      MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation,
      MimeType.ApplicationPdf,
      PowerPointPptxToPdfAsposeBridge
    )
  )

  override def getSplitters: Iterable[SplitterInfo] = Seq(
    // Excel Splitters
    SplitterInfo(MimeType.ApplicationVndMsExcel, ExcelXlsSheetAsposeSplitter),
    SplitterInfo(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      ExcelXlsxSheetAsposeSplitter
    ),
    SplitterInfo(
      MimeType.ApplicationVndMsExcelSheetMacroEnabled,
      ExcelXlsmSheetAsposeSplitter
    ),
    SplitterInfo(
      MimeType.ApplicationVndMsExcelSheetBinary,
      ExcelXlsbSheetAsposeSplitter
    ),
    SplitterInfo(
      MimeType.ApplicationVndOasisOpendocumentSpreadsheet,
      OdsSheetAsposeSplitter
    ),
    // PowerPoint Splitters
    SplitterInfo(
      MimeType.ApplicationVndMsPowerpoint,
      PowerPointPptSlideAsposeSplitter
    ),
    SplitterInfo(
      MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation,
      PowerPointPptxSlideAsposeSplitter
    ),
    // Email Splitters
    SplitterInfo(MimeType.MessageRfc822, EmailAttachmentAsposeSplitter),
    SplitterInfo(MimeType.ApplicationVndMsOutlook, OutlookMsgAsposeSplitter),
    // Archive Splitters
    SplitterInfo(MimeType.ApplicationZip, ZipArchiveAsposeSplitter),
    SplitterInfo(MimeType.ApplicationSevenz, SevenZipArchiveAsposeSplitter),
    // PDF Splitters
    SplitterInfo(MimeType.ApplicationPdf, PdfPageAsposeSplitter)
  )
}
