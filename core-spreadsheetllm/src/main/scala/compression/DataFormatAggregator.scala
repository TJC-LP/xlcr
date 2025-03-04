package com.tjclp.xlcr
package compression

import compression.models.{CellInfo, SheetGrid}

import org.slf4j.LoggerFactory

/**
 * DataFormatAggregator applies format-based rules to compress regions of similar data.
 * It focuses on deterministic formats like dates, currencies, and numbers,
 * replacing specific values with format descriptors to reduce token usage.
 *
 * This is the third step in the SpreadsheetLLM compression pipeline,
 * applied after AnchorExtractor and InvertedIndexTranslator.
 */
object DataFormatAggregator:
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Applies format-based aggregation to the dictionary produced by the InvertedIndexTranslator.
   * Replaces regions of similar values with type descriptors.
   *
   * @param contentMap The map of cell content to locations from InvertedIndexTranslator
   * @param grid       The original sheet grid with cell information
   * @return An updated map with format aggregation applied
   */
  def aggregate(
                 contentMap: Map[String, Either[String, List[String]]],
                 grid: SheetGrid,
                 config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()
               ): Map[String, Either[String, List[String]]] =
    if contentMap.isEmpty then
      return contentMap

    // Build type and format maps for each cell in the grid
    val (typeMap, formatMap) = buildTypeMap(grid.cells.values.toSeq)

    // Identify which values are candidates for aggregation
    val (aggregateCandidates, preserveValues) = partitionAggregationCandidates(contentMap, typeMap)

    logger.info(s"Found ${aggregateCandidates.size} candidate entries for format aggregation")

    // Group candidates by data type and format
    val groupedByFormat = groupByFormat(aggregateCandidates, typeMap, formatMap)

    // Aggregate each format group
    val aggregatedEntries = aggregateFormatGroups(groupedByFormat)

    // Combine preserved values with aggregated entries
    val result = preserveValues ++ aggregatedEntries

    // Log compression results
    val compressionRatio = if result.nonEmpty then contentMap.size.toDouble / result.size else 1.0
    logger.info(f"Format aggregation: ${contentMap.size} entries -> ${result.size} entries ($compressionRatio%.2fx compression)")

    result

  /**
   * Builds a map from cell content to its detected data type.
   * Also captures Number Format Strings from Excel when available.
   *
   * @param cells Sequence of cell information
   * @return Tuple of (Map from cell value to detected data type, Map from cell value to NFS)
   */
  private def buildTypeMap(cells: Seq[CellInfo]): (Map[String, DataType], Map[String, String]) =
    // Build a map from cell value to data type
    val typeMap = cells.map { cell =>
      val dataType = inferDataType(cell.value, cell.isEmpty, cell.isDate, cell.isNumeric)
      (cell.value, dataType)
    }.toMap

    // Build a map from cell value to number format string (if available)
    val formatMap = cells.flatMap { cell =>
      if cell.numberFormatString.isDefined && cell.numberFormatString.get.nonEmpty then
        Some((cell.value, cell.numberFormatString.get))
      else
        None
    }.toMap

    (typeMap, formatMap)

  /**
   * Infers data type with pattern matching.
   *
   * @param value         The cell value as a string
   * @param isEmpty       Whether the cell is empty
   * @param isDateFlag    Whether the cell was flagged as a date by Excel
   * @param isNumericFlag Whether the cell was flagged as numeric by Excel
   * @return The detected DataType
   */
  private def inferDataType(
                             value: String,
                             isEmpty: Boolean,
                             isDateFlag: Boolean,
                             isNumericFlag: Boolean
                           ): DataType =
    if isEmpty then
      DataType.Empty
    else if isDateFlag then
      DataType.Date
    else if value.trim.endsWith("%") then
      DataType.Percentage
    else if
      """^[$€£¥₹]\s*[\d,.]+$""".r.matches(value) ||
        """^[\d,.]+\s*[$€£¥₹]$""".r.matches(value) then
      DataType.Currency
    else if """\d+[.]\d+[eE][+-]?\d+""".r.matches(value) then
      DataType.ScientificNotation
    else if """^(19|20)\d{2}$""".r.matches(value) then
      DataType.Year
    else if """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r.matches(value) then
      DataType.Email
    else if """^\d{1,2}:\d{2}(:\d{2})?(\s*[AaPp][Mm])?$""".r.matches(value) then
      DataType.Time
    else if isNumericFlag || """^\d+$""".r.matches(value) then
      if value.contains(".") then DataType.Float
      else DataType.Integer
    else
      DataType.Text

  /**
   * Separates content map entries into those that should be aggregated
   * and those that should be preserved as-is.
   *
   * @param contentMap The map of cell content to locations
   * @param typeMap    Map from cell value to data type
   * @return Tuple of (candidates for aggregation, entries to preserve)
   */
  private def partitionAggregationCandidates(
                                              contentMap: Map[String, Either[String, List[String]]],
                                              typeMap: Map[String, DataType]
                                            ): (Map[String, Either[String, List[String]]], Map[String, Either[String, List[String]]]) =
    contentMap.partition { case (content, _) =>
      val dataType = typeMap.getOrElse(content, DataType.Text)

      // Only aggregate deterministic formats
      dataType match
        case DataType.Integer | DataType.Float | DataType.Currency |
             DataType.Percentage | DataType.Date | DataType.Time |
             DataType.Year | DataType.ScientificNotation => true
        case _ => false
    }

  /**
   * Groups aggregation candidates by their data type and format.
   * Uses Excel's Number Format Strings when available.
   *
   * @param candidates Content map entries that are candidates for aggregation
   * @param typeMap    Map from cell value to data type
   * @param formatMap  Map from cell value to Excel number format string
   * @return Map from format descriptor to the entries of that format
   */
  private def groupByFormat(
                             candidates: Map[String, Either[String, List[String]]],
                             typeMap: Map[String, DataType],
                             formatMap: Map[String, String] = Map.empty
                           ): Map[FormatDescriptor, Map[String, Either[String, List[String]]]] =
    candidates.groupBy { case (content, _) =>
      val dataType = typeMap.getOrElse(content, DataType.Text)

      // Use Excel's NFS if available, otherwise infer from content
      val formatCode = if formatMap.contains(content) then
        inferFormatCode(content, dataType, Some(formatMap(content)))
      else
        inferFormatCode(content, dataType, None)

      // Create format descriptor with type, format code, and sample value
      FormatDescriptor(dataType, formatCode, Some(content))
    }

  /**
   * Detects format code from a cell value using pattern recognition.
   * Uses Number Format Strings from Excel when available.
   *
   * @param value       The cell value as a string
   * @param dataType    The detected data type
   * @param originalNFS Optional Number Format String from Excel (if available)
   * @return An optional format code
   */
  private def inferFormatCode(
                               value: String,
                               dataType: DataType,
                               originalNFS: Option[String] = None
                             ): Option[String] =
    // If we have an original NFS from Excel, normalize it
    if originalNFS.isDefined && originalNFS.get.nonEmpty then
      val nfs = originalNFS.get

      // Normalize Excel format codes for better aggregation
      val normalizedNFS = nfs match
        // Date formats - normalize to yyyy-mm-dd for consistency
        case f if f.contains("d") && f.contains("m") && f.contains("y") =>
          "yyyy-mm-dd"
        // Currency formats - normalize to standard format
        case f if f.contains("$") || f.contains("€") || f.contains("£") || f.contains("¥") =>
          if f.contains(".00") then "$#,##0.00" else "$#,##0"
        // Percentage formats
        case f if f.contains("%") =>
          if f.contains(".") then "0.00%" else "0%"
        // Accounting formats - normalize to currency
        case f if f.contains("_(") || f.contains("_)") => "$#,##0.00"
        // Scientific notation
        case f if f.contains("e") || f.contains("E") => "0.00E+00"
        // General numeric format with decimals
        case f if f.contains("#") && f.contains(".") => "#,##0.00"
        // General numeric format without decimals
        case f if f.contains("#") || f.contains("0") => "#,##0"
        // Time formats
        case f if f.contains("h") && f.contains("m") =>
          if f.contains("s") then "h:mm:ss" else "h:mm"
        // Keep custom formats as is
        case _ => nfs

      return Some(normalizedNFS)

    // Otherwise, infer format from the value and data type
    dataType match
      case DataType.Date =>
        // Detect common date formats
        val datePatterns = Map(
          """^\d{1,2}/\d{1,2}/\d{4}$""".r -> "mm/dd/yyyy",
          """^\d{4}-\d{1,2}-\d{1,2}$""".r -> "yyyy-mm-dd",
          """^\d{1,2}-[A-Za-z]{3}-\d{4}$""".r -> "dd-mmm-yyyy",
          """^\d{1,2}\.\d{1,2}\.\d{4}$""".r -> "dd.mm.yyyy"
        )

        datePatterns.collectFirst {
          case (pattern, format) if pattern.matches(value) => format
        }.orElse(Some("yyyy-mm-dd"))

      case DataType.Time =>
        // Detect common time formats
        val timePatterns = Map(
          """^\d{1,2}:\d{2}$""".r -> "hh:mm",
          """^\d{1,2}:\d{2}:\d{2}$""".r -> "hh:mm:ss",
          """^\d{1,2}:\d{2}\s*[AaPp][Mm]$""".r -> "hh:mm am/pm"
        )

        timePatterns.collectFirst {
          case (pattern, format) if pattern.matches(value) => format
        }.orElse(Some("hh:mm:ss"))

      case DataType.Currency =>
        // Format based on decimals and thousands separators
        val hasDecimals = value.contains(".")
        val format = if hasDecimals then "$#,##0.00" else "$#,##0"
        Some(format)

      case DataType.Percentage =>
        // Format based on decimals
        val percentValue = value.replace("%", "").trim
        val hasDecimals = percentValue.contains(".")
        val format = if hasDecimals then "0.00%" else "0%"
        Some(format)

      case DataType.Float =>
        // Format based on thousands separators
        val hasCommas = value.contains(",")
        val format = if hasCommas then "#,##0.00" else "#0.00"
        Some(format)

      case DataType.Integer =>
        // Format based on thousands separators
        val hasCommas = value.contains(",")
        val format = if hasCommas then "#,##0" else "#0"
        Some(format)

      case DataType.ScientificNotation =>
        Some("0.00E+00")

      case DataType.Year =>
        Some("yyyy")

      case DataType.Email =>
        Some("@")

      case _ => None

  /**
   * Aggregates each format group into a single descriptor entry.
   *
   * @param formatGroups Map from format descriptor to entries of that format
   * @return Map with aggregated format entries
   */
  private def aggregateFormatGroups(
                                     formatGroups: Map[FormatDescriptor, Map[String, Either[String, List[String]]]]
                                   ): Map[String, Either[String, List[String]]] =
    formatGroups.flatMap { case (formatDescriptor, entries) =>
      // If there's only one entry, no need to aggregate
      if entries.size <= 1 then
        entries
      else
        // Aggregate all locations for this format into one entry
        val allLocations = entries.flatMap { case (_, locationEither) =>
          locationEither match
            case Left(singleLocation) => List(singleLocation)
            case Right(locationList) => locationList
        }.toList

        // Use the format descriptor as the new key
        val formatKey = formatDescriptor.toFormatString

        // Create aggregated entry
        if allLocations.size == 1 then
          Map(formatKey -> Left(allLocations.head))
        else
          Map(formatKey -> Right(allLocations))
    }

  /**
   * Data type classifications for cell values.
   */
  enum DataType:
    case Integer, Float, Date, Time, Currency, Percentage, Year,
    ScientificNotation, Email, Text, Empty

  /**
   * Represents a format descriptor for a region of cells.
   *
   * @param dataType    The type of data in the region
   * @param formatCode  Optional Excel number format code
   * @param sampleValue Optional sample value from the region
   */
  case class FormatDescriptor(
                               dataType: DataType,
                               formatCode: Option[String] = None,
                               sampleValue: Option[String] = None
                             ):
    /**
     * Convert to a string representation for use as a map key.
     */
    def toFormatString: String =
      formatCode match
        case Some(code) =>
          val prefix = sampleValue.map(v => s"$v: ").getOrElse("")
          s"$prefix<${dataType.toString}:$code>"
        case None =>
          val prefix = sampleValue.map(v => s"$v: ").getOrElse("")
          s"$prefix<${dataType.toString}>"