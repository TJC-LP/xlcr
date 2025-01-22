package com.tjclp.xlcr.renderers

import com.tjclp.xlcr.models.{FileContent, Model}
import com.tjclp.xlcr.types.MimeType
import com.tjclp.xlcr.RendererError

/**
 * A Renderer transforms a model M into an output file content of type O (MimeType).
 *
 * @tparam M model type to render
 * @tparam O output MimeType
 */
trait Renderer[M <: Model, O <: MimeType]:
  /**
   * Render model into output file content
   * @param model The model to render
   * @return The rendered file content
   * @throws RendererError if rendering fails
   */
  @throws[RendererError]
  def render(model: M): FileContent[O]