package com.tjclp.xlcr.core

import com.tjclp.xlcr.transform.*
import com.tjclp.xlcr.types.Mime

/**
 * Given instances for XLCR Core transforms.
 *
 * Import these givens to enable compile-time path resolution:
 * {{{
 * import com.tjclp.xlcr.core.given
 * }}}
 */

given xlcrAnyToPlainText: Conversion[Mime, Mime.Plain] =
  XlcrConversions.anyToPlainText

given xlcrAnyToXml: Conversion[Mime, Mime.Xml] =
  XlcrConversions.anyToXml

given xlcrXlsxToOds: Conversion[Mime.Xlsx, Mime.Ods] =
  XlcrConversions.xlsxToOds

given xlcrXlsxSheetSplitter: Splitter[Mime.Xlsx, Mime.Xlsx] =
  XlcrSplitters.xlsxSheetSplitter

given xlcrXlsSheetSplitter: Splitter[Mime.Xls, Mime.Xls] =
  XlcrSplitters.xlsSheetSplitter

given xlcrOdsSheetSplitter: Splitter[Mime.Ods, Mime.Ods] =
  XlcrSplitters.odsSheetSplitter

given xlcrPptxSlideSplitter: Splitter[Mime.Pptx, Mime.Pptx] =
  XlcrSplitters.pptxSlideSplitter

given xlcrDocxSectionSplitter: Splitter[Mime.Docx, Mime.Docx] =
  XlcrSplitters.docxSectionSplitter

given xlcrPdfPageSplitter: Splitter[Mime.Pdf, Mime.Pdf] =
  XlcrSplitters.pdfPageSplitter

given xlcrTextSplitter: DynamicSplitter[Mime.Plain] =
  XlcrSplitters.textSplitter

given xlcrCsvSplitter: DynamicSplitter[Mime.Csv] =
  XlcrSplitters.csvSplitter

given xlcrEmlAttachmentSplitter: DynamicSplitter[Mime.Eml] =
  XlcrSplitters.emlAttachmentSplitter

given xlcrMsgAttachmentSplitter: DynamicSplitter[Mime.Msg] =
  XlcrSplitters.msgAttachmentSplitter

given xlcrZipEntrySplitter: DynamicSplitter[Mime.Zip] =
  XlcrSplitters.zipEntrySplitter
