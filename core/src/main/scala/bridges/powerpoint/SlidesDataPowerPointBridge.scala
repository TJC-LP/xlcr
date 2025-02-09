package com.tjclp.xlcr
package bridges.powerpoint

import bridges.MergeableSymmetricBridge
import models.ppt.SlidesData
import parsers.ppt.SlidesDataPowerPointParser
import renderers.ppt.SlidesDataPowerPointRenderer
import types.MimeType
import types.MimeType.ApplicationVndMsPowerpoint

/**
 * SlidesDataPowerPointBridge can parse PPT/PPTX bytes into SlidesData
 * and render them back to PPTX (application/vnd.ms-powerpoint).
 * It also supports merge logic from MergeableSymmetricBridge.
 */
object SlidesDataPowerPointBridge
  extends MergeableSymmetricBridge[SlidesData, ApplicationVndMsPowerpoint.type]:

  override protected def parser = SlidesDataPowerPointParser

  override protected def renderer = new SlidesDataPowerPointRenderer()