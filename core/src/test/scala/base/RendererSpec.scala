package com.tjclp.xlcr
package base

import models.{FileContent, Model}
import renderers.Renderer
import types.MimeType

import org.scalatest.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait RendererSpec extends AnyFlatSpec with Matchers {

  /**
   * Helper method to render a model and compare with expected FileContent.
   */
  def renderAndCompare[M <: Model, O <: MimeType](
                                                   renderer: Renderer[M, O],
                                                   model: M,
                                                   expected: FileContent[O]
                                                 ): Unit = {
    val result = renderer.render(model)
    result.mimeType shouldBe expected.mimeType
    result.data shouldBe expected.data
  }

  /**
   * Check for expected RendererError on invalid model
   */
  def renderWithError[M <: Model, O <: MimeType](
                                                  renderer: Renderer[M, O],
                                                  model: M,
                                                  errorSubstring: String
                                                ): Unit = {
    val ex = intercept[RendererError] {
      renderer.render(model)
    }
    ex.message should include(errorSubstring)
  }
}