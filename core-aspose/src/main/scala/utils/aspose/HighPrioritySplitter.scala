package com.tjclp.xlcr
package utils.aspose

import models.FileContent
import types.{MimeType, Priority}
import utils.{DocChunk, DocumentSplitter, SplitConfig}

/**
 * Marker trait for all Aspose document splitters.
 * This trait sets the priority to ASPOSE and should be mixed into all Aspose splitter implementations.
 * 
 * @tparam I The input MimeType that this splitter can handle
 */
trait HighPrioritySplitter[I <: MimeType] extends DocumentSplitter[I] {
  /**
   * All Aspose splitters have ASPOSE priority to ensure they're selected over core implementations
   * when Aspose licenses are available.
   */
  override def priority: Priority = Priority.ASPOSE
}