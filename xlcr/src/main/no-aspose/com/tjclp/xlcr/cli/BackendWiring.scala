package com.tjclp.xlcr.cli

import zio.{ Chunk, ZIO }

import com.tjclp.xlcr.transform.{ TransformError, UnsupportedConversion }
import com.tjclp.xlcr.types.{ Content, ConvertOptions, DynamicFragment, Mime }

/**
 * Aspose backend stubs — compiled when Aspose is NOT available (no license at build time).
 *
 * All operations return UnsupportedConversion, causing the unified dispatch to fall through
 * to LibreOffice/Core backends.
 */
object BackendWiring:

  val asposeAvailable: Boolean = false

  def asposeConvert(
    input: Content[Mime],
    to: Mime,
    options: ConvertOptions
  ): ZIO[Any, TransformError, Content[Mime]] =
    ZIO.fail(UnsupportedConversion(input.mime, to))

  def asposeSplit(
    input: Content[Mime],
    options: ConvertOptions
  ): ZIO[Any, TransformError, Chunk[DynamicFragment]] =
    ZIO.fail(UnsupportedConversion(input.mime, input.mime))

  def asposeCanConvert(from: Mime, to: Mime): Boolean = false

  def asposeCanSplit(mime: Mime): Boolean = false

  def checkAsposeStatus(): Unit =
    println("    Status: Not included in this build (no Aspose license detected at build time)")
    println("    To enable: add license file to core-aspose/resources/ or set ASPOSE_TOTAL_LICENSE_B64")
