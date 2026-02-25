package com.tjclp.xlcr.transform

import com.tjclp.xlcr.types.*

import zio.*
import zio.stream.ZStream

/**
 * A Splitter is a 1:N Transform that splits content into multiple fragments of the same output MIME
 * type.
 *
 * This is used when the output MIME type is known at compile time and is the same for all
 * fragments.
 *
 * Examples:
 *   - XLSX → Seq[XLSX] (split spreadsheet into individual sheets)
 *   - PPTX → Seq[PPTX] (split presentation into individual slides)
 *   - PDF → Seq[PDF] (split document into individual pages)
 *   - DOCX → Seq[DOCX] (split document by headings or pages)
 *
 * @tparam I
 *   Input MIME type
 * @tparam O
 *   Output MIME type (same for all fragments)
 */
trait Splitter[I <: Mime, O <: Mime] extends Transform[I, O]:

  /**
   * Split input content into multiple fragments.
   *
   * Implementations should override this method instead of `apply`.
   *
   * @param input
   *   The input content to split
   * @return
   *   A ZIO effect producing a Chunk of fragments
   */
  def split(input: Content[I]): ZIO[Any, TransformError, Chunk[Fragment[O]]]

  /**
   * Implementation of Transform.apply that delegates to split. Extracts the content from each
   * fragment.
   */
  override def apply(input: Content[I]): ZIO[Any, TransformError, Chunk[Content[O]]] =
    split(input).map(_.map(_.content))

  /**
   * Stream fragments as they become available.
   *
   * Default implementation materializes all fragments first. Override for true streaming where
   * fragments can be produced incrementally.
   */
  def streamFragments(input: Content[I]): ZStream[Any, TransformError, Fragment[O]] =
    ZStream.fromIterableZIO(split(input))
end Splitter

object Splitter:

  /**
   * Create a Splitter from a function.
   */
  def apply[I <: Mime, O <: Mime](
    f: Content[I] => ZIO[Any, TransformError, Chunk[Fragment[O]]]
  ): Splitter[I, O] =
    new Splitter[I, O]:
      override def split(input: Content[I]): ZIO[Any, TransformError, Chunk[Fragment[O]]] =
        f(input)

  /**
   * Create a Splitter with a specific priority.
   */
  def withPriority[I <: Mime, O <: Mime](
    prio: Int
  )(f: Content[I] => ZIO[Any, TransformError, Chunk[Fragment[O]]]): Splitter[I, O] =
    new Splitter[I, O]:
      override def split(input: Content[I]): ZIO[Any, TransformError, Chunk[Fragment[O]]] =
        f(input)
      override def priority: Int = prio

  /**
   * Create a Splitter with a name and priority.
   */
  def named[I <: Mime, O <: Mime](
    splitterName: String,
    prio: Int = 0
  )(f: Content[I] => ZIO[Any, TransformError, Chunk[Fragment[O]]]): Splitter[I, O] =
    new Splitter[I, O]:
      override def split(input: Content[I]): ZIO[Any, TransformError, Chunk[Fragment[O]]] =
        f(input)
      override def priority: Int = prio
      override def name: String  = splitterName

  /**
   * Create a Splitter from a potentially throwing function.
   */
  def fromThrowing[I <: Mime, O <: Mime](
    f: Content[I] => Chunk[Fragment[O]]
  ): Splitter[I, O] =
    new Splitter[I, O]:
      override def split(input: Content[I]): ZIO[Any, TransformError, Chunk[Fragment[O]]] =
        ZIO.attempt(f(input)).mapError(TransformError.fromThrowable)
end Splitter

/**
 * A DynamicSplitter is a 1:N Transform that splits content into multiple fragments where each
 * fragment can have a different MIME type determined at runtime.
 *
 * This is used when the output MIME types are not known at compile time.
 *
 * Examples:
 *   - ZIP → Seq[Content[?]] (extract archive entries with varying MIME types)
 *   - EML → Seq[Content[?]] (extract email attachments with varying MIME types)
 *   - MSG → Seq[Content[?]] (extract Outlook message attachments)
 *   - TAR.GZ → Seq[Content[?]] (extract compressed archive entries)
 *
 * @tparam I
 *   Input MIME type
 */
trait DynamicSplitter[I <: Mime] extends Transform[I, Mime]:

  /**
   * Split input content into multiple dynamic fragments.
   *
   * Implementations should override this method instead of `apply`.
   *
   * @param input
   *   The input content to split
   * @return
   *   A ZIO effect producing a Chunk of dynamic fragments
   */
  def splitDynamic(input: Content[I]): ZIO[Any, TransformError, Chunk[DynamicFragment]]

  /**
   * Implementation of Transform.apply that delegates to splitDynamic. Extracts the content from
   * each fragment.
   */
  override def apply(input: Content[I]): ZIO[Any, TransformError, Chunk[Content[Mime]]] =
    splitDynamic(input).map(_.map(_.content))

  /**
   * Stream dynamic fragments as they become available.
   *
   * Default implementation materializes all fragments first. Override for true streaming where
   * fragments can be produced incrementally.
   */
  def streamDynamicFragments(input: Content[I]): ZStream[Any, TransformError, DynamicFragment] =
    ZStream.fromIterableZIO(splitDynamic(input))
end DynamicSplitter

object DynamicSplitter:

  /**
   * Create a DynamicSplitter from a function.
   */
  def apply[I <: Mime](
    f: Content[I] => ZIO[Any, TransformError, Chunk[DynamicFragment]]
  ): DynamicSplitter[I] =
    new DynamicSplitter[I]:
      override def splitDynamic(input: Content[I])
        : ZIO[Any, TransformError, Chunk[DynamicFragment]] =
        f(input)

  /**
   * Create a DynamicSplitter with a specific priority.
   */
  def withPriority[I <: Mime](
    prio: Int
  )(f: Content[I] => ZIO[Any, TransformError, Chunk[DynamicFragment]]): DynamicSplitter[I] =
    new DynamicSplitter[I]:
      override def splitDynamic(input: Content[I])
        : ZIO[Any, TransformError, Chunk[DynamicFragment]] =
        f(input)
      override def priority: Int = prio

  /**
   * Create a DynamicSplitter with a name and priority.
   */
  def named[I <: Mime](
    splitterName: String,
    prio: Int = 0
  )(f: Content[I] => ZIO[Any, TransformError, Chunk[DynamicFragment]]): DynamicSplitter[I] =
    new DynamicSplitter[I]:
      override def splitDynamic(input: Content[I])
        : ZIO[Any, TransformError, Chunk[DynamicFragment]] =
        f(input)
      override def priority: Int = prio
      override def name: String  = splitterName

  /**
   * Create a DynamicSplitter from a potentially throwing function.
   */
  def fromThrowing[I <: Mime](
    f: Content[I] => Chunk[DynamicFragment]
  ): DynamicSplitter[I] =
    new DynamicSplitter[I]:
      override def splitDynamic(input: Content[I])
        : ZIO[Any, TransformError, Chunk[DynamicFragment]] =
        ZIO.attempt(f(input)).mapError(TransformError.fromThrowable)
end DynamicSplitter
