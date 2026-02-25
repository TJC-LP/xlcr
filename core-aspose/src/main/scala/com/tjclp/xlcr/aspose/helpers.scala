package com.tjclp.xlcr.aspose

import java.io.ByteArrayInputStream

import com.tjclp.xlcr.types.*

import zio.blocks.scope.*

// Unscoped instances so Content/Fragment can escape Scope.global.scoped blocks
given unscopedMime[M <: Mime]: Unscoped[M] with               {}
given unscopedChunk[A: Unscoped]: Unscoped[zio.Chunk[A]] with {}
given unscopedContent[M <: Mime]: Unscoped[Content[M]] with   {}
given unscopedFragment[M <: Mime]: Unscoped[Fragment[M]] with {}
given Unscoped[DynamicFragment] with                          {}

// Resource factories for Aspose types — used with scope.allocate for compile-time leak prevention
private[aspose] def wordDocResource(
  doc: => com.aspose.words.Document
): Resource[com.aspose.words.Document] =
  Resource.acquireRelease(doc)(_.cleanup())

private[aspose] def presentationResource(
  pres: => com.aspose.slides.Presentation
): Resource[com.aspose.slides.Presentation] =
  Resource.acquireRelease(pres)(_.dispose())

private[aspose] def pdfDocResource(
  doc: => com.aspose.pdf.Document
): Resource[com.aspose.pdf.Document] =
  Resource.acquireRelease(doc)(_.close())

private[aspose] def cellsWorkbookResource(
  wb: => com.aspose.cells.Workbook
): Resource[com.aspose.cells.Workbook] =
  Resource.acquireRelease(wb)(_.dispose())

/**
 * Load an Aspose Cells Workbook from Content bytes, applying password from ConvertOptions.
 *
 * Uses `MemorySetting.MEMORY_PREFERENCE` to reduce heap pressure on large workbooks by allowing
 * Aspose to page data to disk when memory is constrained.
 */
private[aspose] def loadCellsWorkbook[M <: Mime](
  input: Content[M],
  options: ConvertOptions
): com.aspose.cells.Workbook =
  val loadOpts = new com.aspose.cells.LoadOptions()
  loadOpts.setMemorySetting(com.aspose.cells.MemorySetting.MEMORY_PREFERENCE)
  options.password.foreach(loadOpts.setPassword)
  new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray), loadOpts)
