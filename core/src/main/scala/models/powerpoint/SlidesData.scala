package com.tjclp.xlcr
package models.powerpoint

import models.Model
import types.Mergeable

final case class SlidesData(slides: List[SlideData]) extends Model with Mergeable[SlidesData] {
  override def merge(other: SlidesData): SlidesData = {
    val existingMap = this.slides.map(s => s.index -> s).toMap
    val mergedMap = other.slides.foldLeft(existingMap) { (acc, newSlide) =>
      acc.get(newSlide.index) match {
        case Some(existingSlide) => acc.updated(newSlide.index, existingSlide.merge(newSlide))
        case None => acc.updated(newSlide.index, newSlide)
      }
    }
    SlidesData(mergedMap.values.toList.sortBy(_.index))
  }
}

object SlidesData extends SlidesDataCodecs
