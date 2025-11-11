package com.tjclp.xlcr
package registration

import bridges.excel.{
  ExcelXlsToPdfLibreOfficeBridge,
  ExcelXlsmToPdfLibreOfficeBridge,
  ExcelXlsxToPdfLibreOfficeBridge
}
import bridges.odf.OdsToPdfLibreOfficeBridge
import bridges.powerpoint.{
  PowerPointPptToPdfLibreOfficeBridge,
  PowerPointPptxToPdfLibreOfficeBridge
}
import bridges.word.{ WordDocToPdfLibreOfficeBridge, WordDocxToPdfLibreOfficeBridge }
import spi.{ BridgeInfo, BridgeProvider }
import types.MimeType

/**
 * Provides the LibreOffice bridges for registration via ServiceLoader. These have DEFAULT priority
 * and will act as fallbacks when Aspose (HIGH priority) is not available.
 *
 * Supported conversions:
 *   - Word (DOC, DOCX) → PDF
 *   - Excel (XLS, XLSX, XLSM) → PDF
 *   - PowerPoint (PPT, PPTX) → PDF
 *   - OpenDocument Spreadsheet (ODS) → PDF
 *
 * All bridges use LibreOffice via JODConverter for document conversions. LibreOffice must be
 * installed on the system for these bridges to work.
 */
class LibreOfficeRegistrations extends BridgeProvider {

  override def getBridges: Iterable[BridgeInfo[_ <: MimeType, _ <: MimeType]] =
    Seq(
      // Word -> PDF
      BridgeInfo(
        MimeType.ApplicationMsWord,
        MimeType.ApplicationPdf,
        WordDocToPdfLibreOfficeBridge
      ),
      BridgeInfo(
        MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument,
        MimeType.ApplicationPdf,
        WordDocxToPdfLibreOfficeBridge
      ),
      // Excel -> PDF
      BridgeInfo(
        MimeType.ApplicationVndMsExcel,
        MimeType.ApplicationPdf,
        ExcelXlsToPdfLibreOfficeBridge
      ),
      BridgeInfo(
        MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
        MimeType.ApplicationPdf,
        ExcelXlsxToPdfLibreOfficeBridge
      ),
      BridgeInfo(
        MimeType.ApplicationVndMsExcelSheetMacroEnabled,
        MimeType.ApplicationPdf,
        ExcelXlsmToPdfLibreOfficeBridge
      ),
      // PowerPoint -> PDF
      BridgeInfo(
        MimeType.ApplicationVndMsPowerpoint,
        MimeType.ApplicationPdf,
        PowerPointPptToPdfLibreOfficeBridge
      ),
      BridgeInfo(
        MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation,
        MimeType.ApplicationPdf,
        PowerPointPptxToPdfLibreOfficeBridge
      ),
      // OpenDocument Spreadsheet -> PDF (LibreOffice native format)
      BridgeInfo(
        MimeType.ApplicationVndOasisOpendocumentSpreadsheet,
        MimeType.ApplicationPdf,
        OdsToPdfLibreOfficeBridge
      )
    )
}
