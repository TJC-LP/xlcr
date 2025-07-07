package com.tjclp.xlcr
package splitters.word

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import com.aspose.words._
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._
import scala.util.Using

import models.FileContent
import splitters._
import types.MimeType

/**
 * Splits Word documents by Heading 1 style using Aspose.Words.
 * This provides more accurate heading detection than the POI-based implementation.
 */
trait WordHeadingAsposeSplitter extends DocumentSplitter[MimeType] with SplitFailureHandler {
  override protected val logger = LoggerFactory.getLogger(getClass)
  
  override def split(
    content: FileContent[MimeType],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    
    // Check strategy compatibility
    if (!cfg.hasStrategy(SplitStrategy.Heading)) {
      return handleInvalidStrategy(
        content,
        cfg,
        cfg.strategy.map(_.displayName).getOrElse("none"),
        Seq("heading")
      )
    }
    
    // Wrap main splitting logic with failure handling
    withFailureHandling(content, cfg) {
      // Validate content is not empty
      if (content.data.isEmpty) {
        throw new EmptyDocumentException(
          content.mimeType.mimeType,
          "Word file is empty"
        )
      }
      
      Using(new ByteArrayInputStream(content.data)) { bis =>
        val document = new Document(bis)
        try {
          val chunks = splitDocumentByHeadings(document, cfg, content.mimeType)
          chunks
        } finally {
          document.cleanup()
        }
      }.get
    }
  }
  
