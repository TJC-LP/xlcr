package com.tjclp.xlcr.v2.server

import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.pdfbox.pdmodel.{ PDDocument, PDPage }
import org.apache.pdfbox.pdmodel.common.PDRectangle

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import zio._
import zio.http._
import zio.json._

import com.tjclp.xlcr.v2.server.routes.Routes
import com.tjclp.xlcr.v2.server.json._
import com.tjclp.xlcr.v2.types.Mime

/**
 * Integration tests for the XLCR HTTP Server.
 *
 * Tests all endpoints with real document transformations.
 */
class ServerSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll:

  import Codecs.given

  // Helper to run ZIO effects
  private val runtime = Runtime.default

  private def runZIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(effect).getOrThrowFiberFailure()
    }

  // Test app for route testing
  private val app = Routes.all

  // Document generators
  private def createXlsx(sheets: String*): Array[Byte] =
    val workbook = new XSSFWorkbook()
    val names    = if sheets.isEmpty then Seq("Sheet1") else sheets
    names.foreach { name =>
      val sheet = workbook.createSheet(name)
      val row   = sheet.createRow(0)
      row.createCell(0).setCellValue(s"Data in $name")
    }
    val baos = new ByteArrayOutputStream()
    workbook.write(baos)
    workbook.close()
    baos.toByteArray

  private def createPdf(pages: Int): Array[Byte] =
    val doc = new PDDocument()
    (1 to pages).foreach(_ => doc.addPage(new PDPage(PDRectangle.A4)))
    val baos = new ByteArrayOutputStream()
    doc.save(baos)
    doc.close()
    baos.toByteArray

  private def makeRequest(
    method: Method,
    path: String,
    body: Option[Array[Byte]] = None,
    contentType: Option[String] = None
  ): Request =
    val url = URL.decode(path).getOrElse(URL.empty)
    val headers = contentType match
      case Some(ct) => Headers(Header.ContentType(MediaType.forContentType(ct).get))
      case None     => Headers.empty
    val bodyContent = body match
      case Some(bytes) => Body.fromChunk(Chunk.fromArray(bytes))
      case None        => Body.empty
    Request(method = method, url = url, headers = headers, body = bodyContent)

  private def executeRequest(request: Request): Response =
    runZIO(app.runZIO(request))

  // ============================================================================
  // Health Check Tests
  // ============================================================================

  "GET /health" should "return healthy status" in {
    val request  = makeRequest(Method.GET, "/health")
    val response = executeRequest(request)

    response.status shouldBe Status.Ok

    val body = runZIO(response.body.asString)
    body should include("healthy")
  }

  // ============================================================================
  // Capabilities Tests
  // ============================================================================

  "GET /capabilities" should "return capabilities JSON" in {
    val request  = makeRequest(Method.GET, "/capabilities")
    val response = executeRequest(request)

    response.status shouldBe Status.Ok

    val body = runZIO(response.body.asString)
    val capabilities = body.fromJson[CapabilitiesResponse]

    capabilities.isRight shouldBe true
    capabilities.toOption.get.conversions should not be empty
    capabilities.toOption.get.splits should not be empty
  }

  it should "include text/plain conversions" in {
    val request  = makeRequest(Method.GET, "/capabilities")
    val response = executeRequest(request)
    val body     = runZIO(response.body.asString)
    val caps     = body.fromJson[CapabilitiesResponse].toOption.get

    // All backends support text extraction
    caps.conversions.exists(_.to == "text/plain") shouldBe true
  }

  // ============================================================================
  // Convert Endpoint Tests
  // ============================================================================

  "POST /convert" should "reject requests without 'to' parameter" in {
    val xlsx     = createXlsx("Sheet1")
    val request  = makeRequest(Method.POST, "/convert", Some(xlsx), Some(Mime.xlsx.value))
    val response = executeRequest(request)

    response.status shouldBe Status.BadRequest
  }

  it should "convert XLSX to text/plain" in {
    val xlsx = createXlsx("TestSheet")
    val request = makeRequest(
      Method.POST,
      "/convert?to=txt",
      Some(xlsx),
      Some(Mime.xlsx.value)
    )
    val response = executeRequest(request)

    response.status shouldBe Status.Ok

    val body = runZIO(response.body.asString)
    body should include("TestSheet")
  }

  it should "convert PDF to text/plain" in {
    val pdf = createPdf(1)
    val request = makeRequest(
      Method.POST,
      "/convert?to=plain",
      Some(pdf),
      Some(Mime.pdf.value)
    )
    val response = executeRequest(request)

    // Should succeed even if PDF has no text
    response.status shouldBe Status.Ok
  }

  it should "accept full MIME type in 'to' parameter" in {
    val xlsx = createXlsx("Sheet1")
    val request = makeRequest(
      Method.POST,
      "/convert?to=text/plain",
      Some(xlsx),
      Some(Mime.xlsx.value)
    )
    val response = executeRequest(request)

    response.status shouldBe Status.Ok
  }

  it should "reject empty body" in {
    val request  = makeRequest(Method.POST, "/convert?to=pdf")
    val response = executeRequest(request)

    response.status shouldBe Status.BadRequest
  }

  // ============================================================================
  // Split Endpoint Tests
  // ============================================================================

  "POST /split" should "split XLSX into ZIP of sheets" in {
    val xlsx     = createXlsx("Sheet1", "Sheet2", "Sheet3")
    val request  = makeRequest(Method.POST, "/split", Some(xlsx), Some(Mime.xlsx.value))
    val response = executeRequest(request)

    response.status shouldBe Status.Ok

    // Verify response is a ZIP
    response.header(Header.ContentType).map(_.mediaType) shouldBe Some(MediaType.application.zip)

    // Verify ZIP contains 3 entries
    val zipBytes = runZIO(response.body.asChunk)
    val entries  = extractZipEntries(zipBytes.toArray)

    entries.size shouldBe 3
    entries.exists(_.contains("Sheet1")) shouldBe true
    entries.exists(_.contains("Sheet2")) shouldBe true
    entries.exists(_.contains("Sheet3")) shouldBe true
  }

  it should "split PDF into ZIP of pages" in {
    val pdf      = createPdf(3)
    val request  = makeRequest(Method.POST, "/split", Some(pdf), Some(Mime.pdf.value))
    val response = executeRequest(request)

    response.status shouldBe Status.Ok

    val zipBytes = runZIO(response.body.asChunk)
    val entries  = extractZipEntries(zipBytes.toArray)

    entries.size shouldBe 3
  }

  it should "reject unsupported MIME types" in {
    val request = makeRequest(
      Method.POST,
      "/split",
      Some("not a real document".getBytes),
      Some("audio/mpeg")
    )
    val response = executeRequest(request)

    response.status shouldBe Status.UnsupportedMediaType
  }

  // ============================================================================
  // Info Endpoint Tests
  // ============================================================================

  "POST /info" should "return document info for XLSX" in {
    val xlsx     = createXlsx("Sheet1", "Sheet2")
    val request  = makeRequest(Method.POST, "/info", Some(xlsx), Some(Mime.xlsx.value))
    val response = executeRequest(request)

    response.status shouldBe Status.Ok

    val body = runZIO(response.body.asString)
    val info = body.fromJson[InfoResponse]

    info.isRight shouldBe true
    val infoResponse = info.toOption.get
    infoResponse.mimeType shouldBe Mime.xlsx.value
    infoResponse.canSplit shouldBe true
    infoResponse.fragmentCount shouldBe Some(2)
    infoResponse.availableConversions should contain("text/plain")
  }

  it should "return document info for PDF" in {
    val pdf      = createPdf(5)
    val request  = makeRequest(Method.POST, "/info", Some(pdf), Some(Mime.pdf.value))
    val response = executeRequest(request)

    response.status shouldBe Status.Ok

    val body         = runZIO(response.body.asString)
    val infoResponse = body.fromJson[InfoResponse].toOption.get

    infoResponse.mimeType shouldBe Mime.pdf.value
    infoResponse.canSplit shouldBe true
    infoResponse.fragmentCount shouldBe Some(5)
  }

  // ============================================================================
  // Error Handling Tests
  // ============================================================================

  "Error responses" should "return JSON error format" in {
    val request  = makeRequest(Method.POST, "/convert?to=pdf")
    val response = executeRequest(request)

    val body  = runZIO(response.body.asString)
    val error = body.fromJson[ErrorResponse]

    error.isRight shouldBe true
    error.toOption.get.status shouldBe response.status.code
  }

  // ============================================================================
  // Root Endpoint Tests
  // ============================================================================

  "GET /" should "return server info" in {
    val request  = makeRequest(Method.GET, "/")
    val response = executeRequest(request)

    response.status shouldBe Status.Ok

    val body = runZIO(response.body.asString)
    body should include("XLCR Server")
    body should include("/convert")
    body should include("/split")
  }

  // ============================================================================
  // Helpers
  // ============================================================================

  private def extractZipEntries(zipBytes: Array[Byte]): List[String] =
    val zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))
    var entries = List.empty[String]
    var entry   = zis.getNextEntry
    while entry != null do
      entries = entries :+ entry.getName
      entry = zis.getNextEntry
    zis.close()
    entries
