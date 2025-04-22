package com.tjclp.xlcr
package types

sealed abstract class Extension(val extension: String)

object Extension {
  case object DOCX extends Extension("docx")
  case object DOC extends Extension("doc")
  case object XLSX extends Extension("xlsx")
  case object XLS extends Extension("xls")
  case object XLSB extends Extension("xlsb")
  case object XLSM extends Extension("xlsm")
  case object PPTX extends Extension("pptx")
  case object PPT extends Extension("ppt")
  case object PDF extends Extension("pdf")
  case object EML extends Extension("eml")
  case object MSG extends Extension("msg")
  case object ZIP extends Extension("zip")
  case object GZIP extends Extension("gz")
  case object SEVENZ extends Extension("7z")
  case object TAR extends Extension("tar")
  case object BZIP2 extends Extension("bz2")
  case object TARXZ extends Extension("txz")
  case object XZ extends Extension("xz")
  case object TXT extends Extension("txt")
  case object JPG extends Extension("jpg")
  case object JPEG extends Extension("jpeg")
  case object PNG extends Extension("png")
  case object GIF extends Extension("gif")
  case object BMP extends Extension("bmp")
  case object HTML extends Extension("html")
  case object HTM extends Extension("htm")
  case object XML extends Extension("xml")
  case object JSON extends Extension("json")
  case object MD extends Extension("md")
  case object SVG extends Extension("svg")

  // Helper method to get Extension from string
  def fromString(ext: String): Option[Extension] = ext.toLowerCase match {
    case "docx" => Some(DOCX)
    case "doc" => Some(DOC)
    case "xlsx" => Some(XLSX)
    case "xls" => Some(XLS)
    case "xlsb" => Some(XLSB)
    case "xlsm" => Some(XLSM)
    case "pptx" => Some(PPTX)
    case "ppt" => Some(PPT)
    case "pdf" => Some(PDF)
    case "eml" => Some(EML)
    case "msg" => Some(MSG)
    case "zip" => Some(ZIP)
    case "gz" => Some(GZIP)
    case "7z" => Some(SEVENZ)
    case "tar" => Some(TAR)
    case "bz2" => Some(BZIP2)
    case "txz" => Some(TARXZ)
    case "xz" => Some(XZ)
    case "txt" => Some(TXT)
    case "jpg" => Some(JPG)
    case "jpeg" => Some(JPEG)
    case "png" => Some(PNG)
    case "gif" => Some(GIF)
    case "bmp" => Some(BMP)
    case "html" => Some(HTML)
    case "htm" => Some(HTM)
    case "xml" => Some(XML)
    case "json" => Some(JSON)
    case "md" => Some(MD)
    case "svg" => Some(SVG)
    case _ => None
  }

  // Get all extensions
  val values: Seq[Extension] = Seq(
    DOCX, DOC, XLSX, XLS, XLSB, XLSM, PPTX, PPT, PDF, EML, MSG, ZIP, GZIP, SEVENZ, TAR, BZIP2, TARXZ, XZ,
    TXT, JPG, JPEG, PNG, GIF, BMP, HTML, HTM, XML, JSON, MD, SVG
  )
}
