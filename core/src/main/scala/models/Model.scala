package com.tjclp.xlcr
package models

/**
 * A base trait for a 'Model' abstraction that can be constructed from
 * certain mimeTypes and serialized back into another mimeType.
 * This allows custom logic for each mimeType, or
 * you can unify your bridging logic here.
 */
trait Model {
  def children: Option[List[Model]] = None
}
