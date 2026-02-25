package com.tjclp.xlcr.transform

import zio.{ Chunk, ZIO }

import com.tjclp.xlcr.types.{ Content, Mime }

/**
 * A Conversion is a 1:1 Transform that converts content from one MIME type to another.
 *
 * This is a marker trait that extends Transform and provides a simpler API for implementations that
 * always produce exactly one output for each input.
 *
 * Examples:
 *   - PDF → HTML (document conversion)
 *   - DOCX → PDF (document conversion)
 *   - HTML → PPTX (document conversion)
 *   - PNG → JPEG (image conversion)
 *
 * @tparam I
 *   Input MIME type
 * @tparam O
 *   Output MIME type
 */
trait Conversion[I <: Mime, O <: Mime] extends Transform[I, O]:

  /**
   * Convert input content to output content.
   *
   * Implementations should override this method instead of `apply`.
   *
   * @param input
   *   The input content to convert
   * @return
   *   A ZIO effect producing the converted content
   */
  def convert(input: Content[I]): ZIO[Any, TransformError, Content[O]]

  /**
   * Final implementation of Transform.apply that delegates to convert. Always returns a Chunk of
   * exactly one element.
   */
  final override def apply(input: Content[I]): ZIO[Any, TransformError, Chunk[Content[O]]] =
    convert(input).map(Chunk.single)

object Conversion:

  /**
   * Create a Conversion from a function.
   */
  def apply[I <: Mime, O <: Mime](
    f: Content[I] => ZIO[Any, TransformError, Content[O]]
  ): Conversion[I, O] =
    new Conversion[I, O]:
      override def convert(input: Content[I]): ZIO[Any, TransformError, Content[O]] =
        f(input)

  /**
   * Create a Conversion with a specific priority.
   */
  def withPriority[I <: Mime, O <: Mime](
    prio: Int
  )(f: Content[I] => ZIO[Any, TransformError, Content[O]]): Conversion[I, O] =
    new Conversion[I, O]:
      override def convert(input: Content[I]): ZIO[Any, TransformError, Content[O]] =
        f(input)
      override def priority: Int = prio

  /**
   * Create a Conversion with a name and priority.
   */
  def named[I <: Mime, O <: Mime](
    conversionName: String,
    prio: Int = 0
  )(f: Content[I] => ZIO[Any, TransformError, Content[O]]): Conversion[I, O] =
    new Conversion[I, O]:
      override def convert(input: Content[I]): ZIO[Any, TransformError, Content[O]] =
        f(input)
      override def priority: Int = prio
      override def name: String  = conversionName

  /**
   * Create a pure (non-effectful) Conversion.
   */
  def pure[I <: Mime, O <: Mime](
    f: Content[I] => Content[O]
  ): Conversion[I, O] =
    new Conversion[I, O]:
      override def convert(input: Content[I]): ZIO[Any, TransformError, Content[O]] =
        ZIO.succeed(f(input))

  /**
   * Create a Conversion that may fail.
   */
  def attempt[I <: Mime, O <: Mime](
    f: Content[I] => Either[TransformError, Content[O]]
  ): Conversion[I, O] =
    new Conversion[I, O]:
      override def convert(input: Content[I]): ZIO[Any, TransformError, Content[O]] =
        ZIO.fromEither(f(input))

  /**
   * Create a Conversion from a potentially throwing function.
   */
  def fromThrowing[I <: Mime, O <: Mime](
    f: Content[I] => Content[O]
  ): Conversion[I, O] =
    new Conversion[I, O]:
      override def convert(input: Content[I]): ZIO[Any, TransformError, Content[O]] =
        ZIO.attempt(f(input)).mapError(TransformError.fromThrowable)

  /**
   * Identity conversion that passes content through unchanged. Useful as a starting point for
   * pipelines.
   */
  def identity[M <: Mime]: Conversion[M, M] =
    new Conversion[M, M]:
      override def convert(input: Content[M]): ZIO[Any, TransformError, Content[M]] =
        ZIO.succeed(input)
      override def name: String = "identity"
