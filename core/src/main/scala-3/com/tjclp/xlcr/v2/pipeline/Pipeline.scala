package com.tjclp.xlcr.v2.pipeline

import zio.{Chunk, ZIO}
import zio.stream.ZStream

import com.tjclp.xlcr.v2.registry.{CanTransform, PathFinder, TransformRegistry}
import com.tjclp.xlcr.v2.transform.{Transform, TransformError, UnsupportedConversion}
import com.tjclp.xlcr.v2.types.{Content, DynamicFragment, Mime}

/**
 * High-level Pipeline API for the v2 Transform algebra.
 *
 * Provides convenient methods for common transform operations:
 * - Single content conversion
 * - Multi-step pipelines with intermediate formats
 * - Batch processing
 * - Streaming operations
 *
 * Usage:
 * {{{
 * // Simple conversion
 * Pipeline.convert(pdfContent, Mime.html)
 *
 * // Fluent builder
 * Pipeline
 *   .from(Mime.pdf)
 *   .via(Mime.html)
 *   .to(Mime.pptx)
 *   .run(pdfContent)
 *
 * // With compile-time verification
 * Pipeline.convertTyped[Mime.Pdf, Mime.Html](pdfContent)
 * }}}
 */
object Pipeline:

  // ==========================================================================
  // Core Operations
  // ==========================================================================

  /**
   * Convert content from one MIME type to another using the best available path.
   *
   * This uses PathFinder to discover the optimal transform path at runtime.
   *
   * @param input Content to convert
   * @param to Target MIME type
   * @return Converted content (first result if multiple are produced)
   */
  def convert(input: Content[Mime], to: Mime): ZIO[Any, TransformError, Content[Mime]] =
    for
      transform <- ZIO.fromEither(PathFinder.getPath(input.mime, to))
      results <- transform(input)
      result <- ZIO.fromOption(results.headOption)
        .orElseFail(UnsupportedConversion(input.mime, to))
    yield result

  /**
   * Convert content with compile-time type verification.
   */
  def convertTyped[I <: Mime, O <: Mime](
      input: Content[I]
  )(using ct: CanTransform[I, O]): ZIO[Any, TransformError, Content[O]] =
    ct.transform(input).map(_.head)

  /**
   * Transform content, potentially producing multiple outputs.
   */
  def transform(input: Content[Mime], to: Mime): ZIO[Any, TransformError, Chunk[Content[Mime]]] =
    for
      transform <- ZIO.fromEither(PathFinder.getPath(input.mime, to))
      results <- transform(input)
    yield results

  /**
   * Transform content with compile-time type verification.
   */
  def transformTyped[I <: Mime, O <: Mime](
      input: Content[I]
  )(using ct: CanTransform[I, O]): ZIO[Any, TransformError, Chunk[Content[O]]] =
    ct.transform(input)

  /**
   * Split content into fragments using the registered splitter.
   */
  def split(input: Content[Mime]): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    TransformRegistry.findDynamicSplitter(input.mime) match
      case Some(splitter) => splitter.splitDynamic(input)
      case None =>
        // Try typed splitter, convert fragments to dynamic
        TransformRegistry.findSplitter(input.mime) match
          case Some(splitter) =>
            splitter(input).map(_.zipWithIndex.map { case (content, idx) =>
              DynamicFragment(content, idx, content.filename)
            })
          case None =>
            ZIO.fail(TransformError.unsupported(input.mime, input.mime))

  /**
   * Stream transform results as they become available.
   */
  def stream(input: Content[Mime], to: Mime): ZStream[Any, TransformError, Content[Mime]] =
    PathFinder.getPath(input.mime, to) match
      case Left(err) => ZStream.fail(err)
      case Right(transform) => transform.stream(input)

  // ==========================================================================
  // Batch Operations
  // ==========================================================================

  /**
   * Convert multiple contents in parallel.
   */
  def convertAll(
      inputs: Chunk[Content[Mime]],
      to: Mime
  ): ZIO[Any, TransformError, Chunk[Content[Mime]]] =
    ZIO.foreachPar(inputs)(convert(_, to))

  /**
   * Convert multiple contents sequentially.
   */
  def convertAllSeq(
      inputs: Chunk[Content[Mime]],
      to: Mime
  ): ZIO[Any, TransformError, Chunk[Content[Mime]]] =
    ZIO.foreach(inputs)(convert(_, to))

  // ==========================================================================
  // Pipeline Builder (Fluent API)
  // ==========================================================================

  /**
   * Start building a pipeline from a specific MIME type.
   */
  def from[I <: Mime](mime: I): PipelineBuilder[I, I] =
    PipelineBuilder.initial(mime)

  /**
   * Check if a conversion path exists between two MIME types.
   */
  def canConvert(from: Mime, to: Mime): Boolean =
    PathFinder.pathExists(from, to)

  /**
   * Describe the conversion path between two MIME types.
   */
  def describePath(from: Mime, to: Mime): String =
    PathFinder.describePath(from, to)

