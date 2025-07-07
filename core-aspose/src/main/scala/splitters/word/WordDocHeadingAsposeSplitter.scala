package com.tjclp.xlcr
package splitters.word

object WordDocHeadingAsposeSplitter extends WordHeadingAsposeSplitter {
  override def priority: types.Priority = types.Priority.HIGH
}
