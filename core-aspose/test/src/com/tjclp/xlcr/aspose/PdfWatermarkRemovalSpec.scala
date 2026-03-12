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

  private def run[A](zio: ZIO[Any, TransformError, A]): A =
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(zio).getOrThrowFiberFailure()
    }

  /** Create a PDF with a watermark artifact using the Aspose API. */
  private def createWatermarkedPdf(): Array[Byte] =
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

    val out = new java.io.ByteArrayOutputStream()
    doc.save(out)
    doc.close()
    out.toByteArray

  /** Count watermark artifacts in a PDF byte array. */
  private def countWatermarkArtifacts(pdfBytes: Array[Byte]): Int =
    val doc   = new com.aspose.pdf.Document(new ByteArrayInputStream(pdfBytes))
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
    doc.close()
    count

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
