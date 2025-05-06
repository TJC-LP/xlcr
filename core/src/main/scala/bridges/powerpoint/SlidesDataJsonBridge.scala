package com.tjclp.xlcr
package bridges.powerpoint

import bridges.MergeableSymmetricBridge
import models.powerpoint.SlidesData
import parsers.powerpoint.SlidesDataJsonParser
import renderers.powerpoint.SlidesDataJsonRenderer
import types.MimeType.ApplicationJson

/**
 * SlidesDataJsonBridge can parse JSON -> SlidesData and render SlidesData -> JSON. It also supports
 * merging thanks to MergeableSymmetricBridge.
 */
object SlidesDataJsonBridge
    extends MergeableSymmetricBridge[SlidesData, ApplicationJson.type] {

  override protected def parser = new SlidesDataJsonParser()

  override protected def renderer = new SlidesDataJsonRenderer()
}
