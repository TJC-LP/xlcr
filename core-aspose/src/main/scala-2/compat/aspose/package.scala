package com.tjclp.xlcr
package compat

/**
 * Compatibility layer for Aspose classes to handle the Scala 2.12 specific issues
 * with Aspose class imports.
 */
package object aspose {
  // Cells
  type AsposeWorkbook = com.aspose.cells.Workbook
  type AsposePdfSaveOptions = com.aspose.cells.PdfSaveOptions  
  
  // For constants - use direct paths
  object AsposePageOrientationType {
    val LANDSCAPE = com.aspose.cells.PageOrientationType.LANDSCAPE
  }
  
  object AsposePaperSizeType {
    val PAPER_A_4 = com.aspose.cells.PaperSizeType.PAPER_A_4
  }
  
  // Slides
  type AsposePresentation = com.aspose.slides.Presentation
  
  object AsposeSlidesFormat {
    val Pdf = com.aspose.slides.SaveFormat.Pdf
  }
  
  // Words
  type AsposeDocument = com.aspose.words.Document
  type AsposeLoadOptions = com.aspose.words.LoadOptions
  
  object AsposeLoadFormat {
    val MHTML = com.aspose.words.LoadFormat.MHTML
  }
  
  object AsposeWordsFormat {
    val PDF = com.aspose.words.SaveFormat.PDF
  }
  
  // Email
  object AsposeMailMessage {
    def load(stream: java.io.InputStream): com.aspose.email.MailMessage = {
      com.aspose.email.MailMessage.load(stream)
    }
  }
  
  type AsposeMhtSaveOptions = com.aspose.email.MhtSaveOptions
}