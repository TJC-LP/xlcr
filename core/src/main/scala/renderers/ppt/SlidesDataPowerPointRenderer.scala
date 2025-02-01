package com.tjclp.xlcr
package renderers.ppt

import models.FileContent
import models.ppt.SlidesData
import renderers.Renderer
import types.MimeType
import types.MimeType.ApplicationVndMsPowerpoint

import org.apache.poi.xslf.usermodel.{XMLSlideShow, XSLFSlide, XSLFTextBox}

import java.awt.Rectangle
import java.io.ByteArrayOutputStream

/**
 * SlidesDataPowerPointRenderer converts a SlidesData model into a PowerPoint PPTX file.
 */
class SlidesDataPowerPointRenderer extends Renderer[SlidesData, ApplicationVndMsPowerpoint.type]:
  override def render(model: SlidesData): FileContent[ApplicationVndMsPowerpoint.type] =
    // Create a new PowerPoint slideshow
    val ppt = new XMLSlideShow()

    // Iterate over each slide in the model
    model.slides.foreach { slideData =>
      val pptSlide: XSLFSlide = ppt.createSlide()

      // Set slide background color if provided
      slideData.backgroundColor.foreach { hexColor =>
        val color = java.awt.Color.decode(hexColor)
        pptSlide.getBackground.setFillColor(color)
      }

      // Process each slide element
      slideData.elements.foreach { element =>
        // For text elements, create a text box
        if element.elementType == "text" then
          val textBox: XSLFTextBox = pptSlide.createTextBox()
          textBox.setText(element.content.getOrElse(""))

          // Set position, size, and rotation if available
          element.position.foreach { pos =>
            val rect = new Rectangle(
              pos.x.toInt,
              pos.y.toInt,
              pos.width.toInt,
              pos.height.toInt
            )
            textBox.setAnchor(rect)
            pos.rotation.foreach { angle =>
              textBox.setRotation(angle)
            }
          }
        // Additional element types can be handled here in the future.
      }
    }

    // Write the slideshow to a byte array
    val outputStream = new ByteArrayOutputStream()
    ppt.write(outputStream)
    ppt.close()

    val pptBytes = outputStream.toByteArray
    outputStream.close()

    FileContent[ApplicationVndMsPowerpoint.type](pptBytes, ApplicationVndMsPowerpoint)