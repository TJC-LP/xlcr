package com.tjclp.xlcr

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import compat.aspose._

/**
 * Test compilation of the aspose compatibility layer for Scala 2.12.
 */
class AsposeCompilationSpec extends AnyFlatSpec with Matchers {
  "The aspose compatibility layer for Scala 2.12" should "define all required types" in {
    // This test just verifies compilation with our compatibility layer

    // Test Workbook type
    val workbookType = classOf[AsposeWorkbook]
    workbookType.getName should include("aspose.cells.Workbook")

    // Test PDF save options
    val pdfSaveOptionsType = classOf[AsposePdfSaveOptions]
    pdfSaveOptionsType.getName should include("aspose.cells.PdfSaveOptions")

    // Test PageOrientation constant
    val landscape = AsposePageOrientationType.LANDSCAPE
    // The constant is an int in Scala 2.12, so we just check its type
    landscape.isInstanceOf[Int] should be(true)

    // Test Document type
    val documentType = classOf[AsposeDocument]
    documentType.getName should include("aspose.words.Document")

    // Test Presentation type
    val presentationType = classOf[AsposePresentation]
    presentationType.getName should include("aspose.slides.Presentation")
  }
}
