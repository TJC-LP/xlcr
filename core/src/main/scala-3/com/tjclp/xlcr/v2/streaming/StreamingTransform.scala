package com.tjclp.xlcr.v2.streaming

import zio.{ Chunk, ZIO }
import zio.stream.ZStream

import com.tjclp.xlcr.v2.transform.{ Transform, TransformError }
import com.tjclp.xlcr.v2.types.{ Content, Mime }

/**
 * A StreamingTransform processes input as a stream of chunks rather than loading the entire input
 * into memory.
 *
 * This is useful for processing large files that don't fit in memory, or for pipeline scenarios
 * where partial results can be emitted early.
 *
 * @tparam I
 *   Input MIME type
 * @tparam O
 *   Output MIME type
 */
trait StreamingTransform[I <: Mime, O <: Mime] extends Transform[I, O]:

  /**
   * Process streaming input and produce streaming output.
   *
   * This method receives input as a stream of byte chunks and should emit output Content elements
   * as they become available.
   *
   * @param input
   *   Stream of input byte chunks
   * @param inputMime
   *   The MIME type of the input
   * @return
   *   Stream of output Content elements
   */
  def streamChunked(
    input: ZStream[Any, Nothing, Chunk[Byte]],
    inputMime: I
  ): ZStream[Any, TransformError, Content[O]]

  /**
   * Default implementation of apply that collects the stream and materializes it.
   */
  override def apply(input: Content[I]): ZIO[Any, TransformError, Chunk[Content[O]]] =
    streamChunked(ZStream.succeed(input.data), input.mime).runCollect

  /**
   * Stream implementation that uses streamChunked.
   */
  override def stream(input: Content[I]): ZStream[Any, TransformError, Content[O]] =
    streamChunked(ZStream.succeed(input.data), input.mime)

object StreamingTransform:

  /**
   * Create a StreamingTransform from a function.
   */
  def apply[I <: Mime, O <: Mime](
    f: (ZStream[Any, Nothing, Chunk[Byte]], I) => ZStream[Any, TransformError, Content[O]]
  ): StreamingTransform[I, O] =
    new StreamingTransform[I, O]:
      override def streamChunked(
        input: ZStream[Any, Nothing, Chunk[Byte]],
        inputMime: I
      ): ZStream[Any, TransformError, Content[O]] =
        f(input, inputMime)

  /**
   * Create a StreamingTransform with a specific priority.
   */
  def withPriority[I <: Mime, O <: Mime](
    prio: Int
  )(f: (ZStream[Any, Nothing, Chunk[Byte]], I) => ZStream[Any, TransformError, Content[O]])
    : StreamingTransform[I, O] =
    new StreamingTransform[I, O]:
      override def streamChunked(
        input: ZStream[Any, Nothing, Chunk[Byte]],
        inputMime: I
      ): ZStream[Any, TransformError, Content[O]] =
        f(input, inputMime)
      override def priority: Int = prio

  /**
   * Lift a regular Transform to a StreamingTransform. This doesn't provide true streaming - it
   * materializes the input first.
   */
  def fromTransform[I <: Mime, O <: Mime](transform: Transform[I, O]): StreamingTransform[I, O] =
    new StreamingTransform[I, O]:
      override def streamChunked(
        input: ZStream[Any, Nothing, Chunk[Byte]],
        inputMime: I
      ): ZStream[Any, TransformError, Content[O]] =
        ZStream.fromZIO(
          input.runCollect.flatMap { chunks =>
            val content = Content(chunks.flatten, inputMime)
            transform(content)
          }
        ).flattenChunks

      override def priority: Int = transform.priority
      override def name: String  = s"StreamingAdapter(${transform.name})"

/**
 * A ChunkedProcessor handles documents that can be processed in chunks. Useful for formats that
 * support incremental processing.
 */
trait ChunkedProcessor[I <: Mime, O <: Mime]:
  /**
   * Initialize processing state.
   */
  def init: ZIO[Any, TransformError, ChunkedProcessor.State[O]]

  /**
   * Process a chunk of input bytes.
   *
   * @param state
   *   Current processing state
   * @param chunk
   *   Input bytes to process
   * @return
   *   Updated state and any output produced
   */
  def processChunk(
    state: ChunkedProcessor.State[O],
    chunk: Chunk[Byte]
  ): ZIO[Any, TransformError, (ChunkedProcessor.State[O], Chunk[Content[O]])]

  /**
   * Finalize processing and produce any remaining output.
   *
   * @param state
   *   Final processing state
   * @return
   *   Any remaining output
   */
  def finalize(state: ChunkedProcessor.State[O]): ZIO[Any, TransformError, Chunk[Content[O]]]

object ChunkedProcessor:
  /**
   * Opaque processing state type.
   */
  opaque type State[O] = Any

  object State:
    def apply[O](value: Any): State[O] = value
    extension [O](state: State[O])
      def underlying: Any = state
