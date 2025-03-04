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
    // Track pattern matching for debugging
    val matchInfo = new StringBuilder()
    
    // Variable to store the result
    val result: DataType =                         
      if isEmpty then
        matchInfo.append("isEmpty=true → Empty")
        DataType.Empty
      else if isDateFlag then
        matchInfo.append("isDateFlag=true → Date")
        DataType.Date
      else if value.trim.endsWith("%") then
        matchInfo.append("ends with % → Percentage")
        DataType.Percentage
      else if
        // More flexible currency patterns to handle extra spaces and formats
        val currencyPattern1 = """^\s*[$€£¥₹]\s*[\d,.]+\s*$""".r
        val currencyPattern2 = """^\s*[\d,.]+\s*[$€£¥₹]\s*$""".r
        val dollarPattern = """.*\$.*\d.*""".r // Special looser pattern for dollar sign
        
        val isCurrency = currencyPattern1.matches(value) || 
                        currencyPattern2.matches(value) || 
                        dollarPattern.matches(value)
        
        matchInfo.append(s"Currency pattern check: $value => $isCurrency")
        
        isCurrency
      then
        DataType.Currency
      else if 
        val scientificPattern = """\d+[.]\d+[eE][+-]?\d+""".r
        val isScientific = scientificPattern.matches(value)
        
        matchInfo.append(s"Scientific notation: $isScientific")
        
        isScientific
      then
        DataType.ScientificNotation
      else if 
        val yearPattern = """^(19|20)\d{2}$""".r
        val isYear = yearPattern.matches(value)
        
        matchInfo.append(s"Year pattern: $isYear")
        
        isYear
      then
        DataType.Year
      else if 
        val emailPattern = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r
        val isEmail = emailPattern.matches(value)
        
        matchInfo.append(s"Email pattern: $isEmail")
        
        isEmail
      then
        DataType.Email
      else if 
        val timePattern = """^\d{1,2}:\d{2}(:\d{2})?(\s*[AaPp][Mm])?$""".r
        val isTime = timePattern.matches(value)
        
        matchInfo.append(s"Time pattern: $isTime")
        
        isTime
      then
        DataType.Time
      else if
        // Check for numeric types - using more flexible patterns and isNumericFlag
        val integerPattern = """^\s*\d+\s*$""".r
        val floatPattern = """^\s*\d+\.\d+\s*$""".r
        
        val isInt = integerPattern.matches(value)
        val isFloat = floatPattern.matches(value)
        val isNumber = isNumericFlag || isInt || isFloat
        
        matchInfo.append(s"Number check: isNumericFlag=$isNumericFlag, isInt=$isInt, isFloat=$isFloat")
        
        isNumber
      then
        if value.contains(".") || value.trim.toDoubleOption.exists(_ != value.trim.toIntOption.getOrElse(0).toDouble) then 
          matchInfo.append("Classifying as Float")
          DataType.Float
        else
          matchInfo.append("Classifying as Integer")
          DataType.Integer
      else
        matchInfo.append("No pattern matched → Text")
        DataType.Text
      
    // Only log in verbose mode (disabled by default)
    // Return the determined type
    result

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
    val (candidates, preserved) = contentMap.partition { case (content, _) =>
      val dataType = typeMap.getOrElse(content, DataType.Text)

      // Only aggregate deterministic formats
      val isCandidate = dataType match
        case DataType.Integer | DataType.Float | DataType.Currency |
             DataType.Percentage | DataType.Date | DataType.Time |
             DataType.Year | DataType.ScientificNotation => true
        case _ => false
      
      isCandidate
    }
    
    // Simply log the number of candidates in normal log level
    logger.info(s"Found ${candidates.size} candidate entries for format aggregation")
    
    (candidates, preserved)

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
    if candidates.isEmpty then
      logger.info("No candidates to group by format")
      return Map.empty
    
    // Special handling for small numbers - they're likely the same type regardless of exact value
    // This is a preprocessing step to help with aggregation
    val isAllIntegers = candidates.keys.forall(s => 
      typeMap.getOrElse(s, DataType.Text) == DataType.Integer ||
      typeMap.getOrElse(s, DataType.Text) == DataType.Float
    )
    
    if isAllIntegers && candidates.size >= 2 && candidates.size <= 10 then
      // Group all the numeric values under a common descriptor
      // Choose the smallest value as the representative
      val minValue = candidates.keys.minBy(_.trim.toDoubleOption.getOrElse(Double.MaxValue))
      
      val dataType = typeMap.getOrElse(minValue, DataType.Integer)
      val formatCode = inferFormatCode(minValue, dataType, None)
      val descriptor = FormatDescriptor(dataType, formatCode, Some(s"<Numbers like $minValue>"))
      return Map(descriptor -> candidates)
    
    // Normal grouping  
    val grouped = candidates.groupBy { case (content, _) =>
      val dataType = typeMap.getOrElse(content, DataType.Text)

      // Use Excel's NFS if available, otherwise infer from content
      val formatCode = if formatMap.contains(content) then
        inferFormatCode(content, dataType, Some(formatMap(content)))
      else
        inferFormatCode(content, dataType, None)

      // Create format descriptor with type, format code, and sample value
      val descriptor = FormatDescriptor(dataType, formatCode, Some(content))
      
      descriptor
    }
    
    grouped

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
    if formatGroups.isEmpty then
      logger.info("No format groups to aggregate")
      return Map.empty
      
    formatGroups.flatMap { case (formatDescriptor, entries) =>
      // We'll always aggregate entries by data type, even if there's only one
      // This improves readability by using descriptive format names rather than raw values
      // Only skip aggregation for non-deterministic data types like Text
      
      // Special handling for dates - always aggregate even when there's only one entry
      if formatDescriptor.dataType == DataType.Date && entries.size == 1 then
        // Aggregate the single date entry to improve consistency
        val locationEither = entries.values.head
        val locations = locationEither match
          case Left(singleLocation) => List(singleLocation)
          case Right(locationList) => locationList
          
        // Use the format descriptor as the new key, with better naming
        val formatKey = formatDescriptor.formatCode match
          case Some(code) => s"<Date:$code>"
          case None => "<Date>"
          
        if locations.size == 1 then
          Map(formatKey -> Left(locations.head))
        else
          Map(formatKey -> Right(locations))
      // For other types with size 1, keep original, except for well-known types
      else if entries.size == 1 && !shouldAlwaysAggregate(formatDescriptor.dataType) then
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
   * Determines if a data type should always be aggregated, even with just one entry.
   * This helps consistency in the output and makes the data more readable for LLMs.
   */
  private def shouldAlwaysAggregate(dataType: DataType): Boolean =
    dataType match
      case DataType.Date | DataType.Time | DataType.Currency | 
           DataType.Percentage | DataType.Year => true
      case _ => false

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
     * Creates a more descriptive and LLM-friendly format description.
     */
    def toFormatString: String =
      // Get a more human-readable description of the dataType
      val typeDesc = dataType match
        case DataType.Date => "Date"
        case DataType.Time => "Time"
        case DataType.Currency => "Currency"
        case DataType.Percentage => "Percentage" 
        case DataType.Integer => "Number"
        case DataType.Float => "Decimal"
        case DataType.Year => "Year"
        case DataType.ScientificNotation => "Scientific"
        case DataType.Email => "Email"
        case DataType.Text => "Text"
        case DataType.Empty => "Empty"
      
      // Different format based on type
      (dataType, formatCode, sampleValue) match
        // For dates, prefer using just the format
        case (DataType.Date, Some(code), _) => s"<$typeDesc:$code>"
        case (DataType.Date, None, _) => s"<$typeDesc>"
        
        // For times, prefer using just the format  
        case (DataType.Time, Some(code), _) => s"<$typeDesc:$code>"
        case (DataType.Time, None, _) => s"<$typeDesc>"
        
        // For currency, prefer descriptive format
        case (DataType.Currency, Some(code), Some(sample)) => 
          // Extract the currency symbol if possible
          val symbol = sample.trim.find("$€£¥₹".contains(_)).map(_.toString).getOrElse("$")
          s"<$typeDesc like $symbol>"
        case (DataType.Currency, _, _) => "<Currency>"
        
        // For numbers, prefer "Numbers like X" format
        case ((DataType.Integer | DataType.Float), _, Some(sample)) => 
          s"<$typeDesc like $sample>"
        
        // For percentages, just use "Percentage" format
        case (DataType.Percentage, _, _) => "<Percentage>"
        
        // For everything else, use type and format code if available
        case (_, Some(code), _) => s"<$typeDesc:$code>"
        case (_, None, _) => s"<$typeDesc>"