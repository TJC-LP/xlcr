package com.tjclp.xlcr
package splitters

import spi.{SplitterInfo, SplitterProvider}
import types.{MimeType, Priority}
import utils.{Prioritized, Registry}

import scala.reflect.ClassTag

/** SplitterRegistry manages DocumentSplitter implementations, discovered via SPI.
  * It extends the generic Registry trait with strong typing.
  */
object SplitterRegistry
    extends Registry[
      MimeType, // K: Key is a MimeType
      DocumentSplitter[_ <: MimeType], // V: Value is a DocumentSplitter
      SplitterProvider, // P: SPI Provider type
      SplitterInfo[_ <: MimeType] // I: Info object type
    ] {

  // Basic registry implementation
  override protected def registryName: String = "SplitterRegistry"
  override protected def providerClass: Class[SplitterProvider] =
    classOf[SplitterProvider]
  override implicit protected def valueClassTag: ClassTag[_] = ClassTag(
    classOf[DocumentSplitter[_]]
  )
  override protected def extractProviderInfo(
      provider: SplitterProvider
  ): Iterable[SplitterInfo[_ <: MimeType]] = provider.getSplitters
  override protected def getKey(info: SplitterInfo[_ <: MimeType]): MimeType =
    info.mime
  override protected def getValue(
      info: SplitterInfo[_ <: MimeType]
  ): DocumentSplitter[_ <: MimeType] = info.splitter

  // MIME type subtype matching logic
  override protected def keySubtypeCheck
      : Option[(MimeType, MimeType) => Boolean] = Some {
    (requestedMime, registeredMime) =>
      // Consider a match if base types and subtypes match
      requestedMime.baseType == registeredMime.baseType &&
      requestedMime.subType == registeredMime.subType
  }

  /** Register a splitter with type safety. */
  def register[T <: MimeType](
      mime: T,
      splitter: DocumentSplitter[T]
  ): Unit = {
    super.register(mime, splitter)
  }

  /** Find a splitter for a MIME type with preserved type information. */
  def findSplitter[T <: MimeType](mime: T): Option[DocumentSplitter[T]] = {
    super.getWithSubtypes(mime).map(_.asInstanceOf[DocumentSplitter[T]])
  }

  /** Find all splitters for a MIME type with preserved type information. */
  def findAllSplitters[T <: MimeType](mime: T): List[DocumentSplitter[T]] = {
    super.getAllWithSubtypes(mime).map(_.asInstanceOf[DocumentSplitter[T]])
  }

  /** List registered splitters for diagnostics. */
  def listSplitters(): Seq[(MimeType, String, Priority)] = {
    super.listEntries() // Directly use the trait method
  }
}
