package com.tjclp.xlcr.v2.aspose

import java.io.ByteArrayInputStream

import com.tjclp.xlcr.v2.types.{ Content, ConvertOptions, Mime }

/**
 * Load an Aspose Cells Workbook from Content bytes, applying password from ConvertOptions.
 *
 * Caller is responsible for workbook lifecycle (e.g. registering with `Using.Manager`).
 */
private[aspose] def loadCellsWorkbook[M <: Mime](
  input: Content[M],
  options: ConvertOptions
): com.aspose.cells.Workbook =
  val loadOpts = new com.aspose.cells.LoadOptions()
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
