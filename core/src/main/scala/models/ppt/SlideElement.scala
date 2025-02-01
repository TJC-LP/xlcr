package com.tjclp.xlcr
package models.ppt

import io.circe._
import io.circe.derivation.{Configuration, ConfiguredDecoder, ConfiguredEncoder}

/**
 * SlideElement represents an individual item on a slide,
 * such as text, image, or shape. It now includes an optional shapeType
 * to indicate the kind of shape (e.g. "rectangle", "oval", "line", etc.)
 * and an optional style providing detailed visual properties.
 */
final case class SlideElement(
  elementType: String,                  // e.g., "text", "image", "shape"
  shapeType: Option[String] = None,     // e.g., "rectangle", "oval", "line", etc.
  content: Option[String] = None,       // The textual content or image reference (if applicable)
  position: Option[Position] = None,    // Explicit position coordinates for the element
  style: Option[SlideElementStyle] = None
)

object SlideElement:
  given Configuration = Configuration.default.withDefaults
  implicit val encoder: Encoder[SlideElement] = ConfiguredEncoder.derived[SlideElement]
  implicit val decoder: Decoder[SlideElement] = ConfiguredDecoder.derived[SlideElement]