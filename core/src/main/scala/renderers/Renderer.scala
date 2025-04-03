package com.tjclp.xlcr
package renderers

import models.{FileContent, Model}
import types.MimeType

/**
 * A Renderer transforms a model M into an output file content of type O (MimeType).
 *
 * @tparam M model type to render
 * @tparam O output MimeType
 */
trait Renderer[M <: Model, O <: MimeType] {
  /**
   * Render model into output file content
   *
   * @param model The model to render
   * @return The rendered file content
   * @throws RendererError if rendering fails
   */
  @throws[RendererError]
  def render(model: M): FileContent[O]
}
