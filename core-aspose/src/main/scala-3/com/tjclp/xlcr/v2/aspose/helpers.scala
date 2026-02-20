package com.tjclp.xlcr.v2.aspose

import java.io.ByteArrayInputStream

import zio.blocks.scope.Unscoped

import com.tjclp.xlcr.v2.types.{ Content, ConvertOptions, DynamicFragment, Fragment, Mime }

// Unscoped instances so Content/Fragment can escape Scope.global.scoped blocks
given unscopedMime[M <: Mime]: Unscoped[M] with               {}
given unscopedChunk[A: Unscoped]: Unscoped[zio.Chunk[A]] with {}
given unscopedContent[M <: Mime]: Unscoped[Content[M]] with   {}
given unscopedFragment[M <: Mime]: Unscoped[Fragment[M]] with {}
given Unscoped[DynamicFragment] with                          {}

/**
 * Load an Aspose Cells Workbook from Content bytes, applying password from ConvertOptions.
 *
 * Uses `MemorySetting.MEMORY_PREFERENCE` to reduce heap pressure on large workbooks by allowing
 * Aspose to page data to disk when memory is constrained.
 *
 * Caller is responsible for workbook lifecycle (e.g. registering with `Using.Manager`).
 */
private[aspose] def loadCellsWorkbook[M <: Mime](
  input: Content[M],
  options: ConvertOptions
): com.aspose.cells.Workbook =
  val loadOpts = new com.aspose.cells.LoadOptions()
  loadOpts.setMemorySetting(com.aspose.cells.MemorySetting.MEMORY_PREFERENCE)
  options.password.foreach(loadOpts.setPassword)
  new com.aspose.cells.Workbook(new ByteArrayInputStream(input.data.toArray), loadOpts)

/**
 * Safely evaluate all formulas in a workbook, silently ignoring failures.
 *
 * Formula evaluation can fail on workbooks with external references, missing add-ins, or
 * unsupported functions. Since this is best-effort (raw cell values are still available), we catch
 * and discard all exceptions rather than aborting the conversion.
 */
private[aspose] def safeCalculateFormulas(
  workbook: com.aspose.cells.Workbook,
  options: ConvertOptions
): Unit =
  if options.evaluateFormulas then
    try workbook.calculateFormula()
    catch case _: Exception => ()
