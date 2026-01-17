package com.tjclp.xlcr.v2.registry

import com.tjclp.xlcr.v2.transform.Transform
import com.tjclp.xlcr.v2.types.Mime
import scala.util.NotGiven

/**
 * Type class evidence that a transform exists from type I to type O.
 *
 * This enables compile-time verification of transform paths. When a `CanTransform[I, O]`
 * instance is in scope, it proves that there exists a way to transform content from
 * MIME type I to MIME type O (either directly or via intermediate transforms).
 *
 * Usage:
 * {{{
 * // Compile-time verified transform
 * def convert[I <: Mime, O <: Mime](input: Content[I])(using ct: CanTransform[I, O]): ZIO[Any, TransformError, Content[O]] =
 *   ct.transform(input)
 *
 * // With explicit evidence
 * given pdfToHtml: CanTransform[Mime.Pdf, Mime.Html] = CanTransform.from(myPdfToHtmlConversion)
 * }}}
 *
 * @tparam I Input MIME type
 * @tparam O Output MIME type
 */
trait CanTransform[I <: Mime, O <: Mime]:
  /** The transform that converts from I to O */
  def transform: Transform[I, O]

object CanTransform:

  /**
   * Create a CanTransform instance from an existing transform.
   */
  def from[I <: Mime, O <: Mime](t: Transform[I, O]): CanTransform[I, O] =
    new CanTransform[I, O]:
      val transform: Transform[I, O] = t

  /**
   * Lift any in-scope Transform into CanTransform evidence.
   *
   * This allows compile-time path resolution to start from given conversions
   * and splitters without runtime registration.
   */
  given direct[I <: Mime, O <: Mime](using t: Transform[I, O]): CanTransform[I, O] =
    from(t)

  /**
   * Identity instance - any type can transform to itself.
   */
  given identity[M <: Mime]: CanTransform[M, M] =
    from(Transform.identity[M])

  /**
   * Compose two transforms if we have evidence for I -> M and M -> O.
   *
   * This enables automatic path finding at compile time when all intermediate
   * steps have CanTransform instances in scope.
   */
  given composed[I <: Mime, M <: Mime, O <: Mime](
      using first: CanTransform[I, M],
      second: CanTransform[M, O],
      noDirect: NotGiven[Transform[I, O]]
  ): CanTransform[I, O] =
    from(first.transform >>> second.transform)

  /**
   * Summon a CanTransform instance if available.
   */
  inline def apply[I <: Mime, O <: Mime](using ct: CanTransform[I, O]): CanTransform[I, O] = ct

  /**
   * Check if a transform path exists at compile time.
   *
   * Usage:
   * {{{
   * CanTransform.verify[Mime.Pdf, Mime.Html]  // Compiles if path exists
   * }}}
   */
  inline def verify[I <: Mime, O <: Mime](using ct: CanTransform[I, O]): Unit = ()

  /**
   * Get the transform if evidence exists, otherwise None.
   *
   * This is useful when you want to attempt a transform without compile-time guarantees.
   */
  def optional[I <: Mime, O <: Mime](using
      ev: CanTransform[I, O] = null
  ): Option[Transform[I, O]] =
    Option(ev).map(_.transform)

/**
 * Extension methods for Content when CanTransform evidence is available.
 */
extension [I <: Mime](content: com.tjclp.xlcr.v2.types.Content[I])
  /**
   * Convert this content to another MIME type if a transform exists.
   */
  def convertTo[O <: Mime](using ct: CanTransform[I, O]): zio.ZIO[Any, com.tjclp.xlcr.v2.transform.TransformError, com.tjclp.xlcr.v2.types.Content[O]] =
    ct.transform(content).map(_.head)

  /**
   * Transform this content to another MIME type (may produce multiple outputs).
   */
  def transformTo[O <: Mime](using ct: CanTransform[I, O]): zio.ZIO[Any, com.tjclp.xlcr.v2.transform.TransformError, zio.Chunk[com.tjclp.xlcr.v2.types.Content[O]]] =
    ct.transform(content)
