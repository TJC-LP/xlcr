package com.tjclp.xlcr
package models.ppt

import models.Model
import types.Mergeable

import io.circe.*
import io.circe.generic.semiauto.*

final case class SlidesData(slides: List[SlideData]) extends Model with Mergeable[SlidesData]:
  override def merge(other: SlidesData): SlidesData =
    val existingMap = this.slides.map(s => s.index -> s).toMap
    val mergedMap = other.slides.foldLeft(existingMap) { (acc, newSlide) =>
      acc.get(newSlide.index) match {
        case Some(existingSlide) => acc.updated(newSlide.index, existingSlide.merge(newSlide))
        case None => acc.updated(newSlide.index, newSlide)
      }
    }
    SlidesData(mergedMap.values.toList.sortBy(_.index))

private object SlidesData:
  implicit val encoder: Encoder[SlidesData] = deriveEncoder[SlidesData]
  implicit val decoder: Decoder[SlidesData] = deriveDecoder[SlidesData]