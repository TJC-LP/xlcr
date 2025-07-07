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
    // Find all paragraphs with Heading 1 style
    val headingNodes = doc.getChildNodes(NodeType.PARAGRAPH, true).asScala.collect {
      case para: Paragraph if para.getParagraphFormat != null && 
                              para.getParagraphFormat.getStyleName != null &&
                              para.getParagraphFormat.getStyleName.startsWith("Heading 1") => para
    }.toList
    
    if (headingNodes.isEmpty) {
      throw new EmptyDocumentException(
        mimeType.mimeType,
        "No Heading 1 paragraphs found in document"
      )
    }
    
    logger.info(s"Found ${headingNodes.length} Heading 1 paragraphs")
    
    // Split document into sections based on headings
    val chunks = headingNodes.zipWithIndex.map { case (headingPara, idx) =>
      // Get the heading text for the label
      val headingText = headingPara.getText.trim
      
      // Create a new document for this section
      val sectionDoc = new Document()
      val builder = new DocumentBuilder(sectionDoc)
      
      // Find the range of nodes to copy (from this heading to the next, or end of document)
      val startNode = headingPara
      val endNode = if (idx + 1 < headingNodes.length) {
        headingNodes(idx + 1).getPreviousSibling
      } else {
        // Last section - go to end of document
        findLastNode(doc)
      }
      
      // Copy nodes to the new document
      copyNodeRange(doc, sectionDoc, startNode, endNode)
      
      // Convert to bytes
      val baos = new ByteArrayOutputStream()
      val saveOptions = mimeType match {
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
      
      sectionDoc.save(baos, saveOptions)
      sectionDoc.cleanup()
      
      val chunkData = baos.toByteArray
      DocChunk(
        FileContent(chunkData, mimeType),
        label = headingText,
        index = idx,
        total = headingNodes.length
      )
    }
    
    // Apply chunk range if specified
    cfg.chunkRange match {
      case Some(range) =>
        val validIndices = range.filter(i => i >= 0 && i < chunks.length)
        validIndices.map(chunks(_))
      case None => chunks
    }
  }
  
  private def findLastNode(doc: Document): Node = {
    // Find the last paragraph or table in the document
    val body = doc.getFirstSection.getBody
    var lastNode: Node = null
    
    val nodes = body.getChildNodes(NodeType.ANY, true).asScala.toList
    nodes.reverse.find { node =>
      node.getNodeType == NodeType.PARAGRAPH || node.getNodeType == NodeType.TABLE
    }.getOrElse(body.getLastChild)
  }
  
  private def copyNodeRange(sourceDoc: Document, targetDoc: Document, startNode: Node, endNode: Node): Unit = {
    val importer = new NodeImporter(targetDoc, sourceDoc, ImportFormatMode.KEEP_SOURCE_FORMATTING)
    val body = targetDoc.getFirstSection.getBody
    
    var currentNode = startNode
    var continue = true
    
    while (currentNode != null && continue) {
      val nextNode = currentNode.getNextSibling
      
      // Import and append the node
      val importedNode = importer.importNode(currentNode, true)
      body.appendChild(importedNode)
      
      // Check if we've reached the end node
      if (currentNode == endNode) {
        continue = false
      }
      
      currentNode = nextNode
    }
  }
}