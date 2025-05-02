package com.tjclp.xlcr
package bridges

import bridges.tika.{TikaPlainTextBridge, TikaXmlBridge}
import types.MimeType
import spi.BridgeInfo
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BridgeRegistrySpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {
  
  // Store original registry to restore after tests
  private var originalRegistry: Map[(MimeType, MimeType), Bridge[_, _, _]] = Map.empty
  
  override def beforeAll(): Unit = {
    super.beforeAll()
    // Store current registrations
    originalRegistry = BridgeRegistry.listRegisteredBridges()
      .map { case (inMime, outMime, _, _) => 
        (inMime, outMime) -> BridgeRegistry.findBridge(inMime, outMime).get 
      }.toMap
  }
  
  "BridgeRegistry with wildcard support" should "register and find a wildcard bridge" in {
    // Register a test wildcard bridge
    val customMime = MimeType("application", "custom-format")
    
    // Register TikaBridge with wildcard input
    BridgeRegistry.register(MimeType.Wildcard, MimeType.TextPlain, TikaPlainTextBridge)
    
    // Check if we can find it for an unregistered mime type
    val result = BridgeRegistry.findBridge(customMime, MimeType.TextPlain)
    
    result.isDefined shouldBe true
    result.get should be(TikaPlainTextBridge)
  }
  
  it should "prefer exact matches over wildcard matches" in {
    // Register an exact match for PDF -> Text
    BridgeRegistry.register(MimeType.ApplicationPdf, MimeType.TextPlain, TikaPlainTextBridge)
    
    // Register a different bridge as wildcard
    BridgeRegistry.register(MimeType.Wildcard, MimeType.TextPlain, TikaXmlBridge)
    
    // Check that we get the exact match
    val result = BridgeRegistry.findBridge(MimeType.ApplicationPdf, MimeType.TextPlain)
    
    result.isDefined shouldBe true
    result.get should be(TikaPlainTextBridge) // Should find the exact match
  }
  
  it should "include wildcard bridges in findAllBridges" in {
    // Register both exact and wildcard bridges
    BridgeRegistry.register(MimeType.ApplicationPdf, MimeType.TextPlain, TikaPlainTextBridge)
    BridgeRegistry.register(MimeType.Wildcard, MimeType.TextPlain, TikaXmlBridge)
    
    // Find all bridges for PDF -> Text
    val bridges = BridgeRegistry.findAllBridges(MimeType.ApplicationPdf, MimeType.TextPlain)
    
    // Should contain both bridges
    bridges.map(_.getClass.getSimpleName) should contain allOf(
      TikaPlainTextBridge.getClass.getSimpleName,
      TikaXmlBridge.getClass.getSimpleName
    )
  }
  
  override def afterAll(): Unit = {
    // Clean up by re-registering original bridges
    BridgeRegistry.init() // Reset the registry
    
    originalRegistry.foreach { case ((inMime, outMime), bridge) =>
      BridgeRegistry.register(inMime, outMime, bridge)
    }
    
    super.afterAll()
  }
}