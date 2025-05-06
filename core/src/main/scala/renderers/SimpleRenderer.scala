package com.tjclp.xlcr
package renderers

import models.{ FileContent, Model }
import types.MimeType

/**
 * A simple renderer implementation that provides backward compatibility with existing renderers by
 * delegating to a configuration-less render method.
 *
 * This trait allows existing renderers to be easily adapted to support the new Renderer interface
 * with minimal changes.
 *
 * @tparam M
 *   Model type to render
 * @tparam O
 *   Output MimeType
 */
trait SimpleRenderer[M <: Model, O <: MimeType] extends Renderer[M, O] {

  /**
   * Render implementation that ignores the configuration. Subclasses should implement this method
   * instead of overriding render.
   *
   * @param model
   *   The model to render
   * @return
   *   The rendered file content
   * @throws RendererError
   *   if rendering fails
   */

  @throws[RendererError]
  def render(model: M): FileContent[O]

  /**
   * Implements the Renderer.render method by delegating to renderSimple. This provides
   * compatibility with the new configuration-aware interface.
   *
   * @param model
   *   The model to render
   * @param config
   *   Optional renderer-specific configuration (ignored in SimpleRenderer)
   * @return
   *   The rendered file content
   * @throws RendererError
   *   if rendering fails
   */
  override final def render(
    model: M,
    config: Option[RendererConfig] = None
  ): FileContent[O] =
    render(model)
}

/** Companion object providing factory methods for SimpleRenderer. */
object SimpleRenderer {

  /**
   * Creates a SimpleRenderer implementation from a function.
   *
   * @param renderFn
   *   The function that implements the rendering logic
   * @tparam M
   *   The model type
   * @tparam O
   *   The output MimeType
   * @return
   *   A SimpleRenderer implementation
   */
  def apply[M <: Model, O <: MimeType](
    renderFn: M => FileContent[O]
  ): SimpleRenderer[M, O] = {
    (model: M) => renderFn(model)
  }
}
