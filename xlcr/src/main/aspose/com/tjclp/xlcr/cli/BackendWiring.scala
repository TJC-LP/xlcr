package com.tjclp.xlcr.cli

import com.tjclp.xlcr.aspose.*
import com.tjclp.xlcr.transform.TransformError
import com.tjclp.xlcr.types.*

import zio.*

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
    AsposeTransforms.canConvertLicensed(from, to)

  def asposeCanSplit(mime: Mime): Boolean =
    AsposeTransforms.canSplitLicensed(mime)

  def checkAsposeStatus(): Unit =
    if AsposeLicenseV2.licenseAvailable then
      println("    License: Found (total)")
    else
      // Probe each product to check individual per-product licenses
      val products = Seq(
        (
          "Words",
          () =>
            AsposeLicenseV2.initWords(); AsposeLicenseV2.isLicensed[Words]
        ),
        (
          "Cells",
          () =>
            AsposeLicenseV2.initCells(); AsposeLicenseV2.isLicensed[Cells]
        ),
        (
          "Email",
          () =>
            AsposeLicenseV2.initEmail(); AsposeLicenseV2.isLicensed[Email]
        ),
        (
          "Slides",
          () =>
            AsposeLicenseV2.initSlides(); AsposeLicenseV2.isLicensed[Slides]
        ),
        (
          "Pdf",
          () =>
            AsposeLicenseV2.initPdf(); AsposeLicenseV2.isLicensed[Pdf]
        ),
        (
          "Zip",
          () =>
            AsposeLicenseV2.initZip(); AsposeLicenseV2.isLicensed[Zip]
        )
      )
      val licensed = products.filter(_._2()).map(_._1)
      if licensed.nonEmpty then
        println(s"    License: Per-product (${licensed.mkString(", ")})")
      else println("    License: Not found (conversions will fall back to LibreOffice)")
    end if
    println("    Supported conversions: DOCX/DOC/XLSX/XLS/PPTX/PPT -> PDF, PDF <-> HTML, etc.")
    println("    Supported splits: XLSX/XLS sheets, PPTX/PPT slides, PDF pages, DOCX sections")
  end checkAsposeStatus
end BackendWiring
