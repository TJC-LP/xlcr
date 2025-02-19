package com.tjclp.xlcr
package parsers.powerpoint

import models.FileContent
import models.powerpoint.{Position, SlideData, SlideElement, SlidesData}
import parsers.Parser
import types.MimeType
import types.MimeType.ApplicationVndMsPowerpoint

import org.apache.poi.xslf.usermodel.{XMLSlideShow, XSLFPictureShape, XSLFShape, XSLFTextShape}

import java.io.ByteArrayInputStream
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

/**
 * SlidesDataPowerPointParser parses PowerPoint presentations (PPTX) into SlidesData.
 */
object SlidesDataPowerPointParser extends Parser[ApplicationVndMsPowerpoint.type, SlidesData]:
  override def parse(input: FileContent[ApplicationVndMsPowerpoint.type]): SlidesData =
    Try {
      Using.resource(new ByteArrayInputStream(input.data)) { bais =>
        val ppt = new XMLSlideShow(bais)
        val slides = ppt.getSlides.asScala.zipWithIndex.map { case (slide, idx) =>
          // Extract slide title if available.
          val title = Option(slide.getTitle)
          // Extract notes: iterate over shapes in the notes slide and collect text from XSLFTextShape instances.
          val notes = Option(slide.getNotes).map { notesSlide =>
            notesSlide.getShapes.asScala.collect {
              case ts: XSLFTextShape => ts.getText
            }.mkString(" ")
          }
          // Extract shapes from the slide and convert each to a SlideElement.
          val elements = slide.getShapes.asScala.toList.map { shape =>
            val elementType = shape match {
              case _: XSLFTextShape => "text"
              case _: XSLFPictureShape => "image"
              case _ => "shape"
            }
            val content = shape match {
              case ts: XSLFTextShape => Option(ts.getText)
              case _ => None
            }
            val anchor = shape.getAnchor
            // Since XSLFShape does not provide getRotation, we default to 0.0.
            val position = Position(
              x = anchor.getX,
              y = anchor.getY,
              width = anchor.getWidth,
              height = anchor.getHeight,
              rotation = Some(0.0)
            )
            val shapeType = shape match {
              case _: XSLFTextShape => None
              case _: XSLFPictureShape => None
              case _ => Some(shape.getClass.getSimpleName)
            }
            SlideElement(
              elementType = elementType,
              shapeType = shapeType,
              content = content,
              position = Some(position),
              style = None
            )
          }
          // Extract background color from slide background
          val backgroundColor = Option(slide.getBackground).flatMap { bg =>
            Option(bg.getFillColor).map { color =>
              f"#${color.getRGB & 0xFFFFFF}%06x" // Convert Color to hex string
            }
          }
          SlideData(
            title = title,
            index = idx,
            elements = elements,
            notes = notes,
            backgroundColor = backgroundColor
          )
        }.toList
        ppt.close()
        SlidesData(slides)
      }
    }.recover {
      case ex: Exception =>
        throw ParserError(s"Failed to parse PowerPoint to SlidesData: ${ex.getMessage}", Some(ex))
    }.get