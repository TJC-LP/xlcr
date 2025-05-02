package com.tjclp.xlcr
package spi

import com.tjclp.xlcr.bridges.Bridge
import com.tjclp.xlcr.types.MimeType
import com.tjclp.xlcr.utils.DocumentSplitter

/** Information needed to register a Bridge.
  */
case class BridgeInfo(
    inMime: MimeType,
    outMime: MimeType,
    bridge: Bridge[_, _, _]
)

/** Information needed to register a DocumentSplitter.
  */
case class SplitterInfo(
    mime: MimeType,
    splitter: DocumentSplitter[_ <: MimeType]
)

/** Service Provider Interface for modules contributing Bridges.
  */
trait BridgeProvider {
  def getBridges: Iterable[BridgeInfo]
}

/** Service Provider Interface for modules contributing DocumentSplitters.
  */
trait SplitterProvider {
  def getSplitters: Iterable[SplitterInfo]
}
