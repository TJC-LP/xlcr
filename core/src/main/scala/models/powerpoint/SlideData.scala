package com.tjclp.xlcr
package models.powerpoint

import types.Mergeable

final case class SlideData(
  title: Option[String] = None,
  index: Int,
  isHidden: Boolean = false,
  elements: List[SlideElement] = List.empty,
  notes: Option[String] = None,
  backgroundColor: Option[String] = None
) extends Mergeable[SlideData] {
  override def merge(other: SlideData): SlideData =
    SlideData(
      title = this.title.orElse(other.title),
      index = this.index,
      isHidden = this.isHidden && other.isHidden,
      elements = (this.elements ++ other.elements).distinct,
      notes = this.notes.orElse(other.notes),
      backgroundColor = this.backgroundColor.orElse(other.backgroundColor)
    )
}

object SlideData extends SlideDataCodecs
