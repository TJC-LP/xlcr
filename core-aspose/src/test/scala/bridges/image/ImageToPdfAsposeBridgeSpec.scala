package com.tjclp.xlcr
package bridges.image

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

  "JpegToPdfAsposeBridge" should "convert JPEG to PDF" in {
    // Create a simple test JPEG
    // This is a minimal valid JPEG header for testing
    val jpegHeader = Array[Byte](
      0xff.toByte,
      0xd8.toByte, // SOI marker
      0xff.toByte,
      0xe0.toByte, // APP0 marker
      0x00.toByte,
      0x10.toByte, // APP0 length (16 bytes)
      0x4a.toByte,
      0x46.toByte,
      0x49.toByte,
      0x46.toByte,
      0x00.toByte, // JFIF\0
      0x01.toByte,
      0x01.toByte, // Version 1.1
      0x00.toByte, // Density units (0 = no units)
      0x00.toByte,
      0x01.toByte, // X density (1)
      0x00.toByte,
      0x01.toByte, // Y density (1)
      0x00.toByte,
      0x00.toByte // Thumbnail size (0x0)
    )

    val input = FileContent[ImageJpeg.type](jpegHeader, ImageJpeg)

    // Convert JPEG to PDF - will throw if there's an issue
    val result = JpegToPdfAsposeBridge.convert(input)

    // Verify the result
    result.mimeType shouldBe ApplicationPdf

    // PDF files should start with %PDF
    new String(result.data.take(4)) should be("%PDF")

    // Ensure we got a non-empty PDF
    result.data.length should be > 100
  }

  "PngToPdfAsposeBridge" should "convert PNG to PDF" in {
    // Create a simple test PNG
    // This is a minimal valid PNG header for testing
    val pngHeader = Array[Byte](
      0x89.toByte,
      0x50.toByte,
      0x4e.toByte,
      0x47.toByte, // PNG signature
      0x0d.toByte,
      0x0a.toByte,
      0x1a.toByte,
      0x0a.toByte, // PNG signature continued
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0x0d.toByte, // IHDR chunk length (13 bytes)
      0x49.toByte,
      0x48.toByte,
      0x44.toByte,
      0x52.toByte, // "IHDR"
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0x01.toByte, // width (1 pixel)
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0x01.toByte, // height (1 pixel)
      0x08.toByte, // bit depth (8 bits per channel)
      0x06.toByte, // color type (6 = RGBA)
      0x00.toByte, // compression method (0 = deflate)
      0x00.toByte, // filter method (0 = adaptive)
      0x00.toByte  // interlace method (0 = no interlace)
    )

    val input = FileContent[ImagePng.type](pngHeader, ImagePng)

    // Convert PNG to PDF - will throw if there's an issue
    val result = PngToPdfAsposeBridge.convert(input)

    // Verify the result
    result.mimeType shouldBe ApplicationPdf

    // PDF files should start with %PDF
    new String(result.data.take(4)) should be("%PDF")

    // Ensure we got a non-empty PDF
    result.data.length should be > 100
  }
}
