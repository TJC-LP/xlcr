package com.tjclp.xlcr.server.routes

import com.tjclp.xlcr.cli.UnifiedTransforms
import com.tjclp.xlcr.server.http.ResponseBuilder
import com.tjclp.xlcr.server.json.*
import com.tjclp.xlcr.types.Mime

import zio.*
import zio.http.*
import zio.json.*

/**
 * Routes for capability discovery.
 *
 * GET /capabilities
 *
 * Returns the full list of supported conversions and split operations.
 *
 * Response:
 *   - 200: JSON with capabilities { "conversions": [{"from": "application/pdf", "to":
 *     "text/plain"}, ...], "splits": [{"mimeType": "application/pdf", "outputMimeType":
 *     "application/pdf"}, ...], "supportedInputTypes": ["application/pdf", ...],
 *     "supportedOutputTypes": ["text/plain", ...] }
 */
object CapabilitiesRoutes:

  import Codecs.given

  // Cache the capabilities response since it's static
  private lazy val cachedCapabilities: CapabilitiesResponse = buildCapabilities()

  val routes: Routes[Any, Response] = Routes(
    Method.GET / "capabilities" -> handler { (_: Request) =>
      ZIO.succeed(ResponseBuilder.json(cachedCapabilities.toJson))
    }
  )

  /**
   * Build the full capabilities matrix.
   *
   * Tests all combinations of input/output MIME types to find supported conversions.
   */
  private def buildCapabilities(): CapabilitiesResponse =
    // Common document types to test
    val documentTypes: List[Mime] = List(
      // Text
      Mime.plain,
      Mime.html,
      Mime.markdown,
      Mime.csv,
      Mime.tsv,
      Mime.xml,
      // Application
      Mime.json,
      Mime.pdf,
      Mime.rtf,
      // MS Office legacy
      Mime.doc,
      Mime.xls,
      Mime.ppt,
      // MS Office Open XML
      Mime.docx,
      Mime.xlsx,
      Mime.pptx,
      // MS Office macro-enabled
      Mime.xlsm,
      Mime.xlsb,
      // OpenDocument
      Mime.odt,
      Mime.ods,
      Mime.odp,
      // Email
      Mime.eml,
      Mime.msg,
      // Archives
      Mime.zip,
      Mime.sevenZip
    )

    // Common output types
    val outputTypes: List[Mime] = List(
      Mime.plain,
      Mime.html,
      Mime.xml,
      Mime.pdf,
      Mime.docx,
      Mime.xlsx,
      Mime.pptx,
      Mime.ods,
      Mime.odt,
      Mime.odp,
      Mime.png,
      Mime.jpeg,
      Mime.csv
    )

    // Find all conversion paths
    val conversions =
      for
        from <- documentTypes
        to   <- outputTypes
        if from != to && UnifiedTransforms.canConvert(from, to)
      yield ConversionCapability(from.value, to.value)

    // Find all splittable types
    val splits = documentTypes
      .filter(UnifiedTransforms.canSplit)
      .map { mime =>
        // For most splits, output type is same as input
        // (sheets from XLSX, pages from PDF, etc.)
        val outputMime = mime match
          case m if m == Mime.zip || m == Mime.msg || m == Mime.eml =>
            "application/octet-stream" // Dynamic output types
          case _ =>
            mime.value // Same as input
        SplitCapability(mime.value, outputMime)
      }

    // Collect supported input/output types
    val supportedInputTypes  = (conversions.map(_.from) ++ splits.map(_.mimeType)).distinct.sorted
    val supportedOutputTypes = conversions.map(_.to).distinct.sorted

    CapabilitiesResponse(
      conversions = conversions,
      splits = splits,
      supportedInputTypes = supportedInputTypes,
      supportedOutputTypes = supportedOutputTypes
    )
  end buildCapabilities
end CapabilitiesRoutes
