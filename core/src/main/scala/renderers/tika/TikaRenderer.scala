package com.tjclp.xlcr
package renderers.tika

import java.nio.charset.StandardCharsets

import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }

import models.FileContent
import models.tika.TikaModel
import renderers.SimpleRenderer
import types.MimeType

/**
 * TikaRenderer is a base trait for rendering TikaModel[M] to a FileContent[O].
 *
 * M = Input mime type (the TikaModel's type) O = Output mime type
 */
trait TikaRenderer[O <: MimeType] extends SimpleRenderer[TikaModel[O], O] {
  implicit val mimeTag: ClassTag[O]

  // Explicitly declare mime type
  val mimeType: O

  /**
   * Render the TikaModel into output bytes, returning a FileContent
   *
   * @throws RendererError
   *   on failure
   */
  @throws[RendererError]
  override def render(model: TikaModel[O]): FileContent[O] =
    Try {
      val textBytes = model.text.getBytes(StandardCharsets.UTF_8)
      FileContent[O](textBytes, mimeType)
    } match {
      case Failure(ex) =>
        throw new TikaRenderError(
          s"Failed to render ${mimeTag.runtimeClass.getSimpleName}: ${ex.getMessage}",
          Some(ex)
        )
      case Success(fc) => fc
    }

  /**
   * Optional helper to convert metadata to a human-readable string.
   */
  protected def formatMetadata(meta: Map[String, String]): String =
    meta.map { case (k, v) => s"$k: $v" }.mkString("\n")
}

class TikaRenderError(message: String, cause: Option[Throwable])
    extends RendererError(message, cause)