  private def splitDocumentByHeadings(doc: Document, cfg: SplitConfig, mimeType: MimeType): Seq[DocChunk[_ <: MimeType]] = {
    // Find all heading 1 paragraphs in document order
    val allHeadings = scala.collection.mutable.ArrayBuffer[(Paragraph, Int)]()
    var headingCount = 0
    var hasContentBeforeFirstHeading = false
    
    // Traverse all sections to find headings and check for content before first heading
    for (sectionIdx <- 0 until doc.getSections.getCount) {
      val section = doc.getSections.get(sectionIdx).asInstanceOf[Section]
      val body = section.getBody
      
      for (nodeIdx <- 0 until body.getChildNodes(NodeType.PARAGRAPH, false).getCount) {
        val para = body.getChildNodes(NodeType.PARAGRAPH, false).get(nodeIdx).asInstanceOf[Paragraph]
        if (para.getParagraphFormat != null && 
            para.getParagraphFormat.getStyleName != null &&
            para.getParagraphFormat.getStyleName.startsWith("Heading 1")) {
          allHeadings += ((para, headingCount))
          headingCount += 1
        } else if (allHeadings.isEmpty && para.getText.trim.nonEmpty) {
          hasContentBeforeFirstHeading = true
        }
      }
    }
    
    if (allHeadings.isEmpty) {
      throw new EmptyDocumentException(
        mimeType.mimeType,
        "No Heading 1 paragraphs found in document"
      )
    }
    
    logger.info(s"Found ${allHeadings.length} Heading 1 paragraphs")
    if (hasContentBeforeFirstHeading) {
      logger.info("Document has content before first heading - will create preamble section")
    }
    
    // Log heading texts for debugging
    allHeadings.foreach { case (para, idx) =>
      val text = para.getText.trim
      logger.debug(s"Heading $idx: '${if (text.isEmpty) "[EMPTY]" else text}'")
    }
    
    // Create sections including preamble if needed
    val sections = scala.collection.mutable.ArrayBuffer[DocChunk[_ <: MimeType]]()
    var chunkIndex = 0
    
    // Add preamble section if there's content before first heading
    if (hasContentBeforeFirstHeading) {
      val preambleDoc = doc.deepClone().asInstanceOf[Document]
      
      // Remove everything from first heading onwards
      var currentHeadingCount = 0
      val nodesToRemove = scala.collection.mutable.ArrayBuffer[Node]()
      var foundFirstHeading = false
      
      for (sectionIdx <- 0 until preambleDoc.getSections.getCount) {
        val section = preambleDoc.getSections.get(sectionIdx).asInstanceOf[Section]
        val body = section.getBody
        
        for (nodeIdx <- 0 until body.getChildNodes(NodeType.ANY, false).getCount) {
          val node = body.getChildNodes(NodeType.ANY, false).get(nodeIdx)
          
          node match {
            case para: Paragraph if para.getParagraphFormat != null && 
                                    para.getParagraphFormat.getStyleName != null &&
                                    para.getParagraphFormat.getStyleName.startsWith("Heading 1") =>
              foundFirstHeading = true
              nodesToRemove += node
              
            case _ =>
              if (foundFirstHeading) {
                nodesToRemove += node
              }
          }
        }
      }
      
      nodesToRemove.foreach(_.remove())
      
      // Save preamble if it has content
      val baos = new ByteArrayOutputStream()
      val saveOptions = createSaveOptions(mimeType)
      preambleDoc.save(baos, saveOptions)
      preambleDoc.cleanup()
      
      sections += DocChunk(
        FileContent(baos.toByteArray, mimeType),
        label = "Preamble",
        index = chunkIndex,
        total = allHeadings.length + 1
      )
      chunkIndex += 1
    }
    
    // Process each heading section
    allHeadings.zipWithIndex.foreach { case ((_, headingIdx), idx) =>
      val headingText = allHeadings(idx)._1.getText.trim
      
      // Skip empty headings
      if (headingText.isEmpty) {
        logger.debug(s"Skipping empty heading at index $idx")
      } else {
        // Clone the entire document for each section
        val sectionDoc = doc.deepClone().asInstanceOf[Document]
        
        // Track which heading indices to keep
        val currentHeadingIdx = headingIdx
        val nextHeadingIdx = if (idx + 1 < allHeadings.length) allHeadings(idx + 1)._2 else -1
        
        logger.debug(s"Processing chunk for heading '$headingText': keeping headings $currentHeadingIdx to ${if (nextHeadingIdx == -1) "end" else (nextHeadingIdx - 1).toString}")
        
        // Remove content outside our range
        var currentHeadingCount = 0
        var inTargetSection = false
        val nodesToRemove = scala.collection.mutable.ArrayBuffer[Node]()
        
        for (sectionIdx <- 0 until sectionDoc.getSections.getCount) {
          val section = sectionDoc.getSections.get(sectionIdx).asInstanceOf[Section]
          val body = section.getBody
          
          // Process all body-level nodes
          for (nodeIdx <- 0 until body.getChildNodes(NodeType.ANY, false).getCount) {
            val node = body.getChildNodes(NodeType.ANY, false).get(nodeIdx)
            
            node match {
              case para: Paragraph if para.getParagraphFormat != null && 
                                      para.getParagraphFormat.getStyleName != null &&
                                      para.getParagraphFormat.getStyleName.startsWith("Heading 1") =>
                // This is a heading
                if (currentHeadingCount == currentHeadingIdx) {
                  inTargetSection = true
                } else if (nextHeadingIdx != -1 && currentHeadingCount == nextHeadingIdx) {
                  inTargetSection = false
                }
                
                // Remove heading if it's not in our target range
                if (currentHeadingCount < currentHeadingIdx || 
                    (nextHeadingIdx != -1 && currentHeadingCount >= nextHeadingIdx)) {
                  nodesToRemove += node
                }
                
                currentHeadingCount += 1
                
              case _ =>
                // Non-heading node - remove if not in target section
                if (!inTargetSection) {
                  nodesToRemove += node
                }
            }
          }
        }
        
        // Remove the marked nodes
        logger.debug(s"Removing ${nodesToRemove.size} nodes for heading '$headingText'")
        nodesToRemove.foreach(_.remove())
        
        // Convert to bytes
        val baos = new ByteArrayOutputStream()
        val saveOptions = createSaveOptions(mimeType)
        sectionDoc.save(baos, saveOptions)
        sectionDoc.cleanup()
        
        val chunkData = baos.toByteArray
        sections += DocChunk(
          FileContent(chunkData, mimeType),
          label = headingText,
          index = chunkIndex,
          total = allHeadings.length + (if (hasContentBeforeFirstHeading) 1 else 0)
        )
        chunkIndex += 1
      }
    }
    
    // Apply chunk range if specified
    cfg.chunkRange match {
      case Some(range) =>
        val validIndices = range.filter(i => i >= 0 && i < sections.length)
        validIndices.map(sections(_))
      case None => sections.toSeq
    }
  }
  
  private def createSaveOptions(mimeType: MimeType): SaveOptions = {
    mimeType match {
      case MimeType.ApplicationMsWord => 
        val options = new DocSaveOptions()
        options.setSaveFormat(SaveFormat.DOC)
        options
      case MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument =>
        val options = new OoxmlSaveOptions()
        options.setSaveFormat(SaveFormat.DOCX)
        options
      case _ =>
        new OoxmlSaveOptions() // Default to DOCX
    }
  }
  
}