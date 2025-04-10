package com.tjclp.xlcr
package bridges.powerpoint

import bridges.MergeableSymmetricBridge
import models.powerpoint.SlidesData
import parsers.powerpoint.SlidesDataPowerPointParser
import renderers.powerpoint.SlidesDataPowerPointRenderer
import types.MimeType
import types.MimeType.ApplicationVndMsPowerpoint

import scala.reflect.ClassTag

/**
 * SlidesDataPowerPointBridge can parse PPT/PPTX bytes into SlidesData
 * and render them back to PPTX (application/vnd.ms-powerpoint).
 * It also supports merge logic from MergeableSymmetricBridge.
 */
object SlidesDataPowerPointBridge
  extends MergeableSymmetricBridge[SlidesData, ApplicationVndMsPowerpoint.type] {
  
  implicit val mTag: ClassTag[SlidesData] = implicitly[ClassTag[SlidesData]]
  implicit val tTag: ClassTag[ApplicationVndMsPowerpoint.type] = implicitly[ClassTag[ApplicationVndMsPowerpoint.type]]
  implicit val iTag: ClassTag[ApplicationVndMsPowerpoint.type] = implicitly[ClassTag[ApplicationVndMsPowerpoint.type]]
  implicit val oTag: ClassTag[ApplicationVndMsPowerpoint.type] = implicitly[ClassTag[ApplicationVndMsPowerpoint.type]]

  override protected def parser: SlidesDataPowerPointParser.type = SlidesDataPowerPointParser

  override protected def renderer = new SlidesDataPowerPointRenderer()
}