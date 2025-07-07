package com.tjclp.xlcr
package splitters.powerpoint

import java.io.ByteArrayInputStream

import com.aspose.slides._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import splitters.{ SplitConfig, SplitStrategy }
import types.MimeType

class PowerPointSlideAsposeSplitterSpec extends AnyFreeSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Ensure Aspose license is loaded
    com.tjclp.xlcr.utils.aspose.AsposeLicense.initializeIfNeeded()
  }

  /**
   * Creates a test presentation with custom dimensions
   */
  private def createTestPresentation(
    slideCount: Int = 3,
    width: Float = 1024f,
    height: Float = 768f
  ): Array[Byte] = {
    val pres = new Presentation()
    try {
      // Set custom slide size
      pres.getSlideSize.setSize(width, height, SlideSizeScaleType.DoNotScale)

      // Remove default slide
      pres.getSlides.removeAt(0)

      // Add slides with content
      for (i <- 1 to slideCount) {
        val slide = pres.getSlides.addEmptySlide(pres.getLayoutSlides.get_Item(0))

        // Add a text box with slide number
        val shape = slide.getShapes.addAutoShape(ShapeType.Rectangle, 100, 100, 200, 50)
        shape.getTextFrame.setText(s"Slide $i")

        // Set slide name
        slide.setName(s"Test Slide $i")
      }

      // Convert to bytes
      val baos = new java.io.ByteArrayOutputStream()
      pres.save(baos, SaveFormat.Pptx)
      baos.toByteArray
    } finally
      pres.dispose()
  }

  "PowerPointPptxSlideAsposeSplitter" - {

    "should split a presentation into individual slides" in {
      val presentationData = createTestPresentation(slideCount = 3)
      val content = FileContent(
        presentationData,
        MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation
      )

      val config = SplitConfig(strategy = Some(SplitStrategy.Slide))
      val chunks = PowerPointPptxSlideAsposeSplitter.split(content, config)

      chunks.size shouldBe 3
      chunks.zipWithIndex.foreach { case (chunk, idx) =>
        chunk.label shouldBe s"Test Slide ${idx + 1}"
        chunk.index shouldBe idx
        chunk.total shouldBe 3
      }
    }

    "should preserve original slide dimensions when splitting" in {
      // Create a presentation with custom dimensions
      val customWidth  = 1920f
      val customHeight = 1080f
      val presentationData = createTestPresentation(
        slideCount = 2,
        width = customWidth,
        height = customHeight
      )

      val content = FileContent(
        presentationData,
        MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation
      )

      val config = SplitConfig(strategy = Some(SplitStrategy.Slide))
      val chunks = PowerPointPptxSlideAsposeSplitter.split(content, config)

      chunks.size shouldBe 2

      // Verify each chunk maintains the original dimensions
      chunks.foreach { chunk =>
        val splitPres = new Presentation(new ByteArrayInputStream(chunk.content.data))
        try {
          val slideSize = splitPres.getSlideSize
          val width     = slideSize.getSize.getWidth
          val height    = slideSize.getSize.getHeight

          width shouldBe customWidth
          height shouldBe customHeight

          // Verify the slide exists and has content
          splitPres.getSlides.size() shouldBe 1
        } finally
          splitPres.dispose()
      }
    }

    "should handle different aspect ratios correctly" in {
      // Test with 16:9 aspect ratio
      val widescreen16x9 = createTestPresentation(
        slideCount = 1,
        width = 1280f,
        height = 720f
      )

      val content16x9 = FileContent(
        widescreen16x9,
        MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation
      )

      val chunks16x9 = PowerPointPptxSlideAsposeSplitter.split(
        content16x9,
        SplitConfig(strategy = Some(SplitStrategy.Slide))
      )

      val splitPres16x9 = new Presentation(new ByteArrayInputStream(chunks16x9.head.content.data))
      try {
        val size = splitPres16x9.getSlideSize.getSize
        (size.getWidth / size.getHeight) shouldBe (16.0 / 9.0) +- 0.0001
      } finally
        splitPres16x9.dispose()

      // Test with 4:3 aspect ratio
      val standard4x3 = createTestPresentation(
        slideCount = 1,
        width = 1024f,
        height = 768f
      )

      val content4x3 = FileContent(
        standard4x3,
        MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation
      )

      val chunks4x3 = PowerPointPptxSlideAsposeSplitter.split(
        content4x3,
        SplitConfig(strategy = Some(SplitStrategy.Slide))
      )

      val splitPres4x3 = new Presentation(new ByteArrayInputStream(chunks4x3.head.content.data))
      try {
        val size = splitPres4x3.getSlideSize.getSize
        (size.getWidth / size.getHeight) shouldBe (4.0 / 3.0) +- 0.0001
      } finally
        splitPres4x3.dispose()
    }

    "should handle presentations with notes when preserveSlideNotes is enabled" in {
      val pres = new Presentation()
      try {
        // Add a slide with notes
        val slide = pres.getSlides.get_Item(0)
        slide.setName("Slide with Notes")

        // Add notes to the slide
        val notesSlide = slide.getNotesSlideManager.addNotesSlide()
        notesSlide.getNotesTextFrame.setText("These are important notes for this slide.")

        // Convert to bytes
        val baos = new java.io.ByteArrayOutputStream()
        pres.save(baos, SaveFormat.Pptx)
        val presentationData = baos.toByteArray

        val content = FileContent(
          presentationData,
          MimeType.ApplicationVndOpenXmlFormatsPresentationmlPresentation
        )

        val config = SplitConfig(
          strategy = Some(SplitStrategy.Slide),
          preserveSlideNotes = true
        )

        val chunks = PowerPointPptxSlideAsposeSplitter.split(content, config)

        chunks.size shouldBe 1

        // Verify notes are preserved
        val splitPres = new Presentation(new ByteArrayInputStream(chunks.head.content.data))
        try {
          val splitSlide   = splitPres.getSlides.get_Item(0)
          val notesManager = splitSlide.getNotesSlideManager
          notesManager.getNotesSlide should not be null

          val notesText = notesManager.getNotesSlide.getNotesTextFrame.getText
          notesText should include("These are important notes")
        } finally
          splitPres.dispose()
      } finally
        pres.dispose()
    }
  }

  "PowerPointPptSlideAsposeSplitter" - {

    "should split PPT files while preserving dimensions" in {
      // Create a test PPT presentation
      val pres = new Presentation()
      try {
        // Set custom dimensions
        pres.getSlideSize.setSize(800f, 600f, SlideSizeScaleType.DoNotScale)

        // Add content
        val slide = pres.getSlides.get_Item(0)
        slide.setName("PPT Test Slide")

        // Save as PPT format
        val baos = new java.io.ByteArrayOutputStream()
        pres.save(baos, SaveFormat.Ppt)
        val pptData = baos.toByteArray

        val content = FileContent(pptData, MimeType.ApplicationVndMsPowerpoint)
        val config  = SplitConfig(strategy = Some(SplitStrategy.Slide))

        val chunks = PowerPointPptSlideAsposeSplitter.split(content, config)

        chunks.size shouldBe 1

        // Verify dimensions are preserved
        val splitPres = new Presentation(new ByteArrayInputStream(chunks.head.content.data))
        try {
          splitPres.getSlideSize.getSize.getWidth shouldBe 800f
          splitPres.getSlideSize.getSize.getHeight shouldBe 600f
        } finally
          splitPres.dispose()
      } finally
        pres.dispose()
    }
  }
}
