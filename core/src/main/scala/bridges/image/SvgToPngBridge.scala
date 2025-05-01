package com.tjclp.xlcr
package bridges.image

import bridges.SimpleBridge
import models.FileContent
import parsers.Parser
import renderers.Renderer
import types.MimeType.{ImagePng, ImageSvgXml}

import org.apache.batik.transcoder.image.PNGTranscoder
import org.apache.batik.transcoder.{
  TranscoderException,
  TranscoderInput,
  TranscoderOutput
}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.util.{Failure, Success, Try}

/** SvgToPngBridge parses an SVG (image/svg+xml) into SvgModel,
  * then renders that model to PNG (image/png) using Apache Batik.
  */
object SvgToPngBridge extends SimpleBridge[ImageSvgXml.type, ImagePng.type] {
  override type M = FileContent[ImageSvgXml.type]

  override private[bridges] def inputParser: Parser[ImageSvgXml.type, M] =
    SvgParser

  override private[bridges] def outputRenderer: Renderer[M, ImagePng.type] =
    PngRenderer

  /** Simple parser that wraps the input SVG bytes in an SvgModel.
    */
  private object SvgParser extends Parser[ImageSvgXml.type, M] {
    override def parse(input: FileContent[ImageSvgXml.type]): M =
      FileContent[ImageSvgXml.type](input.data, ImageSvgXml)
  }

  /** Renderer that uses Apache Batik to convert the raw SVG bytes to PNG.
    */
  private object PngRenderer extends Renderer[M, ImagePng.type] {
    override def render(model: M): FileContent[ImagePng.type] =
      Try {
        val transcoder = new PNGTranscoder()

        // Input SVG
        val inStream = new ByteArrayInputStream(model.data)
        val transcoderInput = new TranscoderInput(inStream)

        // Output PNG
        val outStream = new ByteArrayOutputStream()
        val transcoderOutput = new TranscoderOutput(outStream)

        // Transcode
        transcoder.transcode(transcoderInput, transcoderOutput)

        // Retrieve bytes
        val pngBytes = outStream.toByteArray
        inStream.close()
        outStream.close()

        FileContent[ImagePng.type](pngBytes, ImagePng)
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
