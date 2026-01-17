package com.tjclp.xlcr.v2.registry

import scala.quoted.*

import com.tjclp.xlcr.v2.transform.{Conversion, Splitter, Transform}
import com.tjclp.xlcr.v2.types.Mime

/**
 * Compile-time macros for given transform discovery and verification.
 *
 * These macros enable:
 * - Compile-time verification that a transform path exists
 * - Automatic composition of transforms via intermediate types
 * - Type-safe transform chain construction
 *
 * Usage:
 * {{{
 * // Verify path exists at compile time (fails compilation if not)
 * TransformMacros.requirePath[Mime.Docx, Mime.Html]
 *
 * // Check if path exists (returns Option)
 * val maybeTransform = TransformMacros.findPath[Mime.Pdf, Mime.Html]
 * }}}
 */
object TransformMacros:

  /**
   * Require a direct conversion exists at compile time.
   * Compilation fails if no `given Conversion[I, O]` is in scope.
   */
  inline def requireConversion[I <: Mime, O <: Mime](using c: Conversion[I, O]): Conversion[I, O] = c

  /**
   * Require a splitter exists at compile time.
   * Compilation fails if no `given Splitter[I, O]` is in scope.
   */
  inline def requireSplitter[I <: Mime, O <: Mime](using s: Splitter[I, O]): Splitter[I, O] = s

  /**
   * Require a transform path exists at compile time (direct or composed).
   * Uses CanTransform to find paths through intermediate types.
   */
  inline def requirePath[I <: Mime, O <: Mime](using ct: CanTransform[I, O]): Transform[I, O] =
    ct.transform

  /**
   * Get a transform if one exists in scope.
   */
  inline def findConversion[I <: Mime, O <: Mime]: Option[Conversion[I, O]] =
    ${ findConversionImpl[I, O] }

  /**
   * Get a splitter if one exists in scope.
   */
  inline def findSplitter[I <: Mime, O <: Mime]: Option[Splitter[I, O]] =
    ${ findSplitterImpl[I, O] }

  // ==========================================================================
  // Macro Implementations
  // ==========================================================================

  private def findConversionImpl[I <: Mime: Type, O <: Mime: Type](using Quotes): Expr[Option[Conversion[I, O]]] =
    import quotes.reflect.*

    // Search for an implicit Conversion[I, O] in scope
    Implicits.search(TypeRepr.of[Conversion[I, O]]) match
      case iss: ImplicitSearchSuccess =>
        '{ Some(${ iss.tree.asExprOf[Conversion[I, O]] }) }
      case _: ImplicitSearchFailure =>
        '{ None }

  private def findSplitterImpl[I <: Mime: Type, O <: Mime: Type](using Quotes): Expr[Option[Splitter[I, O]]] =
    import quotes.reflect.*

    // Search for an implicit Splitter[I, O] in scope
    Implicits.search(TypeRepr.of[Splitter[I, O]]) match
      case iss: ImplicitSearchSuccess =>
        '{ Some(${ iss.tree.asExprOf[Splitter[I, O]] }) }
      case _: ImplicitSearchFailure =>
        '{ None }
