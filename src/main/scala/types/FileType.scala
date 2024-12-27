package com.tjclp.xlcr
package types

enum FileType(val extension: Extension, val mimeType: MimeType):

  // Word File Types
  case WordDocx extends FileType(Extension.DOCX, MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument)
  case WordDoc extends FileType(Extension.DOC, MimeType.ApplicationMsWord)

  // Excel File Types
  case ExcelXlsx extends FileType(Extension.XLSX, MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
  case ExcelXls extends FileType(Extension.XLS, MimeType.ApplicationVndMsExcel)
  case ExcelXlsb extends FileType(Extension.XLSB, MimeType.ApplicationVndMsExcel)
  case ExcelXlsm extends FileType(Extension.XLSM, MimeType.ApplicationVndMsExcel)

  // PowerPoint File Types
  case PowerPointPptx extends FileType(Extension.PPTX, MimeType.ApplicationVndMsPowerpoint)
  case PowerPointPpt extends FileType(Extension.PPT, MimeType.ApplicationVndMsPowerpoint)

  // PDF File Type
  case PDF extends FileType(Extension.PDF, MimeType.ApplicationPdf)

  // Email File Types
  case EmailEml extends FileType(Extension.EML, MimeType.MessageRfc822)
  case EmailMsg extends FileType(Extension.MSG, MimeType.MessageRfc822)

  // Archive File Type
  case Zip extends FileType(Extension.ZIP, MimeType.ApplicationZip)

  // Text File Type
  case TextTxt extends FileType(Extension.TXT, MimeType.TextPlain)

  // Image File Types
  case ImageJpg extends FileType(Extension.JPG, MimeType.ImageJpg)
  case ImageJpeg extends FileType(Extension.JPEG, MimeType.ImageJpeg)
  case ImagePng extends FileType(Extension.PNG, MimeType.ImagePng)
  case ImageGif extends FileType(Extension.GIF, MimeType.ImageGif)
  case ImageBmp extends FileType(Extension.BMP, MimeType.ImageBmp)

  // HTML File Types
  case HTML extends FileType(Extension.HTML, MimeType.TextHtml)
  case HTM extends FileType(Extension.HTM, MimeType.TextHtml)

  // Data File Types
  case XML extends FileType(Extension.XML, MimeType.ApplicationXml)
  case JSON extends FileType(Extension.JSON, MimeType.ApplicationJson)
  case MD extends FileType(Extension.MD, MimeType.TextMarkdown)

  def getExtension: Extension = this.extension

  def getMimeType: MimeType = this.mimeType

object FileType:
  def fromExtension(ext: String): Option[FileType] =
    Extension.values.find(_.extension.equalsIgnoreCase(ext)).flatMap { extObj =>
      FileType.values.find(_.extension == extObj)
    }