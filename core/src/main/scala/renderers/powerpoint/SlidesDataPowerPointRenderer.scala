package com.tjclp.xlcr
package renderers.powerpoint

import java.awt.{ Color, Rectangle }
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Paths }
import java.util.Base64

import scala.util.{ Try, Using }

import org.apache.poi.common.usermodel.fonts.FontGroup
import org.apache.poi.sl.usermodel.PictureData.PictureType
import org.apache.poi.xslf.usermodel._

import bridges.image.SvgToPngBridge
import models.FileContent
import models.powerpoint._
import renderers.SimpleRenderer
import types.MimeType.{ ApplicationVndMsPowerpoint, ImageSvgXml }
import utils.resource.ResourceWrappers._

/**
 * SlidesDataPowerPointRenderer converts a SlidesData model into a PowerPoint PPTX file, now
 * handling images (including optional SVG), background colors, fonts, line/stroke styling, etc.
 *
 * If the image content is inline SVG, we use SvgToPngBridge to convert it to PNG before embedding.
 */
class SlidesDataPowerPointRenderer
    extends SimpleRenderer[SlidesData, ApplicationVndMsPowerpoint.type] {

  override def render(
    model: SlidesData
  ): FileContent[ApplicationVndMsPowerpoint.type] =
    Using.Manager { use =>
      val ppt = new XMLSlideShow()
      use(new CloseableWrapper(ppt))

      // For each slide in SlidesData:
      model.slides.foreach { slideData =>
        val pptSlide: XSLFSlide = ppt.createSlide()

        // Slide background color
        slideData.backgroundColor.foreach { hexColor =>
          val color = parseColor(hexColor)
          pptSlide.getBackground.setFillColor(color)
        }

        // For each element, create shapes
        slideData.elements.foreach { element =>
          val anchorRect = element.position
            .map { pos =>
              new Rectangle(
                pos.x.toInt,
                pos.y.toInt,
                pos.width.toInt,
                pos.height.toInt
              )
            }
            .getOrElse(new Rectangle(50, 50, 300, 50))

          element.elementType.toLowerCase match {
            case "text" =>
              handleTextElement(pptSlide, element, anchorRect)

            case "image" =>
              handleImageElement(ppt, pptSlide, element, anchorRect)

            case "shape" =>
              handleShapeElement(pptSlide, element, anchorRect)

            case _ =>
              // Unknown element type, ignore or log
              ()
          }
        }
      }

      // Write the slideshow to bytes
      val outputStream = use(new ByteArrayOutputStream())
      ppt.write(outputStream)

      FileContent[ApplicationVndMsPowerpoint.type](
        outputStream.toByteArray,
        ApplicationVndMsPowerpoint
      )
    }.get

  private def handleTextElement(
    pptSlide: XSLFSlide,
    element: SlideElement,
    anchorRect: Rectangle
  ): Unit = {
    val textBox: XSLFTextBox = pptSlide.createTextBox()
    textBox.setAnchor(anchorRect)

    // Insert text into a single paragraph
    val paragraph        = textBox.addNewTextParagraph()
    val run: XSLFTextRun = paragraph.addNewTextRun()
    run.setText(element.content.getOrElse(""))

    // Apply style (background fill color, stroke color, stroke width, font)
    element.style.foreach { style =>
      // Fill color for text box
      style.fillColor.foreach { fill =>
        textBox.setFillColor(parseColor(fill))
      }

      // Stroke color, stroke width
      style.strokeColor.foreach { stroke =>
        textBox.setLineColor(parseColor(stroke))
      }
      style.strokeWidth.foreach { width =>
        textBox.setLineWidth(width)
      }

      // Font
      style.font.foreach { f =>
        run.setFontFamily(f.name, FontGroup.LATIN)
        f.size.foreach(sz => run.setFontSize(sz.toDouble))
        if (f.bold) run.setBold(true)
        if (f.italic) run.setItalic(true)
        if (f.underline) run.setUnderlined(true)

        // Font color
        f.color.foreach { c =>
          run.setFontColor(parseColor(c))
        }
      }
    }

    // If there's a rotation in position, rotate the text box
    element.position.flatMap(_.rotation).foreach { deg =>
      textBox.setRotation(deg)
    }
  }

  private def handleImageElement(
    ppt: XMLSlideShow,
    pptSlide: XSLFSlide,
    element: SlideElement,
    anchorRect: Rectangle
  ): Unit =
    element.content.foreach { contentStr =>
      // Attempt to load or decode the image bytes
      val rawBytes = loadImageBytes(contentStr)
      if (rawBytes.nonEmpty) {
        // If we detect inline SVG, convert to PNG via SvgToPngBridge
        val isSvg = isSvgBytes(rawBytes)
        val imageBytes = if (isSvg) {
          convertSvgToPng(rawBytes).getOrElse(
            rawBytes
          ) // fallback if conversion fails
        } else rawBytes

        val pictureType                    = guessPictureType(imageBytes)
        val pictureData                    = ppt.addPicture(imageBytes, pictureType)
        val pictureShape: XSLFPictureShape = pptSlide.createPicture(pictureData)
        pictureShape.setAnchor(anchorRect)

        // style
        element.style.foreach { style =>
          style.strokeColor.foreach { stroke =>
            pictureShape.setLineColor(parseColor(stroke))
          }
          style.strokeWidth.foreach { width =>
            pictureShape.setLineWidth(width)
          }
        }

        // Rotation
        element.position.flatMap(_.rotation).foreach { deg =>
          pictureShape.setRotation(deg)
        }
      }
    }

  /**
   * Convert inline SVG bytes into PNG bytes using the SvgToPngBridge.
   */
  private def convertSvgToPng(svgBytes: Array[Byte]): Option[Array[Byte]] =
    Try {
      val svgContent = FileContent[ImageSvgXml.type](svgBytes, ImageSvgXml)
      val pngContent = SvgToPngBridge.convert(svgContent)
      pngContent.data
    }.toOption

  /**
   * Attempt to load image bytes from either a file path or a base64-encoded string (including data
   * URIs). Also handles raw inline SVG if the content starts with "<svg".
   */
  private def loadImageBytes(contentStr: String): Array[Byte] = {
    val trimmed = contentStr.trim
    // If data uri or base64
    if (trimmed.startsWith("data:") || isLikelyBase64(trimmed)) {
      Base64.getDecoder.decode(trimmed.replaceAll("^data:.*;base64,", ""))
    } else {
      val path = Paths.get(trimmed)
      if (Files.exists(path)) {
        Files.readAllBytes(path)
      } else if (trimmed.contains("<svg")) {
        // Inline raw SVG content
        trimmed.getBytes(StandardCharsets.UTF_8)
      } else {
        Array.emptyByteArray
      }
    }
  }

  /**
   * Determine if the raw bytes are likely base64
   */
  private def isLikelyBase64(s: String): Boolean =
    // extremely naive check: at least 40 chars, only base64
    s.length > 40 && s.matches("^[A-Za-z0-9+/=]+$")

  /**
   * Determine the PictureType from raw bytes. If we detect raw SVG, use PictureType.SVG (though
   * we'll convert them to PNG anyway).
   */
  private def guessPictureType(bytes: Array[Byte]): PictureType =
    if (isSvgBytes(bytes)) PictureType.SVG
    else if (isJpeg(bytes)) PictureType.JPEG
    else if (isPng(bytes)) PictureType.PNG
    else if (isGif(bytes)) PictureType.GIF
    else PictureType.PNG

  private def isPng(bytes: Array[Byte]): Boolean =
    bytes.length > 8 &&
      bytes(0) == 0x89.toByte && bytes(1) == 0x50.toByte &&
      bytes(2) == 0x4e.toByte && bytes(3) == 0x47.toByte

  private def isJpeg(bytes: Array[Byte]): Boolean =
    bytes.length > 2 &&
      bytes(0) == 0xff.toByte && bytes(1) == 0xd8.toByte

  private def isGif(bytes: Array[Byte]): Boolean =
    bytes.length > 3 &&
      bytes(0) == 'G'.toByte && bytes(1) == 'I'.toByte && bytes(2) == 'F'.toByte

  private def isSvgBytes(data: Array[Byte]): Boolean = {
    val str = new String(data, StandardCharsets.UTF_8).trim.toLowerCase
    str.contains("<svg")
  }

  private def handleShapeElement(
    pptSlide: XSLFSlide,
    element: SlideElement,
    anchorRect: Rectangle
  ): Unit = {
    val shapeBox = pptSlide.createTextBox()
    shapeBox.setAnchor(anchorRect)

    // shape type can be used for something special if needed
    element.shapeType.foreach { stype => /* no-op for now */ }

    // Apply style
    element.style.foreach { style =>
      style.fillColor.foreach { fill =>
        shapeBox.setFillColor(parseColor(fill))
      }
      style.strokeColor.foreach { stroke =>
        shapeBox.setLineColor(parseColor(stroke))
      }
      style.strokeWidth.foreach { width =>
        shapeBox.setLineWidth(width)
      }
    }

    // Rotation
    element.position.flatMap(_.rotation).foreach { deg =>
      shapeBox.setRotation(deg)
    }
  }

  private def parseColor(hex: String): Color = {
    val cleanHex = if (hex.startsWith("#")) hex.substring(1) else hex
    val rgb      = Integer.parseInt(cleanHex, 16)
    new Color(rgb)
  }
}
