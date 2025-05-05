package com.tjclp.xlcr
package parsers.excel

import parsers.ParserConfig

/** Configuration for Excel parsers.
  * 
  * @param evaluateFormulas Whether to evaluate all formulas during parsing (default: false)
  * @param includeHiddenSheets Whether to include hidden sheets in the output (default: false)
  * @param maxSheets Maximum number of sheets to parse (default: unlimited)
  * @param parseNamedRanges Whether to parse named ranges (default: false)
  * @param includeDocumentProperties Whether to include document properties (default: false)
  */
case class ExcelParserConfig(
  evaluateFormulas: Boolean = false,
  includeHiddenSheets: Boolean = false,
  maxSheets: Option[Int] = None,
  parseNamedRanges: Boolean = false,
  includeDocumentProperties: Boolean = false
) extends ParserConfig