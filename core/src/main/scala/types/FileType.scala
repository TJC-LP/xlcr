package com.tjclp.xlcr
package types

sealed abstract class FileType(val extension: Extension, val mimeType: MimeType) {
  def getExtension: Extension = this.extension
  def getMimeType: MimeType   = this.mimeType
}

object FileType {
  // Word File Types
  case object WordDocx extends FileType(
        Extension.DOCX,
        MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument
      )
  case object WordDoc extends FileType(Extension.DOC, MimeType.ApplicationMsWord)

  // Excel File Types
  case object ExcelXlsx
      extends FileType(Extension.XLSX, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
  case object ExcelXls  extends FileType(Extension.XLS, MimeType.ApplicationVndMsExcel)
  case object ExcelXlsb extends FileType(Extension.XLSB, MimeType.ApplicationVndMsExcelSheetBinary)
  case object ExcelXlsm
      extends FileType(Extension.XLSM, MimeType.ApplicationVndMsExcelSheetMacroEnabled)
  case object ExcelOds
      extends FileType(Extension.ODS, MimeType.ApplicationVndOasisOpendocumentSpreadsheet)

  // PowerPoint File Types
  case object PowerPointPptx extends FileType(
        Extension.PPTX,
        MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation
      )
  case object PowerPointPpt extends FileType(Extension.PPT, MimeType.ApplicationVndMsPowerpoint)

  // PDF File Type
  case object PDF extends FileType(Extension.PDF, MimeType.ApplicationPdf)

  // Email File Types
  case object EmailEml extends FileType(Extension.EML, MimeType.MessageRfc822)
  case object EmailMsg extends FileType(Extension.MSG, MimeType.MessageRfc822)

  // Archive File Types
  case object Zip    extends FileType(Extension.ZIP, MimeType.ApplicationZip)
  case object Gzip   extends FileType(Extension.GZIP, MimeType.ApplicationGzip)
  case object SevenZ extends FileType(Extension.SEVENZ, MimeType.ApplicationSevenz)
  case object Tar    extends FileType(Extension.TAR, MimeType.ApplicationTar)
  case object Bzip2  extends FileType(Extension.BZIP2, MimeType.ApplicationBzip2)
  case object TarXz  extends FileType(Extension.TARXZ, MimeType.ApplicationTar)
  case object Xz     extends FileType(Extension.XZ, MimeType.ApplicationXz)

  // Text File Type
  case object TextTxt extends FileType(Extension.TXT, MimeType.TextPlain)

  // Image File Types
  case object ImageJpg  extends FileType(Extension.JPG, MimeType.ImageJpeg)
  case object ImageJpeg extends FileType(Extension.JPEG, MimeType.ImageJpeg)
  case object ImagePng  extends FileType(Extension.PNG, MimeType.ImagePng)
  case object ImageGif  extends FileType(Extension.GIF, MimeType.ImageGif)
  case object ImageBmp  extends FileType(Extension.BMP, MimeType.ImageBmp)

  // HTML File Types
  case object HTML extends FileType(Extension.HTML, MimeType.TextHtml)
  case object HTM  extends FileType(Extension.HTM, MimeType.TextHtml)

  // Data File Types
  // XML
  case object XML  extends FileType(Extension.XML, MimeType.ApplicationXml)
  case object JSON extends FileType(Extension.JSON, MimeType.ApplicationJson)
  case object MD   extends FileType(Extension.MD, MimeType.TextMarkdown)
  case object SVG  extends FileType(Extension.SVG, MimeType.ImageSvgXml)

  // Get all FileType values
  val values: Seq[FileType] = Seq(
    WordDocx,
    WordDoc,
    ExcelXlsx,
    ExcelXls,
    ExcelXlsb,
    ExcelXlsm,
    ExcelOds,
    PowerPointPptx,
    PowerPointPpt,
    PDF,
    EmailEml,
    EmailMsg,
    Zip,
    Gzip,
    SevenZ,
    Tar,
    Bzip2,
    TarXz,
    Xz,
    TextTxt,
    ImageJpg,
    ImageJpeg,
    ImagePng,
    ImageGif,
    ImageBmp,
    HTML,
    HTM,
    XML,
    JSON,
    MD,
    SVG
  )

  def fromExtension(ext: String): Option[FileType] =
    Extension.fromString(ext).flatMap { extObj =>
      values.find(_.extension == extObj)
    }
}
