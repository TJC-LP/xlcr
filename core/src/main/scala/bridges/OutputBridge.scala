package com.tjclp.xlcr
package bridges

import models.Model
import types.MimeType

/**
 * An OutputBridge is responsible for taking our model (a list of SheetData)
 * and producing raw bytes in a specific output mimeType (e.g., JSON, Markdown).
 */
trait OutputBridge[M <: Model[_], O <: MimeType] {
  /** The output MIME type, e.g. application/json */
  def outputMimeType: O

  def modelType: M

  /**
   * Convert the model (in this case, a list of SheetData)
   * into raw bytes for the given output mimeType.
   */
  def render(model: M): Array[Byte]
}