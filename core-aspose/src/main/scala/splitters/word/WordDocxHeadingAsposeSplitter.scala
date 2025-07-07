package com.tjclp.xlcr
package splitters.word

object WordDocxHeadingAsposeSplitter extends WordHeadingAsposeSplitter {
  override def priority: types.Priority = types.Priority.HIGH
}