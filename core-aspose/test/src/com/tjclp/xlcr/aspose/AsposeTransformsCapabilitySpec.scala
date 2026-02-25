package com.tjclp.xlcr.aspose

import java.util.concurrent.atomic.AtomicInteger

import scala.collection.mutable

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.tjclp.xlcr.types.Mime

class AsposeTransformsCapabilitySpec extends AnyWordSpec with Matchers:

  "AsposeTransforms.canConvertLicensed" should {
    "reject supported conversions when required product is unlicensed" in {
      AsposeTransforms.canConvert(Mime.docx, Mime.pdf) shouldBe true

      val result = AsposeTransforms.canConvertLicensed(
        Mime.docx,
        Mime.pdf,
        _ => false
      )
      result shouldBe false
    }

    "require both Email and Words licenses for email-to-pdf conversions" in {
      val seenProducts = mutable.Set.empty[AsposeProduct]

      val fullyLicensed = AsposeTransforms.canConvertLicensed(
        Mime.eml,
        Mime.pdf,
        product =>
          seenProducts += product
          true
      )
      fullyLicensed shouldBe true
      seenProducts shouldBe Set(AsposeProduct.Email, AsposeProduct.Words)

      val missingWords = AsposeTransforms.canConvertLicensed(
        Mime.eml,
        Mime.pdf,
        {
          case AsposeProduct.Email => true
          case AsposeProduct.Words => false
          case _                   => true
        }
      )
      missingWords shouldBe false
    }

    "not evaluate license predicates for unsupported conversions" in {
      val calls = new AtomicInteger(0)
      val result = AsposeTransforms.canConvertLicensed(
        Mime.mp3,
        Mime.pdf,
        _ =>
          calls.incrementAndGet()
          true
      )

      result shouldBe false
      calls.get() shouldBe 0
    }
  }

  "AsposeTransforms.canConvert (Tier 1 additions)" should {
    "support PDF -> DOCX/DOC/XLSX" in {
      AsposeTransforms.canConvert(Mime.pdf, Mime.docx) shouldBe true
      AsposeTransforms.canConvert(Mime.pdf, Mime.doc) shouldBe true
      AsposeTransforms.canConvert(Mime.pdf, Mime.xlsx) shouldBe true
    }

    "support Word -> HTML/Plain/Markdown" in {
      AsposeTransforms.canConvert(Mime.docx, Mime.html) shouldBe true
      AsposeTransforms.canConvert(Mime.doc, Mime.html) shouldBe true
      AsposeTransforms.canConvert(Mime.docm, Mime.html) shouldBe true
      AsposeTransforms.canConvert(Mime.docx, Mime.plain) shouldBe true
      AsposeTransforms.canConvert(Mime.doc, Mime.plain) shouldBe true
      AsposeTransforms.canConvert(Mime.docx, Mime.markdown) shouldBe true
      AsposeTransforms.canConvert(Mime.doc, Mime.markdown) shouldBe true
    }

    "support Excel -> CSV/JSON/Markdown" in {
      AsposeTransforms.canConvert(Mime.xlsx, Mime.csv) shouldBe true
      AsposeTransforms.canConvert(Mime.xls, Mime.csv) shouldBe true
      AsposeTransforms.canConvert(Mime.xlsm, Mime.csv) shouldBe true
      AsposeTransforms.canConvert(Mime.xlsb, Mime.csv) shouldBe true
      AsposeTransforms.canConvert(Mime.ods, Mime.csv) shouldBe true
      AsposeTransforms.canConvert(Mime.xlsx, Mime.json) shouldBe true
      AsposeTransforms.canConvert(Mime.xlsx, Mime.markdown) shouldBe true
    }

    "support RTF conversions" in {
      AsposeTransforms.canConvert(Mime.rtf, Mime.pdf) shouldBe true
      AsposeTransforms.canConvert(Mime.rtf, Mime.docx) shouldBe true
      AsposeTransforms.canConvert(Mime.rtf, Mime.html) shouldBe true
      AsposeTransforms.canConvert(Mime.docx, Mime.rtf) shouldBe true
    }

    "support Email -> HTML and EML <-> MSG" in {
      AsposeTransforms.canConvert(Mime.eml, Mime.html) shouldBe true
      AsposeTransforms.canConvert(Mime.msg, Mime.html) shouldBe true
      AsposeTransforms.canConvert(Mime.eml, Mime.msg) shouldBe true
      AsposeTransforms.canConvert(Mime.msg, Mime.eml) shouldBe true
    }

    "support Word/Slides -> Images" in {
      AsposeTransforms.canConvert(Mime.docx, Mime.png) shouldBe true
      AsposeTransforms.canConvert(Mime.docx, Mime.jpeg) shouldBe true
      AsposeTransforms.canConvert(Mime.pptx, Mime.png) shouldBe true
      AsposeTransforms.canConvert(Mime.pptx, Mime.jpeg) shouldBe true
      AsposeTransforms.canConvert(Mime.ppt, Mime.png) shouldBe true
      AsposeTransforms.canConvert(Mime.ppt, Mime.jpeg) shouldBe true
    }

    "require correct products for new conversions" in {
      // PDF -> DOCX needs Pdf product
      val pdfToDocx = AsposeTransforms.canConvertLicensed(
        Mime.pdf,
        Mime.docx,
        {
          case AsposeProduct.Pdf => true
          case _                 => false
        }
      )
      pdfToDocx shouldBe true

      // DOCX -> HTML needs Words product
      val docxToHtml = AsposeTransforms.canConvertLicensed(
        Mime.docx,
        Mime.html,
        {
          case AsposeProduct.Words => true
          case _                   => false
        }
      )
      docxToHtml shouldBe true

      // XLSX -> CSV needs Cells product
      val xlsxToCsv = AsposeTransforms.canConvertLicensed(
        Mime.xlsx,
        Mime.csv,
        {
          case AsposeProduct.Cells => true
          case _                   => false
        }
      )
      xlsxToCsv shouldBe true

      // EML -> HTML needs Email + Words
      val emlToHtml = AsposeTransforms.canConvertLicensed(
        Mime.eml,
        Mime.html,
        _ => true
      )
      emlToHtml shouldBe true
      val emlToHtmlMissingWords = AsposeTransforms.canConvertLicensed(
        Mime.eml,
        Mime.html,
        {
          case AsposeProduct.Email => true
          case _                   => false
        }
      )
      emlToHtmlMissingWords shouldBe false

      // EML -> MSG only needs Email
      val emlToMsg = AsposeTransforms.canConvertLicensed(
        Mime.eml,
        Mime.msg,
        {
          case AsposeProduct.Email => true
          case _                   => false
        }
      )
      emlToMsg shouldBe true
    }
  }

  "AsposeTransforms.canSplitLicensed" should {
    "gate split capability on the required product license" in {
      AsposeTransforms.canSplit(Mime.zip) shouldBe true

      val licensed = AsposeTransforms.canSplitLicensed(
        Mime.zip,
        {
          case AsposeProduct.Zip => true
          case _                 => false
        }
      )
      licensed shouldBe true

      val unlicensed = AsposeTransforms.canSplitLicensed(Mime.zip, _ => false)
      unlicensed shouldBe false
    }

    "not evaluate license predicates for unsupported split types" in {
      val calls = new AtomicInteger(0)
      val result = AsposeTransforms.canSplitLicensed(
        Mime.mp3,
        _ =>
          calls.incrementAndGet()
          true
      )

      result shouldBe false
      calls.get() shouldBe 0
    }
  }
