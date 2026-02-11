package com.tjclp.xlcr
package bridges.image

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

import org.scalatest.BeforeAndAfterAll

import base.BridgeSpec
import models.FileContent
import types.MimeType.{ ApplicationPdf, ImageJpeg, ImagePng }
import utils.aspose.AsposeLicense

class ImageToPdfAsposeBridgeSpec extends BridgeSpec with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    super.beforeAll()
    AsposeLicense.initializeIfNeeded()
  }

  /** Create a valid 1x1 pixel image in the given format using ImageIO. */
  private def createMinimalImage(format: String): Array[Byte] = {
    val imageType =
      if (format == "jpeg") BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB
    val img = new BufferedImage(1, 1, imageType)
    img.setRGB(0, 0, 0xff0000ff) // blue pixel
    val baos = new ByteArrayOutputStream()
    ImageIO.write(img, format, baos)
    baos.toByteArray
  }

  "JpegToPdfAsposeBridge" should "convert JPEG to PDF" in {
    val jpegBytes = createMinimalImage("jpeg")
    val input     = FileContent[ImageJpeg.type](jpegBytes, ImageJpeg)

    val result = JpegToPdfAsposeBridge.convert(input)

    result.mimeType shouldBe ApplicationPdf
    new String(result.data.take(4)) should be("%PDF")
    result.data.length should be > 100
  }

  "PngToPdfAsposeBridge" should "convert PNG to PDF" in {
    val pngBytes = createMinimalImage("png")
    val input    = FileContent[ImagePng.type](pngBytes, ImagePng)

    val result = PngToPdfAsposeBridge.convert(input)

    result.mimeType shouldBe ApplicationPdf
    new String(result.data.take(4)) should be("%PDF")
    result.data.length should be > 100
  }
}