/**
 * Fluent builder for constructing multi-step transform pipelines.
 *
 * @tparam I Original input MIME type
 * @tparam C Current intermediate MIME type
 */
final class PipelineBuilder[I <: Mime, C <: Mime] private (
    inputMime: I,
    currentMime: C,
    transforms: List[Transform[Mime, Mime]]
):

  /**
   * Add an intermediate format to the pipeline.
   * Forces the conversion to go through this format.
   */
  def via[M <: Mime](mime: M): PipelineBuilder[I, M] =
    val transform = PathFinder.findPath(currentMime: Mime, mime: Mime)
      .getOrElse(Transform.fail(UnsupportedConversion(currentMime, mime)))
    new PipelineBuilder(inputMime, mime, transforms :+ transform)

  /**
   * Set the final output format and complete the pipeline.
   */
  def to[O <: Mime](mime: O): BuiltPipeline[I, O] =
    val transform = PathFinder.findPath(currentMime: Mime, mime: Mime)
      .getOrElse(Transform.fail(UnsupportedConversion(currentMime, mime)))
    val allTransforms = transforms :+ transform
    val composed = allTransforms.reduceLeft(_ >>> _)
    new BuiltPipeline(inputMime, mime, composed.asInstanceOf[Transform[I, O]])

object PipelineBuilder:
  private[pipeline] def initial[M <: Mime](mime: M): PipelineBuilder[M, M] =
    new PipelineBuilder(mime, mime, Nil)

/**
 * A fully constructed pipeline ready to execute.
 *
 * @tparam I Input MIME type
 * @tparam O Output MIME type
 */
final class BuiltPipeline[I <: Mime, O <: Mime] private[pipeline] (
    inputMime: I,
    outputMime: O,
    transform: Transform[I, O]
):

  /**
   * Run the pipeline on input content.
   *
   * @param input Content to process (must have correct MIME type)
   * @return First result (for conversions)
   */
  def run(input: Content[I]): ZIO[Any, TransformError, Content[O]] =
    transform(input).map(_.head)

  /**
   * Run the pipeline and return all results.
   */
  def runAll(input: Content[I]): ZIO[Any, TransformError, Chunk[Content[O]]] =
    transform(input)

  /**
   * Run the pipeline as a stream.
   */
  def runStream(input: Content[I]): ZStream[Any, TransformError, Content[O]] =
    transform.stream(input)

  /**
   * Run the pipeline on multiple inputs in parallel.
   */
  def runBatch(inputs: Chunk[Content[I]]): ZIO[Any, TransformError, Chunk[Content[O]]] =
    ZIO.foreachPar(inputs)(run)

  /**
   * Get the underlying transform.
   */
  def getTransform: Transform[I, O] = transform

  /**
   * Get the input MIME type.
   */
  def getInputMime: I = inputMime

  /**
   * Get the output MIME type.
   */
  def getOutputMime: O = outputMime
