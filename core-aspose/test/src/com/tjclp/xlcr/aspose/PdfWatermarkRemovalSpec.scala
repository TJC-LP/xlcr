package com.tjclp.xlcr.aspose

import java.io.ByteArrayInputStream

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import zio.*

import com.tjclp.xlcr.transform.TransformError
import com.tjclp.xlcr.types.*

/**
 * Integration tests for PDF watermark removal.
 *
 * Requires a valid Aspose.Pdf license (via env var or resource file).
 */
class PdfWatermarkRemovalSpec extends AnyWordSpec with Matchers:

  private val TrailingVectorCommands =
    Set("m", "l", "c", "v", "y", "h", "re", "S", "s", "f", "F", "f*", "B", "B*", "b", "b*", "n", "W", "W*")

  private def run[A](zio: ZIO[Any, TransformError, A]): A =
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(zio).getOrThrowFiberFailure()
    }

  private def openPdf(
    pdfBytes: Array[Byte],
    password: Option[String] = None
  ): com.aspose.pdf.Document =
    password match
      case Some(pwd) => new com.aspose.pdf.Document(new ByteArrayInputStream(pdfBytes), pwd)
      case None      => new com.aspose.pdf.Document(new ByteArrayInputStream(pdfBytes))

  private def withPdf[A](
    pdfBytes: Array[Byte],
    password: Option[String] = None
  )(f: com.aspose.pdf.Document => A): A =
    val doc = openPdf(pdfBytes, password)
    try f(doc)
    finally doc.close()

  /** Create a PDF with a watermark artifact using the Aspose API. */
  private def createWatermarkedPdf(
    password: Option[String] = None
  ): Array[Byte] =
    AsposeLicenseV2.require[Pdf]
    val doc  = new com.aspose.pdf.Document()
    val page = doc.getPages.add()

    // Add text content so the page isn't empty
    page.getParagraphs.add(new com.aspose.pdf.TextFragment("Hello, world!"))

    // Add a watermark artifact
    val watermark = new com.aspose.pdf.WatermarkArtifact()
    watermark.setText(new com.aspose.pdf.facades.FormattedText("CONFIDENTIAL"))
    watermark.setArtifactHorizontalAlignment(com.aspose.pdf.HorizontalAlignment.Center)
    watermark.setArtifactVerticalAlignment(com.aspose.pdf.VerticalAlignment.Center)
    watermark.setRotation(45)
    watermark.setOpacity(0.5)
    watermark.setBackground(true)
    page.getArtifacts.add(watermark)

    password.foreach { pwd =>
      doc.encrypt(
        pwd,
        pwd,
        com.aspose.pdf.facades.DocumentPrivilege.getAllowAll(),
        com.aspose.pdf.CryptoAlgorithm.AESx256,
        false
      )
    }

    val out = new java.io.ByteArrayOutputStream()
    doc.save(out)
    doc.close()
    out.toByteArray

  /** Create a PDF whose content stream ends with legitimate vector artwork. */
  private def createPdfWithTrailingVectorLine(): Array[Byte] =
    AsposeLicenseV2.require[Pdf]
    val doc  = new com.aspose.pdf.Document()
    val page = doc.getPages.add()

    page.getParagraphs.add(new com.aspose.pdf.TextFragment("Hello, world!"))

    val graph = new com.aspose.pdf.drawing.Graph()
    graph.setWidth(240)
    graph.setHeight(20)
    val line  = new com.aspose.pdf.drawing.Line(Array[Float](0f, 10f, 240f, 10f))
    line.getGraphInfo.setLineWidth(2)
    graph.getShapes.addItem(line)
    page.getParagraphs.add(graph)

    val out = new java.io.ByteArrayOutputStream()
    doc.save(out)
    doc.close()
    out.toByteArray

  /** Count watermark artifacts in a PDF byte array. */
  private def countWatermarkArtifacts(
    pdfBytes: Array[Byte],
    password: Option[String] = None
  ): Int =
    withPdf(pdfBytes, password) { doc =>
      val pages = doc.getPages
      var count = 0
      var i     = 1
      while i <= pages.size() do
        val artifacts = pages.get_Item(i).getArtifacts
        var j         = 1
        while j <= artifacts.size() do
          if artifacts.get_Item(j).getSubtype ==
              com.aspose.pdf.Artifact.ArtifactSubtype.Watermark
          then count += 1
          j += 1
        i += 1
      count
    }

  private def isEncrypted(
    pdfBytes: Array[Byte],
    password: Option[String] = None
  ): Boolean =
    withPdf(pdfBytes, password)(_.isEncrypted)

  private def countGraphicElements(pdfBytes: Array[Byte]): Int =
    withPdf(pdfBytes) { doc =>
      val absorber = new com.aspose.pdf.vector.GraphicsAbsorber()
      try
        absorber.visit(doc.getPages.get_Item(1))
        absorber.getElements.size()
      finally absorber.dispose()
    }

  private def hasTrailingVectorCommandsAfterLastText(pdfBytes: Array[Byte]): Boolean =
    withPdf(pdfBytes) { doc =>
      val ops   = doc.getPages.get_Item(1).getContents
      val total = ops.size()

      var lastET = -1
      var idx    = total
      while idx >= 1 && lastET < 0 do
        val cmd = Option(ops.get_Item(idx).getCommandName).map(_.trim).getOrElse("")
        if cmd == "ET" then lastET = idx
        idx -= 1

      if lastET < 0 || lastET >= total then false
      else
        var hasTrailingVector = false
        idx = lastET + 1
        while idx <= total && !hasTrailingVector do
          val cmd = Option(ops.get_Item(idx).getCommandName).map(_.trim).getOrElse("")
          if TrailingVectorCommands.contains(cmd) then hasTrailingVector = true
          idx += 1
        hasTrailingVector
    }

  "processPdf with removeWatermarks=true" should {
    "remove watermark artifacts from all pages" in {
      val watermarked = createWatermarkedPdf()
      countWatermarkArtifacts(watermarked) should be > 0

      val input = Content.fromChunk[Mime.Pdf](
        Chunk.fromArray(watermarked),
        Mime.pdf
      )
      val options = ConvertOptions(removeWatermarks = true)
      val result  = run(processPdf(input, options))

      countWatermarkArtifacts(result.data.toArray) shouldBe 0
      result.mime shouldBe Mime.pdf
    }

    "preserve encryption for password-protected PDFs" in {
      val password    = "secret-password"
      val watermarked = createWatermarkedPdf(password = Some(password))
      isEncrypted(watermarked, Some(password)) shouldBe true
      countWatermarkArtifacts(watermarked, Some(password)) should be > 0

      val input = Content.fromChunk[Mime.Pdf](
        Chunk.fromArray(watermarked),
        Mime.pdf
      )
      val options = ConvertOptions(
        password = Some(password),
        removeWatermarks = true
      )
      val result = run(processPdf(input, options))

      isEncrypted(result.data.toArray, Some(password)) shouldBe true
      countWatermarkArtifacts(result.data.toArray, Some(password)) shouldBe 0
    }

    "preserve legitimate trailing vector artwork" in {
      val pdfWithTrailingVector = createPdfWithTrailingVectorLine()
      hasTrailingVectorCommandsAfterLastText(pdfWithTrailingVector) shouldBe true
      val originalGraphicCount = countGraphicElements(pdfWithTrailingVector)
      originalGraphicCount should be > 0

      val input = Content.fromChunk[Mime.Pdf](
        Chunk.fromArray(pdfWithTrailingVector),
        Mime.pdf
      )
      val options = ConvertOptions(removeWatermarks = true)
      val result  = run(processPdf(input, options))

      countGraphicElements(result.data.toArray) shouldBe originalGraphicCount
    }
  }

  "processPdf with removeWatermarks=false" should {
    "pass through PDF unchanged (watermarks preserved)" in {
      val watermarked   = createWatermarkedPdf()
      val originalCount = countWatermarkArtifacts(watermarked)
      originalCount should be > 0

      val input = Content.fromChunk[Mime.Pdf](
        Chunk.fromArray(watermarked),
        Mime.pdf
      )
      val options = ConvertOptions(removeWatermarks = false)
      val result  = run(processPdf(input, options))

      countWatermarkArtifacts(result.data.toArray) shouldBe originalCount
    }
  }

  "AsposeTransforms.convert PDF->PDF" should {
    "reject bare PDF->PDF with no processing flags" in {
      val watermarked = createWatermarkedPdf()
      val input = Content[Mime](
        Chunk.fromArray(watermarked),
        Mime.pdf
      )

      val exit = Unsafe.unsafe { implicit u =>
        Runtime.default.unsafe.run(
          AsposeTransforms.convert(input, Mime.pdf)
        )
      }
      exit.isFailure shouldBe true
    }

    "succeed with removeWatermarks=true" in {
      val watermarked = createWatermarkedPdf()
      val input = Content[Mime](
        Chunk.fromArray(watermarked),
        Mime.pdf
      )
      val options = ConvertOptions(removeWatermarks = true)

      val result = run(AsposeTransforms.convert(input, Mime.pdf, options))
      result.mime shouldBe Mime.pdf
      countWatermarkArtifacts(result.data.toArray) shouldBe 0
    }
  }
