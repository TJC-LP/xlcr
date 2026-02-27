package com.tjclp.xlcr.server

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import zio._
import zio.http._
import zio.json._

import com.tjclp.xlcr.server.routes.Routes
import com.tjclp.xlcr.server.json._
import com.tjclp.xlcr.config.LibreOfficeConfig
import com.tjclp.xlcr.types.Mime

/**
 * Fast unit tests for the XLCR HTTP Server.
 *
 * Tests routing logic, validation, and error handling. Actual document processing is tested
 * separately in integration tests.
 */
class ServerSpec extends AnyFlatSpec with Matchers:

  import Codecs.given

  // Helper to run ZIO effects
  private val runtime = Runtime.default

  private def runZIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(effect).getOrThrowFiberFailure()
    }

  // Test app for route testing
  private val app = Routes.all

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
    runZIO(ZIO.scoped(app.runZIO(request)))

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

  it should "not start LibreOffice by default" in {
    if LibreOfficeConfig.isAvailable() then
      LibreOfficeConfig.shutdown()
      val request  = makeRequest(Method.GET, "/health")
      val response = executeRequest(request)
      response.status shouldBe Status.Ok

      val body   = runZIO(response.body.asString)
      val health = body.fromJson[HealthResponse]
      health.isRight shouldBe true
      health.toOption.get.libreoffice.flatMap(_.ready) shouldBe None
      health.toOption.get.libreoffice.get.running shouldBe false
    else
      info("Skipping: LibreOffice unavailable")
  }

  it should "support explicit LibreOffice runtime probes" in {
    LibreOfficeConfig.shutdown()
    val request  = makeRequest(Method.GET, "/health?check=libreoffice")
    val response = executeRequest(request)
    response.status shouldBe Status.Ok

    val body   = runZIO(response.body.asString)
    val health = body.fromJson[HealthResponse]
    health.isRight shouldBe true

    val payload = health.toOption.get
    val lo      = payload.libreoffice.get

    if LibreOfficeConfig.isAvailable() then
      payload.status shouldBe "healthy"
      lo.ready shouldBe Some(true)
      lo.running shouldBe true
      LibreOfficeConfig.shutdown()
    else
      payload.status shouldBe "unhealthy"
      lo.ready shouldBe Some(false)
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
  // Convert Endpoint Validation Tests
  // ============================================================================

  "POST /convert" should "reject requests without 'to' parameter" in {
    val request  = makeRequest(Method.POST, "/convert", Some("test".getBytes), Some(Mime.xlsx.value))
    val response = executeRequest(request)

    response.status shouldBe Status.BadRequest
  }

  it should "reject empty body" in {
    val request  = makeRequest(Method.POST, "/convert?to=pdf")
    val response = executeRequest(request)

    response.status shouldBe Status.BadRequest
  }

  // ============================================================================
  // Split Endpoint Validation Tests
  // ============================================================================

  "POST /split" should "reject empty body" in {
    val request  = makeRequest(Method.POST, "/split")
    val response = executeRequest(request)

    response.status shouldBe Status.BadRequest
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
  // Info Endpoint Validation Tests
  // ============================================================================

  "POST /info" should "reject empty body" in {
    val request  = makeRequest(Method.POST, "/info")
    val response = executeRequest(request)

    response.status shouldBe Status.BadRequest
  }

  it should "prefer Content-Type over content sniffing by default" in {
    val htmlBody = "<html><body>hello</body></html>".getBytes("UTF-8")
    val request  = makeRequest(
      Method.POST,
      "/info",
      Some(htmlBody),
      Some(Mime.csv.value)
    )
    val response = executeRequest(request)

    response.status shouldBe Status.Ok

    val body = runZIO(response.body.asString)
    val info = body.fromJson[InfoResponse]
    info.isRight shouldBe true
    info.toOption.get.mimeType shouldBe Mime.csv.value
  }

  it should "honor detect=tika and override Content-Type for /info" in {
    val htmlBody = "<html><body>hello</body></html>".getBytes("UTF-8")
    val request  = makeRequest(
      Method.POST,
      "/info?detect=tika",
      Some(htmlBody),
      Some(Mime.csv.value)
    )
    val response = executeRequest(request)

    response.status shouldBe Status.Ok

    val body = runZIO(response.body.asString)
    val info = body.fromJson[InfoResponse]
    info.isRight shouldBe true
    info.toOption.get.mimeType shouldBe Mime.html.value
  }

  // ============================================================================
  // Capability/Availability Tests
  // ============================================================================

  "GET /capabilities" should "hide LibreOffice-only conversions when unavailable" in {
    if !LibreOfficeConfig.isAvailable() then
      val request  = makeRequest(Method.GET, "/capabilities")
      val response = executeRequest(request)
      response.status shouldBe Status.Ok

      val body         = runZIO(response.body.asString)
      val capabilities = body.fromJson[CapabilitiesResponse]
      capabilities.isRight shouldBe true

      val hasOdgToSvg = capabilities.toOption.get.conversions.exists(c =>
        c.from == Mime.odg.value && c.to == Mime.svg.value
      )
      hasOdgToSvg shouldBe false
    else
      info("Skipping: LibreOffice available")
  }

  "POST /convert with backend=libreoffice" should "return 503 when LibreOffice is unavailable" in {
    if !LibreOfficeConfig.isAvailable() then
      val request = makeRequest(
        Method.POST,
        "/convert?to=pdf&backend=libreoffice",
        Some("hello".getBytes("UTF-8")),
        Some(Mime.plain.value)
      )
      val response = executeRequest(request)

      response.status shouldBe Status.ServiceUnavailable
    else
      info("Skipping: LibreOffice available")
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
