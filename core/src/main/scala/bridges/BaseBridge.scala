package com.tjclp.xlcr
package bridges

import models.{FileContent, Model}
import parsers.Parser
import renderers.Renderer
import types.{MimeType, Prioritized, Priority}

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
    *
    * @param input The input file content to convert
    * @param config Optional bridge-specific configuration
    * @return The converted file content
    */
  @throws[BridgeError]
  def convert(
      input: FileContent[I],
      config: Option[BridgeConfig] = None
  ): FileContent[O] = {
    val model = parseInput(input, config)
    render(model, config)
  }

  /** Parse input file content into an internal model M
    *
    * @param input The input file content to parse
    * @param config Optional bridge-specific configuration
    * @throws ParserError on parse failures
    */
  @throws[ParserError]
  def parseInput(
      input: FileContent[I],
      config: Option[BridgeConfig] = None
  ): M = {
    // Extract and convert parser config if possible
    val parserConfig = getParserConfig(config)

    // Pass the parser config to the parser
    inputParser.parse(input, parserConfig)
  }

  /** Render a model M into the output file content O
    *
    * @param model The model to render
    * @param config Optional bridge-specific configuration
    * @throws RendererError on render failures
    */
  @throws[RendererError]
  def render(model: M, config: Option[BridgeConfig] = None): FileContent[O] = {
    // Extract and convert renderer config if possible
    val rendererConfig = getRendererConfig(config)

    // Pass the renderer config to the renderer
    outputRenderer.render(model, rendererConfig)
  }

  /** Extract parser configuration from bridge configuration.
    * Can be overridden by specific bridge implementations to provide custom conversion.
    *
    * @param config Optional bridge configuration
    * @return Optional parser configuration
    */
  protected def getParserConfig(
      config: Option[BridgeConfig]
  ): Option[parsers.ParserConfig] = None

  /** Extract renderer configuration from bridge configuration.
    * Can be overridden by specific bridge implementations to provide custom conversion.
    *
    * @param config Optional bridge configuration
    * @return Optional renderer configuration
    */
  protected def getRendererConfig(
      config: Option[BridgeConfig]
  ): Option[renderers.RendererConfig] = None

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
    *
    * @param source The source file content to merge
    * @param existing The existing file content to merge into
    * @param config Optional bridge-specific configuration
    * @return The merged file content
    */
  def convertWithDiff(
      source: FileContent[I],
      existing: FileContent[O],
      config: Option[BridgeConfig] = None
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
