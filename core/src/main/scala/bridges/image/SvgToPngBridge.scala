package com.tjclp.xlcr
package bridges.image

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.util.{ Failure, Success, Try, Using }

import org.apache.batik.transcoder.image.PNGTranscoder
import org.apache.batik.transcoder.{ TranscoderException, TranscoderInput, TranscoderOutput }

import bridges.SimpleBridge
import models.FileContent
import parsers.{ Parser, SimpleParser }
import renderers.{ Renderer, SimpleRenderer }
import types.MimeType.{ ImagePng, ImageSvgXml }

/**
 * SvgToPngBridge parses an SVG (image/svg+xml) into SvgModel, then renders that model to PNG
 * (image/png) using Apache Batik.
 */
object SvgToPngBridge extends SimpleBridge[ImageSvgXml.type, ImagePng.type] {
  override type M = FileContent[ImageSvgXml.type]

  override def inputParser: Parser[ImageSvgXml.type, M] =
    SvgParser

  override private[bridges] def outputRenderer: Renderer[M, ImagePng.type] =
    PngRenderer

  /**
   * Simple parser that wraps the input SVG bytes in an SvgModel.
   */
  private object SvgParser extends SimpleParser[ImageSvgXml.type, M] {
    override def parse(input: FileContent[ImageSvgXml.type]): M =
      FileContent[ImageSvgXml.type](input.data, ImageSvgXml)
  }

  /**
   * Renderer that uses Apache Batik to convert the raw SVG bytes to PNG.
   */
  private object PngRenderer extends SimpleRenderer[M, ImagePng.type] {
    override def render(model: M): FileContent[ImagePng.type] =
      Try {
        Using.Manager { use =>
          val transcoder = new PNGTranscoder()

          // Input SVG
          val inStream        = use(new ByteArrayInputStream(model.data))
          val transcoderInput = new TranscoderInput(inStream)

          // Output PNG
          val outStream        = use(new ByteArrayOutputStream())
          val transcoderOutput = new TranscoderOutput(outStream)

          // Transcode
          transcoder.transcode(transcoderInput, transcoderOutput)

          // Retrieve bytes
          FileContent[ImagePng.type](outStream.toByteArray, ImagePng)
        }.get
      } match {
        case Failure(ex: TranscoderException) =>
          throw RendererError(
            s"Failed to transcode SVG to PNG: ${ex.getMessage}",
            Some(ex)
          )
        case Failure(other) =>
          throw RendererError(
            s"Rendering SVG to PNG failed: ${other.getMessage}",
            Some(other)
          )
        case Success(fc) => fc
      }
  }
}
