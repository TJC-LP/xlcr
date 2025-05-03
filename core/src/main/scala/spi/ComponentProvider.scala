package com.tjclp.xlcr
package spi

import bridges.Bridge
import models.Model
import splitters.DocumentSplitter
import types.MimeType

/** Information needed to register a Bridge.
  * The type parameters ensure that the bridge's input and output MIME types
  * match the declared inMime and outMime fields, preserving type safety.
  *
  * @tparam I The input MimeType
  * @tparam O The output MimeType
  */
case class BridgeInfo[I <: MimeType, O <: MimeType](
    inMime: I,
    outMime: O,
    bridge: Bridge[_ <: Model, I, O]
)

/** Information needed to register a DocumentSplitter.
  */
case class SplitterInfo[T <: MimeType](
    mime: T,
    splitter: DocumentSplitter[T]
)

/** Service Provider Interface for modules contributing Bridges.
  */
trait BridgeProvider {
  def getBridges: Iterable[BridgeInfo[_ <: MimeType, _ <: MimeType]]
}

/** Service Provider Interface for modules contributing DocumentSplitters.
  */
trait SplitterProvider {
  def getSplitters: Iterable[SplitterInfo[_ <: MimeType]]
}