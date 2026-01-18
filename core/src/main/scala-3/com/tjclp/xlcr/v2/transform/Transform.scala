package com.tjclp.xlcr.v2.transform

import zio.{ Chunk, ZIO }
import zio.stream.ZStream

import com.tjclp.xlcr.v2.types.{ Content, Mime }

/**
 * The unified Transform algebra for XLCR v2.
 *
 * A Transform represents a conversion from content of one MIME type to content of another MIME type
 * (possibly the same). The key insight is that both conversions and splits share the same algebra:
 *
 * `Content[I] → ZIO[Any, TransformError, Chunk[Content[O]]]`
 *
 *   - Conversions return a Chunk of size 1
 *   - Splits return a Chunk of size N
 *
 * This unified algebra enables:
 *   - Composable transform pipelines
 *   - Uniform error handling via ZIO
 *   - Streaming support for large files
 *   - Priority-based transform selection
 *
 * @tparam I
 *   Input MIME type
 * @tparam O
 *   Output MIME type
 */
trait Transform[I <: Mime, O <: Mime]:

  /**
   * Apply this transform to input content.
   *
   * @param input
   *   The input content to transform
   * @return
   *   A ZIO effect producing a Chunk of output content
   */
  def apply(input: Content[I]): ZIO[Any, TransformError, Chunk[Content[O]]]

  /**
   * Stream the transform output, producing each result element as it becomes available.
   *
   * Default implementation materializes the full result. Override for true streaming.
   *
   * @param input
   *   The input content to transform
   * @return
   *   A ZStream producing output content elements
   */
  def stream(input: Content[I]): ZStream[Any, TransformError, Content[O]] =
    ZStream.fromIterableZIO(apply(input))

  /**
   * Compose this transform with another, producing a transform from I to O2.
   *
   * The resulting transform applies this transform first, then applies the second transform to each
   * output element, flattening the results.
   *
   * @tparam O2
   *   The output type of the composed transform
   * @param that
   *   The transform to apply after this one
   * @return
   *   A composed transform
   */
  def andThen[O2 <: Mime](that: Transform[O, O2]): Transform[I, O2] =
    Transform.Composed(this, that)

  /**
   * Alias for andThen using the >>> operator.
   */
  def >>>[O2 <: Mime](that: Transform[O, O2]): Transform[I, O2] =
    andThen(that)

  /**
   * Priority of this transform for registry-based selection. Higher priority transforms are
   * preferred. Default is 0.
   */
  def priority: Int = 0

  /**
   * Human-readable name for this transform (for logging/debugging).
   */
  def name: String = getClass.getSimpleName

object Transform:

  /**
   * Composed transform that chains two transforms together.
   */
  private[transform] final class Composed[I <: Mime, M <: Mime, O <: Mime](
    first: Transform[I, M],
    second: Transform[M, O]
  ) extends Transform[I, O]:

    override def apply(input: Content[I]): ZIO[Any, TransformError, Chunk[Content[O]]] =
      for
        intermediate <- first(input)
        results      <- ZIO.foreach(intermediate)(second.apply)
      yield results.flatten

    override def stream(input: Content[I]): ZStream[Any, TransformError, Content[O]] =
      first.stream(input).flatMap(second.stream)

    // Composed transforms use the minimum priority of their components
    override def priority: Int = math.min(first.priority, second.priority)

    override def name: String = s"${first.name} >>> ${second.name}"

  /**
   * Create a Transform from a function.
   */
  def apply[I <: Mime, O <: Mime](
    f: Content[I] => ZIO[Any, TransformError, Chunk[Content[O]]]
  ): Transform[I, O] =
    new Transform[I, O]:
      override def apply(input: Content[I]): ZIO[Any, TransformError, Chunk[Content[O]]] =
        f(input)

  /**
   * Create a Transform that produces exactly one output from a function.
   */
  def single[I <: Mime, O <: Mime](
    f: Content[I] => ZIO[Any, TransformError, Content[O]]
  ): Transform[I, O] =
    new Transform[I, O]:
      override def apply(input: Content[I]): ZIO[Any, TransformError, Chunk[Content[O]]] =
        f(input).map(Chunk.single)

  /**
   * Create a Transform with a specific priority.
   */
  def withPriority[I <: Mime, O <: Mime](
    prio: Int
  )(f: Content[I] => ZIO[Any, TransformError, Chunk[Content[O]]]): Transform[I, O] =
    new Transform[I, O]:
      override def apply(input: Content[I]): ZIO[Any, TransformError, Chunk[Content[O]]] =
        f(input)
      override def priority: Int = prio

  /**
   * Identity transform that passes content through unchanged.
   */
  def identity[M <: Mime]: Transform[M, M] =
    new Transform[M, M]:
      override def apply(input: Content[M]): ZIO[Any, TransformError, Chunk[Content[M]]] =
        ZIO.succeed(Chunk.single(input))
      override def stream(input: Content[M]): ZStream[Any, TransformError, Content[M]] =
        ZStream.succeed(input)
      override def name: String = "identity"

  /**
   * Transform that always fails with the given error.
   */
  def fail[I <: Mime, O <: Mime](error: TransformError): Transform[I, O] =
    new Transform[I, O]:
      override def apply(input: Content[I]): ZIO[Any, TransformError, Chunk[Content[O]]] =
        ZIO.fail(error)
      override def name: String = s"fail(${error.message})"

  /**
   * Transform that always succeeds with empty output.
   */
  def empty[I <: Mime, O <: Mime]: Transform[I, O] =
    new Transform[I, O]:
      override def apply(input: Content[I]): ZIO[Any, TransformError, Chunk[Content[O]]] =
        ZIO.succeed(Chunk.empty)
      override def name: String = "empty"
