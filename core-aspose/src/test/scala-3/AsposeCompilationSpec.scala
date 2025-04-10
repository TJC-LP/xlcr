package com.tjclp.xlcr

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import compat.aspose.AsposeBridge

/**
 * Test compilation of the aspose compatibility layer for Scala 3.
 */
class AsposeCompilationSpec extends AnyFlatSpec with Matchers:
  "The aspose compatibility layer for Scala 3" should "define all required functionality" in {
    // This test just verifies compilation with our compatibility layer
    
    // Test Cells functionality
    val landscape = AsposeBridge.Cells.LANDSCAPE_ORIENTATION
    landscape.isInstanceOf[Int] should be(true)
    
    val paperA4 = AsposeBridge.Cells.PAPER_A4
    paperA4.isInstanceOf[Int] should be(true)
    
    // Test Words constants
    val mhtmlFormat = AsposeBridge.Words.MHTML_FORMAT
    mhtmlFormat.isInstanceOf[Int] should be(true)
    
    val pdfFormat = AsposeBridge.Words.PDF_FORMAT
    pdfFormat.isInstanceOf[Int] should be(true)
    
    // Test Slides constants
    val slidesPdfFormat = AsposeBridge.Slides.PDF_FORMAT
    slidesPdfFormat.isInstanceOf[Int] should be(true)
  }