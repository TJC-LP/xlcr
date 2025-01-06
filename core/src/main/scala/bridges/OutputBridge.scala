package com.tjclp.xlcr
package bridges

import models.Model
import types.MimeType

/**
 * An OutputBridge is responsible for taking our model (a list of SheetData)
 * and producing raw bytes in a specific output mimeType (e.g., JSON, Markdown).
 */
trait OutputBridge[M <: Model, O <: MimeType] {
  /**
   * Convert the model (in this case, a list of SheetData)
   * into raw bytes for the given output mimeType.
   */
  def render(model: M): Array[Byte]
}