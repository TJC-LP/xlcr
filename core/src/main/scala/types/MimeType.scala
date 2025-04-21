package com.tjclp.xlcr
package types

sealed abstract class MimeType(val mimeType: String)

object MimeType {
  // Text MIME types
  case object TextPlain extends MimeType("text/plain")
  case object TextHtml extends MimeType("text/html")
  case object TextMarkdown extends MimeType("text/markdown")

  // Application MIME types
  case object ApplicationJson extends MimeType("application/json")
  case object ApplicationXml extends MimeType("application/xml")
  case object ApplicationPdf extends MimeType("application/pdf")
  case object ApplicationZip extends MimeType("application/zip")
  case object ApplicationVndMsOutlook extends MimeType("application/vnd.ms-outlook")
  case object ApplicationOctet extends MimeType("application/octet-stream")
  case object ApplicationMsWord extends MimeType("application/msword")
  case object ApplicationVndMsExcel extends MimeType("application/vnd.ms-excel")
  case object ApplicationVndMsPowerpoint extends MimeType("application/vnd.ms-powerpoint")
  case object ApplicationVndOpenXmlFormatsWordprocessingmlDocument extends MimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
  case object ApplicationVndOpenXmlFormatsSpreadsheetmlSheet extends MimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  case object ApplicationVndOpenXmlFormatsPresentationmlPresentation extends MimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation")

  // Image MIME types
  case object ImageJpeg extends MimeType("image/jpeg")
  case object ImagePng extends MimeType("image/png")
  case object ImageGif extends MimeType("image/gif")
  case object ImageBmp extends MimeType("image/bmp")
  case object ImageSvgXml extends MimeType("image/svg+xml")

  // Message MIME types
  case object MessageRfc822 extends MimeType("message/rfc822")

  // Get all MimeType values
  val values: Seq[MimeType] = Seq(
    TextPlain, TextHtml, TextMarkdown,
    ApplicationJson, ApplicationXml, ApplicationPdf, ApplicationZip, ApplicationOctet,
    ApplicationVndMsOutlook,
    ApplicationMsWord, ApplicationVndMsExcel, ApplicationVndMsPowerpoint,
    ApplicationVndOpenXmlFormatsWordprocessingmlDocument,
    ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
    ApplicationVndOpenXmlFormatsPresentationmlPresentation,
    ImageJpeg, ImagePng, ImageGif, ImageBmp, ImageSvgXml,
    MessageRfc822
  )

  def fromString(mimeTypeStr: String): Option[MimeType] = {
    values.find(_.mimeType == mimeTypeStr)
  }
}
