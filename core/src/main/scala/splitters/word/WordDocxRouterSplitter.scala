package com.tjclp.xlcr
package splitters.word

import org.slf4j.LoggerFactory
import models.FileContent
import splitters._
import types.MimeType

/**
 * Router splitter for DOCX files that dispatches to the appropriate implementation
 * based on the splitting strategy.
 */
object WordDocxRouterSplitter extends DocumentSplitter[MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type] 
    with SplitFailureHandler {
  
  override protected val logger = LoggerFactory.getLogger(getClass)
  override def priority: types.Priority = types.Priority.DEFAULT
  
  override def split(
    content: FileContent[MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type],
    cfg: SplitConfig
  ): Seq[DocChunk[_ <: MimeType]] = {
    
    // Determine which strategy to use
    val strategy = cfg.strategy match {
      case Some(SplitStrategy.Auto) | None =>
        // Default strategy for Word documents is Page
        Some(SplitStrategy.Page)
      case other => other
    }
    
    // Dispatch to appropriate splitter based on strategy
    strategy match {
      case Some(SplitStrategy.Page) =>
        logger.debug("Routing DOCX to page splitter")
        WordDocxPageSplitter.split(content, cfg.copy(strategy = Some(SplitStrategy.Page)))
        
      case Some(SplitStrategy.Heading) =>
        logger.debug("Routing DOCX to heading splitter")
        WordDocxHeadingSplitter.split(content, cfg.copy(strategy = Some(SplitStrategy.Heading)))
        
      case Some(other) =>
        handleInvalidStrategy(
          content,
          cfg,
          other.displayName,
          Seq("page", "heading")
        )
        
      case None =>
        // This shouldn't happen due to the normalization above, but handle it anyway
        handleInvalidStrategy(
          content,
          cfg,
          "none",
          Seq("page", "heading")
        )
    }
  }
}