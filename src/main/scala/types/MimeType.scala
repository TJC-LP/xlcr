package com.tjclp.xlcr
package types

enum MimeType(val mimeType: String):
  case ApplicationMsWord extends MimeType("application/msword")
  case ApplicationVndOpenXmlFormatsWordprocessingmlDocument extends MimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
  case ApplicationVndMsExcel extends MimeType("application/vnd.ms-excel")
  case ApplicationVndMsPowerpoint extends MimeType("application/vnd.ms-powerpoint")
  case ApplicationPdf extends MimeType("application/pdf")
  case MessageRfc822 extends MimeType("message/rfc822")
  case ApplicationZip extends MimeType("application/zip")
  case TextPlain extends MimeType("text/plain")
  case ImageJpeg extends MimeType("image/jpeg")
  case ImagePng extends MimeType("image/png")
  case ImageGif extends MimeType("image/gif")
  case ImageBmp extends MimeType("image/bmp")
  case TextHtml extends MimeType("text/html")
  case ApplicationXml extends MimeType("application/xml")
  case ApplicationJson extends MimeType("application/json")
  case ApplicationVndOpenXmlFormatsSpreadsheetmlSheet extends MimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  case TextMarkdown extends MimeType("text/markdown")
  case ImageSvgXml extends MimeType("image/svg+xml") // newly added