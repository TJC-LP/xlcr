package com.tjclp.xlcr
package bridges

import models.Model
import types.MimeType

import java.nio.file.Path
import scala.util.Using

/**
 * An InputBridge is responsible for parsing raw bytes (for a specific
 * input mimeType) into a list of SheetData (our model).
 */
trait InputBridge[I <: MimeType, M <: Model] {
  /**
   * Parse raw bytes from an input file into our intermediate model.
   */
  def parse(inputBytes: Array[Byte]): M

  /**
   * Parse a file path into our intermediate model by reading the bytes.
   */
  def parse(path: Path): M = {
    Using.resource(java.nio.file.Files.newInputStream(path)) { is =>
      parse(is.readAllBytes())
    }
  }
}
