package com.tjclp.xlcr.v2.cli

import zio.{ Chunk, ZIO }

import com.tjclp.xlcr.v2.aspose.{ AsposeTransforms, AsposeLicenseV2 }
import com.tjclp.xlcr.v2.transform.TransformError
import com.tjclp.xlcr.v2.types.{ Content, ConvertOptions, DynamicFragment, Mime }

/**
 * Aspose backend wiring — compiled when Aspose is available (license detected at build time).
 *
 * Delegates to AsposeTransforms for conversions/splits and reports real license status.
 */
object BackendWiring:

  val asposeAvailable: Boolean = true

  def asposeConvert(
    input: Content[Mime],
    to: Mime,
    options: ConvertOptions
  ): ZIO[Any, TransformError, Content[Mime]] =
    AsposeTransforms.convert(input, to, options)

  def asposeSplit(
    input: Content[Mime],
    options: ConvertOptions
  ): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    AsposeTransforms.split(input, options)

  def asposeCanConvert(from: Mime, to: Mime): Boolean =
    AsposeTransforms.canConvert(from, to)

  def asposeCanSplit(mime: Mime): Boolean =
    AsposeTransforms.canSplit(mime)

  def checkAsposeStatus(): Unit =
    if AsposeLicenseV2.licenseAvailable then
      println("    License: Found (total)")
    else
      println("    License: Not found (per-product or env-var licenses may apply at runtime)")
    println("    Supported conversions: DOCX/DOC/XLSX/XLS/PPTX/PPT -> PDF, PDF <-> HTML, etc.")
    println("    Supported splits: XLSX/XLS sheets, PPTX/PPT slides, PDF pages, DOCX sections")
