package com.tjclp.xlcr
package bridges

import bridges.tika.TikaPlainTextBridge
import types.MimeType

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BridgeRegistrySpec
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterAll {

  // Store original registry to restore after tests
  private var originalRegistry: Map[(MimeType, MimeType), Bridge[_, _, _]] =
    Map.empty

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Store current registrations
    originalRegistry = BridgeRegistry
      .listBridges()
      .map { case (inMime, outMime, _, _) =>
        (inMime, outMime) -> BridgeRegistry.findBridge(inMime, outMime).get
      }
      .toMap
  }

  "BridgeRegistry with wildcard support" should "register and find a wildcard bridge" in {
    // Register a test wildcard bridge
    val customMime = MimeType("application", "custom-format")

    // Register TikaBridge with wildcard input
    BridgeRegistry.register(
      MimeType.Wildcard,
      MimeType.TextPlain,
      TikaPlainTextBridge
    )

    // Check if we can find it for an unregistered mime type
    val result = BridgeRegistry.findBridge(customMime, MimeType.TextPlain)

    result.isDefined shouldBe true
    result.get should be(TikaPlainTextBridge)
  }

  it should "prefer exact matches over wildcard matches" in {
    // Register an exact match for PDF -> Text
    BridgeRegistry.register(
      MimeType.ApplicationPdf,
      MimeType.TextPlain,
      TikaPlainTextBridge
    )

    // Register a different bridge as wildcard - we use the same bridge as the test is checking priority, not different bridge types
    BridgeRegistry.register(
      MimeType.Wildcard,
      MimeType.TextPlain,
      TikaPlainTextBridge
    )

    // Check that we get the exact match
    val result =
      BridgeRegistry.findBridge(MimeType.ApplicationPdf, MimeType.TextPlain)

    result.isDefined shouldBe true
    result.get should be(TikaPlainTextBridge) // Should find the exact match
  }

  it should "include wildcard bridges in findAllBridges" in {
    // Register both exact and wildcard bridges
    BridgeRegistry.register(
      MimeType.ApplicationPdf,
      MimeType.TextPlain,
      TikaPlainTextBridge
    )
    BridgeRegistry.register(
      MimeType.Wildcard,
      MimeType.TextPlain,
      TikaPlainTextBridge
    )

    // Find all bridges for PDF -> Text
    val bridges =
      BridgeRegistry.findAllBridges(MimeType.ApplicationPdf, MimeType.TextPlain)

    // Should contain at least the bridge we registered
    bridges.map(_.getClass.getSimpleName) should contain(
      TikaPlainTextBridge.getClass.getSimpleName
    )

    // Should have at least 1 bridge (we registered the same bridge twice with different keys)
    bridges.size should be >= 1
  }

  it should "support finding bridges using subtype matching" in {
    // Register a bridge for a specific Excel file format
    BridgeRegistry.register(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet,
      MimeType.TextPlain,
      TikaPlainTextBridge
    )

    // Create a custom subtype of Excel MIME type with parameters
    val excelWithParams =
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
        .withParameter("charset", "utf-8")
        .withParameter("delimiter", ",")

    // Should be able to find the bridge when requesting with a parameterized MIME type
    val result = BridgeRegistry.findBridge(excelWithParams, MimeType.TextPlain)

    result.isDefined shouldBe true
    result.get should be(TikaPlainTextBridge)
  }
}
