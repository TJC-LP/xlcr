package com.tjclp.xlcr
package compression

import compression.AnchorExtractor.{CellInfo, SheetGrid}
import org.slf4j.LoggerFactory

/**
 * DataFormatAggregator applies format-based rules to compress regions of similar data.
 * It focuses especially on numeric or date-heavy areas, replacing specific values
 * with format descriptors to further reduce token usage.
 *
 * This is the third and final step in the SpreadsheetLLM compression pipeline,
 * applied after AnchorExtractor and InvertedIndexTranslator have done their work.
 */
object DataFormatAggregator:
  private val logger = LoggerFactory.getLogger(getClass)
  
  /**
   * Data type classifications for cell values.
   */
  enum DataType:
    case Integer, Float, Date, Time, Currency, Percentage, Year, 
         ScientificNotation, Email, Text, Empty
  
  /**
   * Represents a format descriptor for a region of cells.
   *
   * @param dataType The type of data in the region
   * @param formatCode Optional Excel number format code
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
          // Use format code if available
          val prefix = sampleValue.map(v => s"$v: ").getOrElse("")
          s"$prefix<${dataType.toString}:$code>"
        case None => 
          // Otherwise use data type with optional sample
          val prefix = sampleValue.map(v => s"$v: ").getOrElse("")
          s"$prefix<${dataType.toString}>"
  
  /**
   * Applies format-based aggregation to the dictionary produced by the InvertedIndexTranslator.
   * This replaces regions of similar values with type descriptors, particularly for
   * numeric data, dates, and other structured formats.
   *
   * @param contentMap The map of cell content to locations from InvertedIndexTranslator
   * @param grid The original sheet grid with cell information
   * @return An updated map with format aggregation applied
   */
  def aggregate(
    contentMap: Map[String, Either[String, List[String]]],
    grid: SheetGrid
  ): Map[String, Either[String, List[String]]] =
    if contentMap.isEmpty then
      return contentMap
    
    // Step 1: Build a type map for each cell in the grid
    val typeMap = buildTypeMap(grid.cells.values.toSeq)
    
    // Step 2: Identify which values are candidates for aggregation
    val (aggregateCandidates, preserveValues) = partitionAggregationCandidates(contentMap, typeMap)
    
    logger.info(s"Found ${aggregateCandidates.size} candidate entries for format aggregation")
    
    // Step 3: Group candidates by data type and format
    val groupedByFormat = groupByFormat(aggregateCandidates, typeMap)
    
    // Step 4: Aggregate each format group
    val aggregatedEntries = aggregateFormatGroups(groupedByFormat)
    
    // Step 5: Combine preserved values with aggregated entries
    val result = preserveValues ++ aggregatedEntries
    
    // Log compression results
    val compressionRatio = if result.nonEmpty then
      contentMap.size.toDouble / result.size
    else
      1.0
      
    logger.info(f"Format aggregation: ${contentMap.size} entries -> ${result.size} entries ($compressionRatio%.2fx compression)")
    
    result
  
  /**
   * Builds a map from cell content to its detected data type.
   *
   * @param cells Sequence of cell information
   * @return Map from cell value to detected data type
   */
  private def buildTypeMap(cells: Seq[CellInfo]): Map[String, DataType] =
    cells.map { cell =>
      val dataType = inferDataType(cell.value, 
                                  cell.isEmpty, 
                                  cell.isDate, 
                                  cell.isNumeric)
      
      (cell.value, dataType)
    }.toMap

  /**
   * Improved data type inference with more sophisticated pattern matching.
   * Uses regex patterns to identify specific formats like currencies, emails, etc.
   *
   * @param value The cell value as a string
   * @param isEmpty Whether the cell is empty
   * @param isDateFlag Whether the cell was flagged as a date by Excel
   * @param isNumericFlag Whether the cell was flagged as numeric by Excel
   * @return The detected DataType
   */
  private def inferDataType(value: String, 
                           isEmpty: Boolean, 
                           isDateFlag: Boolean, 
                           isNumericFlag: Boolean): DataType =
    if isEmpty then
      DataType.Empty
    else if isDateFlag then
      DataType.Date
    else if value.trim.endsWith("%") then
      DataType.Percentage
    else if """^[$€£¥₹]\s*[\d,.]+$""".r.matches(value) || 
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
    else if isNumericFlag then
      if value.contains(".") then DataType.Float
      else DataType.Integer
    else
      DataType.Text
  
  /**
   * Separates content map entries into those that should be aggregated
   * and those that should be preserved as-is.
   *
   * @param contentMap The map of cell content to locations
   * @param typeMap Map from cell value to data type
   * @return Tuple of (candidates for aggregation, entries to preserve)
   */
  private def partitionAggregationCandidates(
    contentMap: Map[String, Either[String, List[String]]],
    typeMap: Map[String, DataType]
  ): (Map[String, Either[String, List[String]]], Map[String, Either[String, List[String]]]) =
    contentMap.partition { case (content, _) =>
      val dataType = typeMap.getOrElse(content, DataType.Text)
      
      // Only aggregate numeric types, dates, times, etc.
      // Text values should generally be preserved as they often contain important information
      dataType match
        case DataType.Integer | DataType.Float | DataType.Currency | 
             DataType.Percentage | DataType.Date | DataType.Time |
             DataType.Year | DataType.ScientificNotation => true
        case _ => false
    }
  
  /**
   * Groups aggregation candidates by their data type and format.
   * This enhanced version also uses format codes for more precise grouping.
   *
   * @param candidates Content map entries that are candidates for aggregation
   * @param typeMap Map from cell value to data type
   * @return Map from format descriptor to the entries of that format
   */
  private def groupByFormat(
    candidates: Map[String, Either[String, List[String]]],
    typeMap: Map[String, DataType]
  ): Map[FormatDescriptor, Map[String, Either[String, List[String]]]] =
    // Group by inferred data type and format code
    candidates.groupBy { case (content, _) =>
      val dataType = typeMap.getOrElse(content, DataType.Text)
      val formatCode = inferFormatCode(content, dataType)
      
      // Create a format descriptor with type, format code, and sample value
      FormatDescriptor(dataType, formatCode, Some(content))
    }
  
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
   * Detects format code from a cell value using pattern recognition.
   * This enhanced version uses regex patterns to detect common formats.
   *
   * @param value The cell value as a string
   * @param dataType The detected data type
   * @return An optional format code
   */
  private def inferFormatCode(value: String, dataType: DataType): Option[String] =
    dataType match
      case DataType.Date =>
        // Detect common date formats: MM/DD/YYYY, YYYY-MM-DD, DD-MMM-YYYY, etc.
        val datePatterns = Map(
          """^\d{1,2}/\d{1,2}/\d{4}$""".r -> "mm/dd/yyyy",
          """^\d{4}-\d{1,2}-\d{1,2}$""".r -> "yyyy-mm-dd",
          """^\d{1,2}-[A-Za-z]{3}-\d{4}$""".r -> "dd-mmm-yyyy",
          """^\d{1,2}\.\d{1,2}\.\d{4}$""".r -> "dd.mm.yyyy"
        )
        
        datePatterns.collectFirst {
          case (pattern, format) if pattern.matches(value) => format
        }.orElse(Some("yyyy-mm-dd")) // Default date format
        
      case DataType.Time =>
        // Detect common time formats: HH:MM, HH:MM:SS, HH:MM AM/PM
        val timePatterns = Map(
          """^\d{1,2}:\d{2}$""".r -> "hh:mm",
          """^\d{1,2}:\d{2}:\d{2}$""".r -> "hh:mm:ss",
          """^\d{1,2}:\d{2}\s*[AaPp][Mm]$""".r -> "hh:mm am/pm"
        )
        
        timePatterns.collectFirst {
          case (pattern, format) if pattern.matches(value) => format
        }.orElse(Some("hh:mm:ss")) // Default time format
        
      case DataType.Currency =>
        // Detect currency format with symbol (before or after)
        val hasCurrencySymbol = """^[$€£¥₹]|[$€£¥₹]$""".r.findFirstIn(value).isDefined
        val hasCommas = value.contains(",")
        val hasDecimals = value.contains(".")
        val decimalPlaces = if hasDecimals then
          value.split("\\.")(1).length
        else
          0
          
        val format = 
          if hasCurrencySymbol then
            if hasCommas && hasDecimals then
              if decimalPlaces == 2 then "$#,##0.00"
              else s"$$#,##0.${"0" * decimalPlaces}"
            else if hasDecimals then
              if decimalPlaces == 2 then "$#0.00"
              else s"$$#0.${"0" * decimalPlaces}"
            else "$#,##0"
          else
            if hasCommas && hasDecimals then "#,##0.00"
            else if hasDecimals then "#0.00"
            else "#,##0"
            
        Some(format)
        
      case DataType.Percentage =>
        // Detect percentage format with varying decimal places
        val percentValue = value.replace("%", "").trim
        val hasDecimals = percentValue.contains(".")
        val decimalPlaces = if hasDecimals then
          percentValue.split("\\.")(1).length
        else
          0
          
        val format = 
          if decimalPlaces > 0 then s"0.${"0" * decimalPlaces}%"
          else "0%"
          
        Some(format)
        
      case DataType.Float =>
        // Detect floating point format with varying decimal places
        val hasCommas = value.contains(",")
        val decimalPlaces = if value.contains(".") then
          value.split("\\.")(1).length
        else
          0
          
        val format =
          if hasCommas then
            if decimalPlaces > 0 then s"#,##0.${"0" * decimalPlaces}"
            else "#,##0"
          else
            if decimalPlaces > 0 then s"#0.${"0" * decimalPlaces}"
            else "#0"
            
        Some(format)
        
      case DataType.Integer =>
        // Detect integer format with or without thousands separators
        val hasCommas = value.contains(",")
        val format = if hasCommas then "#,##0" else "#0"
        Some(format)
        
      case DataType.ScientificNotation =>
        // Scientific notation (e.g., 1.23E+04)
        Some("0.00E+00")
        
      case DataType.Year =>
        // 4-digit or 2-digit year
        val format = if value.length == 4 then "yyyy" else "yy"
        Some(format)
        
      case DataType.Email =>
        // No specific format for email, just indicate it's an email
        Some("@")
        
      case _ => None