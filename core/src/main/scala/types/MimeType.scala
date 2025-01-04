package com.tjclp.xlcr
package types

sealed trait MimeTypeCategory

sealed trait ApplicationMimeType extends MimeTypeCategory

sealed trait TextMimeType extends MimeTypeCategory

sealed trait ImageMimeType extends MimeTypeCategory

sealed trait MessageMimeType extends MimeTypeCategory

enum MimeType(val mimeType: String):
  // Application types
  case ApplicationMsWord extends MimeType("application/msword") with ApplicationMimeType
  case ApplicationVndOpenXmlFormatsWordprocessingmlDocument extends MimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document") with ApplicationMimeType
  case ApplicationVndMsExcel extends MimeType("application/vnd.ms-excel") with ApplicationMimeType
  case ApplicationVndMsPowerpoint extends MimeType("application/vnd.ms-powerpoint") with ApplicationMimeType
  case ApplicationPdf extends MimeType("application/pdf") with ApplicationMimeType
  case ApplicationZip extends MimeType("application/zip") with ApplicationMimeType
  case ApplicationXml extends MimeType("application/xml") with ApplicationMimeType
  case ApplicationJson extends MimeType("application/json")
  case ApplicationVndOpenXmlFormatsSpreadsheetmlSheet extends MimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") with ApplicationMimeType

  // Text types
  case TextPlain extends MimeType("text/plain") with TextMimeType
  case TextHtml extends MimeType("text/html") with TextMimeType
  case TextMarkdown extends MimeType("text/markdown") with TextMimeType

  // Image types
  case ImageJpeg extends MimeType("image/jpeg") with ImageMimeType
  case ImagePng extends MimeType("image/png") with ImageMimeType
  case ImageGif extends MimeType("image/gif") with ImageMimeType
  case ImageBmp extends MimeType("image/bmp") with ImageMimeType
  case ImageSvgXml extends MimeType("image/svg+xml") with ImageMimeType

  // Message types
  case MessageRfc822 extends MimeType("message/rfc822") with MessageMimeType

  def category: MimeTypeCategory = this.asInstanceOf[MimeTypeCategory]


object MimeType:
  def unsafeFromString(str: String): MimeType =
    fromString(str).getOrElse(
      throw new IllegalArgumentException(s"Invalid mime type: $str")
    )

  def fromString(str: String): Option[MimeType] =
    MimeType.values.find(_.mimeType == str.toLowerCase)

  // You might also want a method that tries to normalize the input
  def normalize(str: String): String = str.trim.toLowerCase