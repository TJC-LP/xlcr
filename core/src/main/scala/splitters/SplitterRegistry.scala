package com.tjclp.xlcr
package splitters

import spi.{SplitterInfo, SplitterProvider}
import types.{MimeType, Priority}
import utils.{Prioritized, Registry}

import scala.reflect.ClassTag

/** SplitterRegistry manages DocumentSplitter implementations, discovered via SPI.
  * It extends the generic Registry trait with strong typing.
  */
object SplitterRegistry extends Registry[
  MimeType, // K: Key is a MimeType
  DocumentSplitter[_ <: MimeType], // V: Value is a DocumentSplitter
  SplitterProvider, // P: SPI Provider type
  SplitterInfo[_ <: MimeType], // I: Info object type
  DocumentSplitter[_] // VC: The raw class type for ClassTag without type parameters
] {

  // Implement abstract members from Registry trait
  override protected def registryName: String = "SplitterRegistry"
  override protected def providerClass: Class[SplitterProvider] = classOf[SplitterProvider]
  // Provide ClassTag for DocumentSplitter type V
  override implicit protected def valueClassTag: ClassTag[DocumentSplitter[_]] =
    ClassTag(classOf[DocumentSplitter[_]])

  override protected def extractProviderInfo(provider: SplitterProvider): Iterable[SplitterInfo[_ <: MimeType]] =
    provider.getSplitters

  override protected def getKey(info: SplitterInfo[_ <: MimeType]): MimeType =
    info.mime

  override protected def getValue(info: SplitterInfo[_ <: MimeType]): DocumentSplitter[_ <: MimeType] =
    info.splitter

  // Override keySubtypeCheck to provide MIME type subtype check
  override protected def keySubtypeCheck: Option[(MimeType, MimeType) => Boolean] = Some {
    (requestedMime, registeredMime) =>
      // Consider a match if base types and subtypes match
      requestedMime.baseType == registeredMime.baseType &&
      requestedMime.subType == registeredMime.subType
  }

  /** Explicitly register a splitter dynamically with strong typing.
    * Useful for runtime configuration or testing.
    * 
    * @tparam T The mime type that the splitter handles
    * @param mime The mime type to register the splitter for
    * @param splitter The splitter implementation
    */
  def register[T <: MimeType](
      mime: T,
      splitter: DocumentSplitter[T]
  ): Unit = {
    addEntry(mime, splitter)
  }

  /** Find the highest priority splitter for a MIME type with precise return type.
    * 
    * @tparam T The mime type to find a splitter for
    * @param mime The mime type instance
    * @return An optional splitter that can handle the given mime type
    */
  def findSplitter[T <: MimeType](mime: T): Option[DocumentSplitter[T]] = {
    getWithSubtypes(mime).map(_.asInstanceOf[DocumentSplitter[T]])
  }

  /** Find all splitters for a MIME type with precise return types.
    * 
    * @tparam T The mime type to find splitters for
    * @param mime The mime type instance
    * @return A list of splitters that can handle the given mime type
    */
  def findAllSplitters[T <: MimeType](mime: T): List[DocumentSplitter[T]] = {
    getAllWithSubtypes(mime).map(_.asInstanceOf[DocumentSplitter[T]])
  }

  /** Diagnostic method to list registered splitters with their priorities. */
  def listRegisteredSplitters(): Seq[(MimeType, String, Priority)] = {
    listRegisteredEntries() // Directly use the trait method
  }
  
  /** Legacy method for backward compatibility with code that doesn't use type parameters */
  def findSplitterLegacy(mime: MimeType): Option[DocumentSplitter[_ <: MimeType]] = {
    findSplitter[MimeType](mime).map(_.asInstanceOf[DocumentSplitter[_ <: MimeType]])
  }
  
  /** Legacy method for backward compatibility with code that doesn't use type parameters */
  def findAllSplittersLegacy(mime: MimeType): List[DocumentSplitter[_ <: MimeType]] = {
    findAllSplitters[MimeType](mime).map(_.asInstanceOf[DocumentSplitter[_ <: MimeType]])
  }
}