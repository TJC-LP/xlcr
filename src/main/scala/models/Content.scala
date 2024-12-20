package com.tjclp.xlcr
package models

/**
 * A container for extracted or generated file content, allowing for
 * arbitrary byte data, plus contentType and associated metadata.
 *
 * @param data        The raw byte content.
 * @param contentType The MIME type (e.g., "text/plain", "application/json").
 * @param metadata    Additional metadata as key-value pairs.
 */
case class Content(
                    data: Array[Byte],
                    contentType: String,
                    metadata: Map[String, String]
                  )