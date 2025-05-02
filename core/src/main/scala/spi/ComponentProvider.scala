package com.tjclp.xlcr
package spi

import bridges.Bridge
import splitters.DocumentSplitter
import types.MimeType

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
