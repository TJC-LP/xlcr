package com.tjclp.xlcr
package bridges

import types.MimeType
import models.FileContent

trait SimpleBridge[I <: MimeType, O <: MimeType] extends Bridge[FileContent[I], I, O] {
  type M = FileContent[I]
}
  
