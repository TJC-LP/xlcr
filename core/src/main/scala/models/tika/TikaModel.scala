package com.tjclp.xlcr
package models.tika

import models.Model
import types.MimeType

/**
 * A generic model for Tika-related activities, storing text, optional XML, and metadata.
 * @param text the plain text extracted by Tika
 * @param metadata key-value pairs from Tika
 */
case class TikaModel[O <: MimeType](
    text: String,
    metadata: Map[String, String]
) extends Model
