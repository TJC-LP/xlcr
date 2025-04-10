package com.tjclp.xlcr
package bridges.powerpoint

import bridges.MergeableSymmetricBridge
import models.powerpoint.SlidesData
import parsers.powerpoint.SlidesDataJsonParser
import renderers.powerpoint.SlidesDataJsonRenderer
import types.MimeType
import types.MimeType.ApplicationJson

import scala.reflect.ClassTag

/**
 * SlidesDataJsonBridge can parse JSON -> SlidesData and render SlidesData -> JSON.
 * It also supports merging thanks to MergeableSymmetricBridge.
 */
object SlidesDataJsonBridge
  extends MergeableSymmetricBridge[SlidesData, ApplicationJson.type] {
  
  implicit val mTag: ClassTag[SlidesData] = implicitly[ClassTag[SlidesData]]
  implicit val tTag: ClassTag[ApplicationJson.type] = implicitly[ClassTag[ApplicationJson.type]]
  implicit val iTag: ClassTag[ApplicationJson.type] = implicitly[ClassTag[ApplicationJson.type]]
  implicit val oTag: ClassTag[ApplicationJson.type] = implicitly[ClassTag[ApplicationJson.type]]

  override protected def parser = new SlidesDataJsonParser()

  override protected def renderer = new SlidesDataJsonRenderer()
}