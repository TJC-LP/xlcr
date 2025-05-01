package com.tjclp.xlcr
package bridges

import models.{FileContent, Model}
import parsers.Parser
import renderers.Renderer
import types.{MimeType, Priority}
import utils.Prioritized

import com.tjclp.xlcr.{
  BridgeError,
  ParserError,
  RendererError,
  UnsupportedConversionError
}

import scala.reflect.ClassTag

/** BaseBridge provides a common implementation for both Scala 2 and 3.
  * It defines the core functionality for bridges that transform content from one format to another.
  *
  * This trait is extended by the version-specific Bridge traits to handle the differences
  * between Scala 2 and 3 syntax for implicit parameters.
  *
  * @tparam M The internal model type
  * @tparam I The input MimeType
  * @tparam O The output MimeType
  */
trait BaseBridge[M <: Model, I <: MimeType, O <: MimeType] extends Prioritized {
  // This requires ClassTags to be available, either implicitly (Scala 3) or explicitly (Scala 2)
  def mTag: ClassTag[M]
  def iTag: ClassTag[I]
  def oTag: ClassTag[O]

  /** Default priority for bridges.
    * Specialized bridges (like high-priority bridges) should override this.
    */
  override def priority: Priority = Priority.DEFAULT

  /** Convert input: FileContent[I] -> M -> FileContent[O]
    */
  @throws[BridgeError]
  def convert(input: FileContent[I]): FileContent[O] = {
    val model = parseInput(input)
    render(model)
  }

  /** Parse input file content into an internal model M
    *
    * @throws ParserError on parse failures
    */
  @throws[ParserError]
  def parseInput(input: FileContent[I]): M = {
    inputParser.parse(input)
  }

  /** Render a model M into the output file content O
    *
    * @throws RendererError on render failures
    */
  @throws[RendererError]
  def render(model: M): FileContent[O] = {
    outputRenderer.render(model)
  }
  
  // Protected accessors for inputParser and outputRenderer - unneeded with private[bridges]
  // private def protected_inputParser: Parser[I, M] = inputParser
  // private def protected_outputRenderer: Renderer[M, O] = outputRenderer

  /** Attempt to convert an output file back to a model (if we have an outputParser).
    */
  @throws[BridgeError]
  def convertBack(output: FileContent[O]): M = {
    parseOutput(output)
  }

  /** Parse output file content (if outputParser is available) into an internal model M
    * for merging or diffing scenarios
    */
  @throws[ParserError]
  def parseOutput(output: FileContent[O]): M = {
    outputParser match {
      case Some(parser) => parser.parse(output)
      case None =>
        throw ParserError(
          s"No output parser available to parse ${oTag.runtimeClass.getSimpleName} back into ${mTag.runtimeClass.getSimpleName}",
          None
        )
    }
  }

  /** @return Optional parser for the output mime type (useful if we want parseOutput)
    */
  def outputParser: Option[Parser[O, M]] = None

  /** @return Optional renderer for the input mime type (if we want to do a "reverse" from M to I)
    */
  def inputRenderer: Option[Renderer[M, I]] = None

  /** Convert with diff: merges the source FileContent[I] into the existingFileContent[O],
    * requiring that M is Mergeable. By default, throws if not implemented.
    */
  def convertWithDiff(
      source: FileContent[I],
      existing: FileContent[O]
  ): FileContent[O] = {
    throw UnsupportedConversionError(
      s"No diff/merge supported for $iTag => $oTag (model: ${mTag.runtimeClass.getSimpleName})."
    )
  }

  /** @return Parser for the input mime type
    */
  // Package-private visibility allows access within the bridges package
  private[bridges] def inputParser: Parser[I, M]

  /** @return Renderer for the output mime type
    */
  // Package-private visibility allows access within the bridges package  
  private[bridges] def outputRenderer: Renderer[M, O]
}
