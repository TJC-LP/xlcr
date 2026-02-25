package com.tjclp.xlcr.pipeline

import zio.{ Chunk, ZIO }
import zio.stream.ZStream

import com.tjclp.xlcr.registry.CanTransform
import com.tjclp.xlcr.transform.{ DynamicSplitter, Splitter, Transform, TransformError }
import com.tjclp.xlcr.types.{ Content, DynamicFragment, Fragment, Mime }

/**
 * High-level Pipeline API for the v2 Transform algebra.
 *
 * Provides typed, compile-time-verified helpers for common transform operations:
 *   - Single content conversion
 *   - Multi-step pipelines with intermediate formats
 *   - Batch processing
 *   - Streaming operations
 *   - Typed and dynamic splitting
 *
 * Usage:
 * {{{
 * import com.tjclp.xlcr.core.given
 *
 * // Simple conversion
 * Pipeline.convert[Mime.Pdf, Mime.Html](pdfContent)
 *
 * // Fluent builder
 * Pipeline
 *   .from(Mime.pdf)
 *   .via(Mime.html)
 *   .to(Mime.pptx)
 *   .run(pdfContent)
 * }}}
 */
object Pipeline:

  // ==========================================================================
  // Core Operations
  // ==========================================================================

  /**
   * Convert content from one MIME type to another using compile-time evidence.
   *
   * This requires a CanTransform[I, O] in scope, which is derived from in-scope given Transform
   * instances (conversions or splitters).
   */
  def convert[I <: Mime, O <: Mime](
    input: Content[I]
  )(using ct: CanTransform[I, O]): ZIO[Any, TransformError, Content[O]] =
    val transform = ct.transform
    transform(input).flatMap(firstOrFail(_, transform.name))

  /**
   * Convert content with compile-time type verification (alias of convert).
   */
  def convertTyped[I <: Mime, O <: Mime](
    input: Content[I]
  )(using ct: CanTransform[I, O]): ZIO[Any, TransformError, Content[O]] =
    convert(input)

  /**
   * Transform content, potentially producing multiple outputs.
   */
  def transform[I <: Mime, O <: Mime](
    input: Content[I]
  )(using ct: CanTransform[I, O]): ZIO[Any, TransformError, Chunk[Content[O]]] =
    ct.transform(input)

  /**
   * Transform content with compile-time type verification (alias of transform).
   */
  def transformTyped[I <: Mime, O <: Mime](
    input: Content[I]
  )(using ct: CanTransform[I, O]): ZIO[Any, TransformError, Chunk[Content[O]]] =
    transform(input)

  /**
   * Split content into typed fragments using an in-scope Splitter.
   */
  def split[I <: Mime, O <: Mime](
    input: Content[I]
  )(using splitter: Splitter[I, O]): ZIO[Any, TransformError, Chunk[Fragment[O]]] =
    splitter.split(input)

  /**
   * Split content into dynamic fragments using an in-scope DynamicSplitter.
   */
  def splitDynamic[I <: Mime](
    input: Content[I]
  )(using splitter: DynamicSplitter[I]): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    splitter.splitDynamic(input)

  /**
   * Split content using a typed Splitter and widen results to DynamicFragment.
   */
  def splitToDynamic[I <: Mime, O <: Mime](
    input: Content[I]
  )(using splitter: Splitter[I, O]): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    splitter.split(input).map(_.map(DynamicFragment.from))

  /**
   * Stream transform results as they become available.
   */
  def stream[I <: Mime, O <: Mime](
    input: Content[I]
  )(using ct: CanTransform[I, O]): ZStream[Any, TransformError, Content[O]] =
    ct.transform.stream(input)

  // ==========================================================================
  // Batch Operations
  // ==========================================================================

  /**
   * Convert multiple contents in parallel.
   */
  def convertAll[I <: Mime, O <: Mime](
    inputs: Chunk[Content[I]]
  )(using ct: CanTransform[I, O]): ZIO[Any, TransformError, Chunk[Content[O]]] =
    ZIO.foreachPar(inputs)(convert(_))

  /**
   * Convert multiple contents sequentially.
   */
  def convertAllSeq[I <: Mime, O <: Mime](
    inputs: Chunk[Content[I]]
  )(using ct: CanTransform[I, O]): ZIO[Any, TransformError, Chunk[Content[O]]] =
    ZIO.foreach(inputs)(convert(_))

  // ==========================================================================
  // Pipeline Builder (Fluent API)
  // ==========================================================================

  /**
   * Start building a pipeline from a specific MIME type.
   */
  def from[I <: Mime](mime: I): PipelineBuilder[I, I] =
    PipelineBuilder.initial(mime)

  private[pipeline] def firstOrFail[O <: Mime](
    results: Chunk[Content[O]],
    transformName: String
  ): ZIO[Any, TransformError, Content[O]] =
    ZIO.fromOption(results.headOption)
      .orElseFail(TransformError.validation(s"$transformName produced no outputs"))

/**
 * Fluent builder for constructing multi-step transform pipelines.
 *
 * @tparam I
 *   Original input MIME type
 * @tparam C
 *   Current intermediate MIME type
 */
final class PipelineBuilder[I <: Mime, C <: Mime] private (
  inputMime: I,
  currentMime: C,
  transform: Transform[I, C]
):

  /**
   * Add an intermediate format to the pipeline. Requires compile-time evidence that C -> M is
   * possible.
   */
  def via[M <: Mime](mime: M)(using ct: CanTransform[C, M]): PipelineBuilder[I, M] =
    new PipelineBuilder(inputMime, mime, transform >>> ct.transform)

  /**
   * Set the final output format and complete the pipeline.
   */
  def to[O <: Mime](mime: O)(using ct: CanTransform[C, O]): BuiltPipeline[I, O] =
    new BuiltPipeline(inputMime, mime, transform >>> ct.transform)

object PipelineBuilder:
  private[pipeline] def initial[M <: Mime](mime: M): PipelineBuilder[M, M] =
    new PipelineBuilder(mime, mime, Transform.identity[M])

/**
 * A fully constructed pipeline ready to execute.
 *
 * @tparam I
 *   Input MIME type
 * @tparam O
 *   Output MIME type
 */
final class BuiltPipeline[I <: Mime, O <: Mime] private[pipeline] (
  inputMime: I,
  outputMime: O,
  transform: Transform[I, O]
):

  /**
   * Run the pipeline on input content.
   */
  def run(input: Content[I]): ZIO[Any, TransformError, Content[O]] =
    transform(input).flatMap(Pipeline.firstOrFail(_, transform.name))

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
