package com.tjclp.xlcr.v2.types

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests for the Mime opaque type system.
 */
class MimeSpec extends AnyFlatSpec with Matchers:

  // ============================================================================
  // Basic Value Tests
  // ============================================================================

  "Mime.value" should "return the raw MIME string" in {
    Mime.pdf.value shouldBe "application/pdf"
    Mime.html.value shouldBe "text/html"
    Mime.xlsx.value shouldBe "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  }

  it should "work with custom MIME types" in {
    val custom = Mime("application/x-custom")
    custom.value shouldBe "application/x-custom"
  }

  // ============================================================================
  // Type Extraction Tests
  // ============================================================================

  "Mime.baseType" should "extract the base type before the slash" in {
    Mime.pdf.baseType shouldBe "application"
    Mime.html.baseType shouldBe "text"
    Mime.jpeg.baseType shouldBe "image"
    Mime.mp3.baseType shouldBe "audio"
    Mime.mp4.baseType shouldBe "video"
  }

  "Mime.subType" should "extract the subtype after the slash" in {
    Mime.pdf.subType shouldBe "pdf"
    Mime.html.subType shouldBe "html"
    Mime.jpeg.subType shouldBe "jpeg"
    Mime.json.subType shouldBe "json"
  }

  it should "handle complex subtypes" in {
    Mime.xlsx.subType shouldBe "vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    Mime.docx.subType shouldBe "vnd.openxmlformats-officedocument.wordprocessingml.document"
  }

  it should "strip parameters from MIME types" in {
    val withParams = Mime("text/html; charset=utf-8")
    withParams.subType shouldBe "html"
  }

  "Mime.mimeType" should "return full type without parameters" in {
    Mime.pdf.mimeType shouldBe "application/pdf"
    val withParams = Mime("text/plain; charset=utf-8")
    withParams.mimeType shouldBe "text/plain"
  }

  // ============================================================================
  // Type Predicate Tests
  // ============================================================================

  "Mime.isText" should "return true for text types" in {
    Mime.plain.isText shouldBe true
    Mime.html.isText shouldBe true
    Mime.csv.isText shouldBe true
    Mime.markdown.isText shouldBe true
    Mime.css.isText shouldBe true
    Mime.javascript.isText shouldBe true
  }

  it should "return false for non-text types" in {
    Mime.pdf.isText shouldBe false
    Mime.xlsx.isText shouldBe false
    Mime.jpeg.isText shouldBe false
  }

  "Mime.isApplication" should "return true for application types" in {
    Mime.pdf.isApplication shouldBe true
    Mime.json.isApplication shouldBe true
    Mime.xlsx.isApplication shouldBe true
    Mime.zip.isApplication shouldBe true
  }

  it should "return false for non-application types" in {
    Mime.html.isApplication shouldBe false
    Mime.jpeg.isApplication shouldBe false
  }

  "Mime.isImage" should "return true for image types" in {
    Mime.jpeg.isImage shouldBe true
    Mime.png.isImage shouldBe true
    Mime.gif.isImage shouldBe true
    Mime.svg.isImage shouldBe true
    Mime.webp.isImage shouldBe true
  }

  it should "return false for non-image types" in {
    Mime.pdf.isImage shouldBe false
    Mime.html.isImage shouldBe false
  }

  "Mime.isMessage" should "return true for message types" in {
    Mime.eml.isMessage shouldBe true
  }

  "Mime.isAudio" should "return true for audio types" in {
    Mime.mp3.isAudio shouldBe true
    Mime.wav.isAudio shouldBe true
    Mime.flac.isAudio shouldBe true
    Mime.ogg.isAudio shouldBe true
  }

  "Mime.isVideo" should "return true for video types" in {
    Mime.mp4.isVideo shouldBe true
    Mime.videoWebm.isVideo shouldBe true
    Mime.avi.isVideo shouldBe true
    Mime.mov.isVideo shouldBe true
  }

  // ============================================================================
  // Extension Detection Tests
  // ============================================================================

  "Mime.fromExtension" should "detect common document formats" in {
    Mime.fromExtension("pdf") shouldBe Mime.pdf
    Mime.fromExtension("html") shouldBe Mime.html
    Mime.fromExtension("htm") shouldBe Mime.html
    Mime.fromExtension("txt") shouldBe Mime.plain
    Mime.fromExtension("json") shouldBe Mime.json
    Mime.fromExtension("xml") shouldBe Mime.xml
  }

  it should "detect Microsoft Office formats" in {
    Mime.fromExtension("doc") shouldBe Mime.doc
    Mime.fromExtension("docx") shouldBe Mime.docx
    Mime.fromExtension("xls") shouldBe Mime.xls
    Mime.fromExtension("xlsx") shouldBe Mime.xlsx
    Mime.fromExtension("xlsm") shouldBe Mime.xlsm
    Mime.fromExtension("ppt") shouldBe Mime.ppt
    Mime.fromExtension("pptx") shouldBe Mime.pptx
    Mime.fromExtension("msg") shouldBe Mime.msg
  }

  it should "detect OpenDocument formats" in {
    Mime.fromExtension("odt") shouldBe Mime.odt
    Mime.fromExtension("ods") shouldBe Mime.ods
    Mime.fromExtension("odp") shouldBe Mime.odp
    Mime.fromExtension("odg") shouldBe Mime.odg
    Mime.fromExtension("odf") shouldBe Mime.odf
  }

  it should "detect image formats" in {
    Mime.fromExtension("jpg") shouldBe Mime.jpeg
    Mime.fromExtension("jpeg") shouldBe Mime.jpeg
    Mime.fromExtension("png") shouldBe Mime.png
    Mime.fromExtension("gif") shouldBe Mime.gif
    Mime.fromExtension("svg") shouldBe Mime.svg
    Mime.fromExtension("webp") shouldBe Mime.webp
    Mime.fromExtension("tiff") shouldBe Mime.tiff
    Mime.fromExtension("tif") shouldBe Mime.tiff
    Mime.fromExtension("heic") shouldBe Mime.heic
  }

  it should "detect archive formats" in {
    Mime.fromExtension("zip") shouldBe Mime.zip
    Mime.fromExtension("gz") shouldBe Mime.gzip
    Mime.fromExtension("gzip") shouldBe Mime.gzip
    Mime.fromExtension("7z") shouldBe Mime.sevenZip
    Mime.fromExtension("tar") shouldBe Mime.tar
    Mime.fromExtension("rar") shouldBe Mime.rar
  }

  it should "detect audio/video formats" in {
    Mime.fromExtension("mp3") shouldBe Mime.mp3
    Mime.fromExtension("wav") shouldBe Mime.wav
    Mime.fromExtension("mp4") shouldBe Mime.mp4
    Mime.fromExtension("webm") shouldBe Mime.videoWebm
    Mime.fromExtension("avi") shouldBe Mime.avi
    Mime.fromExtension("mov") shouldBe Mime.mov
  }

  it should "handle extensions with leading dot" in {
    Mime.fromExtension(".pdf") shouldBe Mime.pdf
    Mime.fromExtension(".xlsx") shouldBe Mime.xlsx
  }

  it should "be case-insensitive" in {
    Mime.fromExtension("PDF") shouldBe Mime.pdf
    Mime.fromExtension("XLSX") shouldBe Mime.xlsx
    Mime.fromExtension("Html") shouldBe Mime.html
  }

  it should "return octet-stream for unknown extensions" in {
    Mime.fromExtension("xyz") shouldBe Mime.octet
    Mime.fromExtension("unknown") shouldBe Mime.octet
  }

  // ============================================================================
  // Filename Detection Tests
  // ============================================================================

  "Mime.fromFilename" should "extract extension and detect MIME" in {
    Mime.fromFilename("document.pdf") shouldBe Mime.pdf
    Mime.fromFilename("spreadsheet.xlsx") shouldBe Mime.xlsx
    Mime.fromFilename("report.docx") shouldBe Mime.docx
    Mime.fromFilename("presentation.pptx") shouldBe Mime.pptx
  }

  it should "handle paths with multiple dots" in {
    Mime.fromFilename("my.file.name.pdf") shouldBe Mime.pdf
    Mime.fromFilename("version.1.2.xlsx") shouldBe Mime.xlsx
  }

  it should "handle files without extension" in {
    Mime.fromFilename("README") shouldBe Mime.octet
    Mime.fromFilename("Makefile") shouldBe Mime.octet
  }

  it should "handle dotfiles" in {
    Mime.fromFilename(".gitignore") shouldBe Mime.octet
    Mime.fromFilename(".env") shouldBe Mime.octet
  }

  // ============================================================================
  // String Parsing Tests
  // ============================================================================

  "Mime.parse" should "parse MIME type strings" in {
    Mime.parse("application/pdf").value shouldBe "application/pdf"
    Mime.parse("text/html").value shouldBe "text/html"
  }

  it should "strip parameters" in {
    Mime.parse("text/html; charset=utf-8").value shouldBe "text/html"
    Mime.parse("application/json; charset=UTF-8").value shouldBe "application/json"
  }

  it should "normalize to lowercase" in {
    Mime.parse("Application/PDF").value shouldBe "application/pdf"
    Mime.parse("TEXT/HTML").value shouldBe "text/html"
  }

  "Mime.fromString" should "find known MIME types" in {
    Mime.fromString("application/pdf") shouldBe Some(Mime.pdf)
    Mime.fromString("text/html") shouldBe Some(Mime.html)
    Mime.fromString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") shouldBe Some(Mime.xlsx)
  }

  it should "handle parameters" in {
    Mime.fromString("text/html; charset=utf-8") shouldBe Some(Mime.html)
    Mime.fromString("application/json; charset=UTF-8") shouldBe Some(Mime.json)
  }

  it should "return None for unknown MIME types" in {
    Mime.fromString("application/x-custom") shouldBe None
    Mime.fromString("foo/bar") shouldBe None
  }

  // ============================================================================
  // Values Collection Tests
  // ============================================================================

  "Mime.values" should "contain all predefined MIME types" in {
    Mime.values should contain(Mime.pdf)
    Mime.values should contain(Mime.html)
    Mime.values should contain(Mime.xlsx)
    Mime.values should contain(Mime.docx)
    Mime.values should contain(Mime.pptx)
    Mime.values should contain(Mime.jpeg)
    Mime.values should contain(Mime.png)
    Mime.values should contain(Mime.mp3)
    Mime.values should contain(Mime.mp4)
  }

  it should "contain at least 60 types" in {
    Mime.values.size should be >= 60
  }

  it should "have all valid MIME type strings" in {
    Mime.values.foreach { mime =>
      mime.value should include("/")
      mime.baseType should not be empty
      mime.subType should not be empty
    }
  }

  // ============================================================================
  // Singleton Value Tests
  // ============================================================================

  "Predefined MIME types" should "have correct values" in {
    // Text
    Mime.plain.value shouldBe "text/plain"
    Mime.html.value shouldBe "text/html"
    Mime.markdown.value shouldBe "text/markdown"
    Mime.csv.value shouldBe "text/csv"

    // Application
    Mime.json.value shouldBe "application/json"
    Mime.xml.value shouldBe "application/xml"
    Mime.pdf.value shouldBe "application/pdf"
    Mime.zip.value shouldBe "application/zip"

    // MS Office
    Mime.doc.value shouldBe "application/msword"
    Mime.xls.value shouldBe "application/vnd.ms-excel"
    Mime.ppt.value shouldBe "application/vnd.ms-powerpoint"

    // Images
    Mime.jpeg.value shouldBe "image/jpeg"
    Mime.png.value shouldBe "image/png"
    Mime.gif.value shouldBe "image/gif"

    // Message
    Mime.eml.value shouldBe "message/rfc822"
  }

  // ============================================================================
  // Type Safety Tests (compile-time)
  // ============================================================================

  "Opaque type subtypes" should "be assignable to Mime" in {
    // These assignments should compile
    val pdf: Mime = Mime.pdf
    val html: Mime = Mime.html
    val xlsx: Mime = Mime.xlsx

    // Verify runtime values
    pdf.value shouldBe "application/pdf"
    html.value shouldBe "text/html"
    xlsx.value shouldBe "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  }

  it should "allow Mime to be used where String is expected" in {
    // Mime <: String allows this
    val mimeAsString: String = Mime.pdf
    mimeAsString shouldBe "application/pdf"
  }
