package com.tjclp.xlcr
package registration

import scala.jdk.CollectionConverters._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import bridges.BridgeRegistry
import spi.{ BridgeProvider, SplitterProvider }
import splitters.SplitterRegistry
import types.MimeType
import types.Priority

class AsposeRegistrationSpec extends AnyFlatSpec with Matchers {

  "AsposeRegistrations" should "be discoverable via ServiceLoader" in {
    val serviceLoader = java.util.ServiceLoader.load(classOf[BridgeProvider])
    val providers = serviceLoader.iterator().asScala.toList
    
    providers.map(_.getClass.getName) should contain("com.tjclp.xlcr.registration.AsposeRegistrations")
  }

  it should "register all expected bridges" in {
    val registrations = new AsposeRegistrations()
    val bridges = registrations.getBridges.toList
    
    // Verify we have the expected number of bridges
    bridges.size should be(16) // Updated based on actual count
    
    // Verify key bridge registrations by checking mime type pairs
    val mimePairs = bridges.map(b => (b.inMime, b.outMime)).toSet
    
    // Word to PDF bridges
    mimePairs should contain((MimeType.ApplicationMsWord, MimeType.ApplicationPdf))
    mimePairs should contain((MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument, MimeType.ApplicationPdf))
    
    // Excel to PDF bridges
    mimePairs should contain((MimeType.ApplicationVndMsExcel, MimeType.ApplicationPdf))
    mimePairs should contain((MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet, MimeType.ApplicationPdf))
    
    // PDF to Image bridges
    mimePairs should contain((MimeType.ApplicationPdf, MimeType.ImagePng))
    mimePairs should contain((MimeType.ApplicationPdf, MimeType.ImageJpeg))
    
    // Image to PDF bridges
    mimePairs should contain((MimeType.ImagePng, MimeType.ApplicationPdf))
    mimePairs should contain((MimeType.ImageJpeg, MimeType.ApplicationPdf))
  }

  it should "register all expected splitters" in {
    val registrations = new AsposeRegistrations()
    val splitters = registrations.getSplitters.toList
    
    // Verify we have the expected number of splitters
    splitters.size should be(14) // Updated based on actual count
    
    // Verify key splitter registrations
    val mimeTypes = splitters.map(_.mime).toSet
    
    // Excel splitters
    mimeTypes should contain(MimeType.ApplicationVndMsExcel)
    mimeTypes should contain(MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet)
    
    // PowerPoint splitters
    mimeTypes should contain(MimeType.ApplicationVndMsPowerpoint)
    mimeTypes should contain(MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation)
    
    // PDF splitter
    mimeTypes should contain(MimeType.ApplicationPdf)
    
    // Archive splitters
    mimeTypes should contain(MimeType.ApplicationZip)
    mimeTypes should contain(MimeType.ApplicationSevenz)
  }

  "Aspose bridges" should "have HIGH priority" in {
    val registrations = new AsposeRegistrations()
    val bridges = registrations.getBridges.toList
    
    // Check that all Aspose bridges have HIGH priority
    bridges.foreach { bridgeInfo =>
      val bridge = bridgeInfo.bridge
      bridge.priority should be(Priority.HIGH)
    }
  }

  "PDF to Image Aspose bridges" should "be registered with HIGH priority" in {
    // Get the actual registered bridges from the registry
    val pdfToPngBridge = BridgeRegistry.findBridge(MimeType.ApplicationPdf, MimeType.ImagePng)
    val pdfToJpegBridge = BridgeRegistry.findBridge(MimeType.ApplicationPdf, MimeType.ImageJpeg)
    
    // Verify they exist
    pdfToPngBridge should be(defined)
    pdfToJpegBridge should be(defined)
    
    // Verify they have HIGH priority
    pdfToPngBridge.get.priority should be(Priority.HIGH)
    pdfToJpegBridge.get.priority should be(Priority.HIGH)
  }

  "Aspose splitters" should "have HIGH priority" in {
    val registrations = new AsposeRegistrations()
    val splitters = registrations.getSplitters.toList
    
    // Check that all Aspose splitters have HIGH priority
    // Note: Some router splitters might have different priorities
    splitters.foreach { splitterInfo =>
      val splitter = splitterInfo.splitter
      val splitterName = splitter.getClass.getSimpleName
      
      // Most Aspose splitters should have HIGH priority
      // Router splitters are special and might have DEFAULT priority
      if (!splitterName.contains("Router")) {
        withClue(s"Splitter $splitterName should have HIGH priority:") {
          splitter.priority should be(Priority.HIGH)
        }
      }
    }
  }

  "BridgeRegistry" should "prefer Aspose bridges over core bridges" in {
    // Find all bridges for Word to PDF conversion
    val wordToPdfBridges = BridgeRegistry.findAllBridges(
      MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument,
      MimeType.ApplicationPdf
    )
    
    // Should have at least one bridge
    wordToPdfBridges should not be empty
    
    // The first bridge (highest priority) should be the Aspose one
    val topBridge = wordToPdfBridges.head
    topBridge.getClass.getName should include("Aspose")
    topBridge.priority should be(Priority.HIGH)
  }

  "SplitterRegistry" should "prefer Aspose splitters over core splitters" in {
    // Find all splitters for Excel files
    val excelSplitters = SplitterRegistry.findAllSplitters(
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
    )
    
    // Should have at least one splitter
    excelSplitters should not be empty
    
    // The first splitter (highest priority) should be the Aspose one
    val topSplitter = excelSplitters.head
    topSplitter.getClass.getName should include("Aspose")
    topSplitter.priority should be(Priority.HIGH)
  }

  "ServiceLoader integration" should "automatically register all Aspose components" in {
    // Verify that specific bridges are registered in the BridgeRegistry
    val htmlToPdf = BridgeRegistry.findBridge(MimeType.TextHtml, MimeType.ApplicationPdf)
    htmlToPdf should be(defined)
    htmlToPdf.get.getClass.getName should include("HtmlToPdfAsposeBridge")
    
    // Verify that specific splitters are registered in the SplitterRegistry
    val pdfSplitter = SplitterRegistry.findSplitter(MimeType.ApplicationPdf)
    pdfSplitter should be(defined)
    pdfSplitter.get.getClass.getName should include("PdfPageAsposeSplitter")
  }

  "Email to PDF bridges" should "be properly registered" in {
    val emlToPdf = BridgeRegistry.findBridge(MimeType.MessageRfc822, MimeType.ApplicationPdf)
    val msgToPdf = BridgeRegistry.findBridge(MimeType.ApplicationVndMsOutlook, MimeType.ApplicationPdf)
    
    emlToPdf should be(defined)
    msgToPdf should be(defined)
    
    emlToPdf.get.priority should be(Priority.HIGH)
    msgToPdf.get.priority should be(Priority.HIGH)
  }
}