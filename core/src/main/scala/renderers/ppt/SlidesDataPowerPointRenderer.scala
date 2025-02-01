package com.tjclp.xlcr
package renderers.ppt

import models.FileContent
import models.ppt.*
import renderers.Renderer
import types.MimeType
import types.MimeType.ApplicationVndMsPowerpoint

import org.apache.poi.common.usermodel.fonts.FontGroup
import org.apache.poi.sl.usermodel.PictureData.PictureType
import org.apache.poi.xslf.usermodel.*

import java.awt.{Color, Rectangle}
import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Paths}
import java.util.Base64

/**
 * SlidesDataPowerPointRenderer converts a SlidesData model into a PowerPoint PPTX file,
 * now handling images, background colors, fonts, line/stroke styling, etc.
 */
class SlidesDataPowerPointRenderer extends Renderer[SlidesData, ApplicationVndMsPowerpoint.type]:

  override def render(model: SlidesData): FileContent[ApplicationVndMsPowerpoint.type] =
    val ppt = new XMLSlideShow()

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
        val anchorRect = element.position.map { pos =>
          new Rectangle(pos.x.toInt, pos.y.toInt, pos.width.toInt, pos.height.toInt)
        }.getOrElse(new Rectangle(50, 50, 300, 50))

        element.elementType.toLowerCase match
          case "text" =>
            val textBox: XSLFTextBox = pptSlide.createTextBox()
            textBox.setAnchor(anchorRect)

            // Insert text into a single paragraph
            val paragraph = textBox.addNewTextParagraph()
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
                // Use two-arg setFontFamily with FontGroup.LATIN
                run.setFontFamily(f.name, FontGroup.LATIN)
                f.size.foreach(sz => run.setFontSize(sz.toDouble))
                if f.bold then run.setBold(true)
                if f.italic then run.setItalic(true)
                if f.underline then run.setUnderlined(true)

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

          case "image" =>
            // We'll handle content as either a file path or base64
            element.content.foreach { contentStr =>
              val imageBytes = loadImageBytes(contentStr)
              if imageBytes.nonEmpty then
                val pictureType = guessPictureType(imageBytes)
                val pictureData = ppt.addPicture(imageBytes, pictureType)
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

          case "shape" =>
            val shapeBox = pptSlide.createTextBox()
            shapeBox.setAnchor(anchorRect)

            // shape type can be used for something special if needed
            element.shapeType.foreach { stype => /* no-op for now */}

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

          case _ =>
            // Unknown element type, ignore or log
            ()
      }
    }

    // Write the slideshow to bytes
    val outputStream = new ByteArrayOutputStream()
    ppt.write(outputStream)
    ppt.close()

    val pptBytes = outputStream.toByteArray
    outputStream.close()
    FileContent[ApplicationVndMsPowerpoint.type](pptBytes, ApplicationVndMsPowerpoint)

  /**
   * Attempt to load image bytes from either a file path or a base64-encoded string.
   */
  private def loadImageBytes(contentStr: String): Array[Byte] =
    if contentStr.trim.startsWith("data:") || isLikelyBase64(contentStr) then
      Base64.getDecoder.decode(contentStr.replaceAll("^data:.*;base64,", ""))
    else
      val path = Paths.get(contentStr)
      if Files.exists(path) then
        Files.readAllBytes(path)
      else
        Array.emptyByteArray

  private def isLikelyBase64(s: String): Boolean =
    s.contains("=") && s.length > 50

  /**
   * Determine the PictureType from raw bytes
   */
  private def guessPictureType(bytes: Array[Byte]): PictureType =
    if isJpeg(bytes) then PictureType.JPEG
    else if isPng(bytes) then PictureType.PNG
    else if isGif(bytes) then PictureType.GIF
    else PictureType.PNG

  private def isPng(bytes: Array[Byte]): Boolean =
    bytes.length > 8 &&
      bytes(0) == 0x89.toByte && bytes(1) == 0x50.toByte &&
      bytes(2) == 0x4E.toByte && bytes(3) == 0x47.toByte

  private def isJpeg(bytes: Array[Byte]): Boolean =
    bytes.length > 2 &&
      bytes(0) == 0xFF.toByte && bytes(1) == 0xD8.toByte

  private def isGif(bytes: Array[Byte]): Boolean =
    bytes.length > 3 &&
      bytes(0) == 'G'.toByte && bytes(1) == 'I'.toByte && bytes(2) == 'F'.toByte

  private def parseColor(hex: String): Color =
    val cleanHex = if hex.startsWith("#") then hex.substring(1) else hex
    val rgb = Integer.parseInt(cleanHex, 16)
    new Color(rgb)