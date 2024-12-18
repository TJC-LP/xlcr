package com.tjclp.xlcr
package types

enum FileType(val extensions: List[String], val mimeType: String):
  case Word extends FileType(List("docx", "doc"), "application/msword")
  case Excel extends FileType(List("xlsx", "xls", "xlsb", "xlsm"), "application/vnd.ms-excel")
  case PowerPoint extends FileType(List("pptx", "ppt"), "application/vnd.ms-powerpoint")
  case PDF extends FileType(List("pdf"), "application/pdf")
  case Email extends FileType(List("eml", "msg"), "message/rfc822")
  case Zip extends FileType(List("zip"), "application/zip")

  def getExtension: String = this.extensions.head
  def getMimeType: String = this.mimeType
  def supportsExtension(ext: String): Boolean = this.extensions.contains(ext.toLowerCase)

object FileType:
  def fromExtension(ext: String): Option[FileType] =
    FileType.values.find(_.supportsExtension(ext))
