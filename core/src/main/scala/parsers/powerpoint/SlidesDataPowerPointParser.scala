package com.tjclp.xlcr
package parsers.powerpoint

import java.awt.{ Color, Rectangle }
import java.io.ByteArrayInputStream

import scala.jdk.CollectionConverters._
import scala.util.Try

import org.apache.poi.sl.usermodel.ColorStyle
import org.apache.poi.sl.usermodel.PaintStyle.SolidPaint
import org.apache.poi.xslf.usermodel._

import models.FileContent
import models.powerpoint._
import parsers.SimpleParser
import types.MimeType.ApplicationVndMsPowerpoint
import scala.util.Using

/**
 * SlidesDataPowerPointParser parses PowerPoint presentations (PPTX) into SlidesData, including
 * style (fill color, stroke color/width) and text font details.
 */
object SlidesDataPowerPointParser
    extends SimpleParser[ApplicationVndMsPowerpoint.type, SlidesData] {
  override def parse(
    input: FileContent[ApplicationVndMsPowerpoint.type]
  ): SlidesData =
    Try {
      Using.resource(new ByteArrayInputStream(input.data)) { bais =>
        val ppt = new XMLSlideShow(bais)
        val slides = ppt.getSlides.asScala.zipWithIndex.map {
          case (slide, idx) =>
            val title = Option(slide.getTitle)
            val notes = Option(slide.getNotes).map { notesSlide =>
              notesSlide.getShapes.asScala
                .collect { case ts: XSLFTextShape =>
                  ts.getText
                }
                .mkString(" ")
            }

            // Extract shapes from the slide
            val elements = slide.getShapes.asScala.toList.map { shape =>
              val elementType  = guessElementType(shape)
              val contentOpt   = getTextContent(shape)
              val shapeTypeOpt = guessShapeType(shape)
              val anchorRect   = rectangleFromAnchor(shape.getAnchor)
              val rotationDeg  = getRotation(shape)
              val positionOpt = Some(
                Position(
                  x = anchorRect.x.toDouble,
                  y = anchorRect.y.toDouble,
                  width = anchorRect.width.toDouble,
                  height = anchorRect.height.toDouble,
                  rotation = Some(rotationDeg)
                )
              )

              val styleOpt = buildStyle(shape)

              SlideElement(
                elementType = elementType,
                shapeType = shapeTypeOpt,
                content = contentOpt,
                position = positionOpt,
                style = styleOpt
              )
            }

            // Extract background color if any
            val backgroundColor = Option(slide.getBackground).flatMap { bg =>
              Option(bg.getFillColor).map(c => colorToHex(c))
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
    }.recover { case ex: Exception =>
      throw ParserError(
        s"Failed to parse PowerPoint to SlidesData: ${ex.getMessage}",
        Some(ex)
      )
    }.get

  /**
   * Build a SlideElementStyle from the shape's fill, line, and text run properties.
   */
  private def buildStyle(shape: XSLFShape): Option[SlideElementStyle] = {
    val fillColor   = getFillColor(shape)
    val strokeColor = getLineColor(shape)
    val strokeWidth = getLineWidth(shape)

    // If it's a text shape, try to fetch the first run's font data
    val fontOpt = shape match {
      case textShape: XSLFTextShape =>
        val maybeRun = textShape.getTextParagraphs.asScala
          .flatMap(_.getTextRuns.asScala)
          .headOption
        maybeRun.map(tr => textRunToPptFontData(tr))
      case _ => None
    }

    if (fillColor.isEmpty && strokeColor.isEmpty && strokeWidth.isEmpty && fontOpt.isEmpty) None
    else {
      Some(
        SlideElementStyle(
          fillColor = fillColor,
          strokeColor = strokeColor,
          strokeWidth = strokeWidth,
          font = fontOpt
        )
      )
    }
  }

  /**
   * Extract fill color from a shape, returning a hex string if we find it.
   */
  private def getFillColor(shape: XSLFShape): Option[String] =
    shape match {
      case auto: XSLFAutoShape =>
        Option(auto.getFillColor).map(colorToHex)
      case textShape: XSLFTextShape =>
        Option(textShape.getFillColor).map(colorToHex)
      case _ => None
    }

  /**
   * Extract line color from a shape, returning a hex string if found.
   */
  private def getLineColor(shape: XSLFShape): Option[String] =
    shape match {
      case sh: XSLFSimpleShape =>
        Option(sh.getLineColor).map(colorToHex)
      case _ => None
    }

  /**
   * Extract line width from a shape, if available.
   */
  private def getLineWidth(shape: XSLFShape): Option[Double] =
    shape match {
      case sh: XSLFSimpleShape =>
        val lw = sh.getLineWidth
        if (lw > 0) Some(lw) else None
      case _ => None
    }

  /**
   * Convert a single text run to PptFontData, extracting color, size, bold, italic, etc.
   */
  private def textRunToPptFontData(tr: XSLFTextRun): PptFontData = {
    val color = Option(tr.getFontColor).map {
      case c: Color => colorToHex(c)
      case solid: SolidPaint =>
        val cs: ColorStyle = solid.getSolidColor
        colorToHex(toAwtColor(cs))
      case _ => "#000000"
    }

    PptFontData(
      name = Option(tr.getFontFamily).getOrElse(""),
      size = if (tr.getFontSize > 0) Some(tr.getFontSize.toInt) else None,
      bold = tr.isBold,
      italic = tr.isItalic,
      underline = tr.isUnderlined,
      color = color
    )
  }

  /**
   * Convert an Apache ColorStyle to a java.awt.Color
   */
  private def toAwtColor(cs: ColorStyle): Color =
    cs.getColor

  /**
   * Attempt to extract text content from a shape if it's a text shape.
   */
  private def getTextContent(shape: XSLFShape): Option[String] =
    shape match {
      case ts: XSLFTextShape =>
        val txt = ts.getText
        Option(txt).filterNot(_.trim.isEmpty)
      case _ => None
    }

  /**
   * Provide a top-level guess for shape type: text/image/shape
   */
  private def guessElementType(shape: XSLFShape): String =
    shape match {
      case _: XSLFTextShape    => "text"
      case _: XSLFPictureShape => "image"
      case _                   => "shape"
    }

  /**
   * Provide a more specific shape type, e.g. "XSLFAutoShape"
   */
  private def guessShapeType(shape: XSLFShape): Option[String] =
    shape match {
      case _: XSLFTextShape    => None
      case _: XSLFPictureShape => None
      case other               => Some(other.getClass.getSimpleName)
    }

  /**
   * Convert the anchor (Rectangle2D) to a concrete Rectangle of integer coords.
   */
  private def rectangleFromAnchor(
    rect2D: java.awt.geom.Rectangle2D
  ): Rectangle =
    new Rectangle(
      rect2D.getX.toInt,
      rect2D.getY.toInt,
      rect2D.getWidth.toInt,
      rect2D.getHeight.toInt
    )

  /**
   * Retrieve the shape's rotation, or 0.0 if not available.
   */
  private def getRotation(shape: XSLFShape): Double =
    shape match {
      case s: XSLFSimpleShape => s.getRotation
      case _                  => 0.0
    }

  /**
   * Convert Java Color to #RRGGBB format
   */
  private def colorToHex(color: Color): String =
    f"#${color.getRGB & 0xffffff}%06X"
}
