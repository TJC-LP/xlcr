package com.tjclp.xlcr
package bridges.powerpoint

import bridges.MergeableSymmetricBridge
import models.powerpoint.SlidesData
import parsers.powerpoint.SlidesDataPowerPointParser
import renderers.powerpoint.SlidesDataPowerPointRenderer
import types.MimeType.ApplicationVndMsPowerpoint

/**
 * SlidesDataPowerPointBridge can parse PPT/PPTX bytes into SlidesData and render them back to PPTX
 * (application/vnd.ms-powerpoint). It also supports merge logic from MergeableSymmetricBridge.
 */
object SlidesDataPowerPointBridge
    extends MergeableSymmetricBridge[
      SlidesData,
      ApplicationVndMsPowerpoint.type
    ] {

  override protected def parser: SlidesDataPowerPointParser.type =
    SlidesDataPowerPointParser

  override protected def renderer = new SlidesDataPowerPointRenderer()
}
