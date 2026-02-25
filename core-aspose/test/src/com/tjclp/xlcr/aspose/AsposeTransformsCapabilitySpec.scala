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
