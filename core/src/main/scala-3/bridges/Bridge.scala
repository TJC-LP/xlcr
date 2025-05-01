package com.tjclp.xlcr.bridges

import com.tjclp.xlcr.models.{FileContent, Model}
import com.tjclp.xlcr.parsers.Parser
import com.tjclp.xlcr.renderers.Renderer
import com.tjclp.xlcr.types.{MimeType, Priority}
import com.tjclp.xlcr.utils.Prioritized
import com.tjclp.xlcr.{BridgeError, ParserError, RendererError, UnsupportedConversionError}

import scala.reflect.ClassTag

/**
 * A Bridge ties together a Parser and a Renderer to transform
 * FileContent[I] -> Model -> FileContent[O].
 *
 * @tparam M The internal model type
 * @tparam I The input MimeType
 * @tparam O The output MimeType
 */
trait Bridge[M <: Model, I <: MimeType, O <: MimeType](
                                                        using mTag: ClassTag[M], iTag: ClassTag[I], oTag: ClassTag[O]
                                                      ) extends Prioritized:
  
  /**
   * Default priority for bridges.
   * Specialized bridges (like Aspose bridges) should override this.
   */
  override def priority: Priority = Priority.DEFAULT
  /**
   * Convert input: FileContent[I] -> M -> FileContent[O]
   */
  @throws[BridgeError]
  def convert(input: FileContent[I]): FileContent[O] =
    val model = parseInput(input)
    render(model)

  /**
   * Parse input file content into an internal model M
   *
   * @throws ParserError on parse failures
   */
  @throws[ParserError]
  def parseInput(input: FileContent[I]): M =
    inputParser.parse(input)

  /**
   * Render a model M into the output file content O
   *
   * @throws RendererError on render failures
   */
  @throws[RendererError]
  def render(model: M): FileContent[O] =
    outputRenderer.render(model)

  /**
   * Attempt to convert an output file back to a model (if we have an outputParser).
   */
  @throws[BridgeError]
  def convertBack(output: FileContent[O]): M =
    parseOutput(output)

  /**
   * Parse output file content (if outputParser is available) into an internal model M
   * for merging or diffing scenarios
   */
  @throws[ParserError]
  def parseOutput(output: FileContent[O]): M =
    outputParser match
      case Some(parser) => parser.parse(output)
      case None => throw ParserError(
        s"No output parser available to parse ${oTag.runtimeClass.getSimpleName} back into ${mTag.runtimeClass.getSimpleName}",
        None
      )

  /**
   * Attempt to chain this bridge with another, resulting in a new
   * Bridge from I -> the other bridge's output type.
   */
  def chain[O2 <: MimeType](that: Bridge[M, _, O2])(using o2Tag: ClassTag[O2]): Bridge[M, I, O2] =
    new Bridge[M, I, O2]:
      override protected def inputParser: Parser[I, M] = Bridge.this.inputParser

      override protected def outputParser: Option[Parser[O2, M]] = that.outputParser

      override protected def inputRenderer: Option[Renderer[M, I]] = Bridge.this.inputRenderer

      override protected def outputRenderer: Renderer[M, O2] = that.outputRenderer

  /**
   * @return Optional parser for the output mime type (useful if we want parseOutput)
   */
  protected def outputParser: Option[Parser[O, M]] = None

  /**
   * @return Optional renderer for the input mime type (if we want to do a "reverse" from M to I)
   */
  protected def inputRenderer: Option[Renderer[M, I]] = None

  /**
   * Convert with diff: merges the source FileContent[I] into the existingFileContent[O],
   * requiring that M is Mergeable. By default, throws if not implemented.
   */
  def convertWithDiff(source: FileContent[I], existing: FileContent[O]): FileContent[O] =
    throw UnsupportedConversionError(
      s"No diff/merge supported for $iTag => $oTag (model: ${mTag.runtimeClass.getSimpleName})."
    )

  /**
   * @return Parser for the input mime type
   */
  protected def inputParser: Parser[I, M]

  /**
   * @return Renderer for the output mime type
   */
  protected def outputRenderer: Renderer[M, O]