package com.tjclp.xlcr
package bridges.aspose

import bridges.Bridge
import bridges.aspose.email.EmailToPdfAsposeBridge
import bridges.aspose.word.WordToPdfAsposeBridge
import types.MimeType
import types.MimeType.{ApplicationMsWord, ApplicationPdf, MessageRfc822}

/**
 * BridgeRegistryUpdateExample is an illustration of how you might register
 * the new Aspose-based bridges. If your project already has a BridgeRegistry,
 * you can adapt the lines below as needed.
 */
object BridgeRegistryUpdateExample {

  // Hypothetical registry
  private var registryMap: Map[(MimeType, MimeType), Bridge[_, _, _]] = Map.empty

  // Initialize or register your bridges
  def init(): Unit = {
    register((ApplicationMsWord, ApplicationPdf), WordToPdfAsposeBridge)
    register((MessageRfc822, ApplicationPdf), EmailToPdfAsposeBridge)
  }

  private def register(key: (MimeType, MimeType), br: Bridge[_,_,_]): Unit = {
    registryMap = registryMap + (key -> br)
  }

  /**
   * Illustrative usage in a pipeline to find the correct Bridge:
   */
  def findBridge(in: MimeType, out: MimeType): Option[Bridge[_, _, _]] = {
    registryMap.get((in, out))
  }

}