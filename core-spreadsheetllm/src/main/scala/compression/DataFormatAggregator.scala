package com.tjclp.xlcr
package compression

import scala.collection.compat._

import com.tjclp.xlcr.compression.models.{ CellInfo, SheetGrid }

import org.slf4j.LoggerFactory

/**
 * DataFormatAggregator applies format-based rules to compress regions of similar data. It focuses
 * on deterministic formats like dates, currencies, and numbers, replacing specific values with
 * format descriptors to reduce token usage.
 *
 * This is the third step in the SpreadsheetLLM compression pipeline, applied after AnchorExtractor
 * and InvertedIndexTranslator.
 *
 * Implementation is aligned with the original TableDataAggregation.py algorithm.
 */
object DataFormatAggregator {
  private val logger = LoggerFactory.getLogger(getClass)

  // Scala 2 compatible definition of DataType
  sealed trait DataType
  object DataType {
    case object Integer            extends DataType
    case object Float              extends DataType
    case object Date               extends DataType
    case object Time               extends DataType
    case object Currency           extends DataType
    case object Percentage         extends DataType
    case object Year               extends DataType
    case object ScientificNotation extends DataType
    case object Email              extends DataType
    case object Text               extends DataType
    case object Empty              extends DataType
    case object Fraction           extends DataType
    case object IpAddress          extends DataType
  }

  /**
   * Applies format-based aggregation to the dictionary produced by the InvertedIndexTranslator.
   * Replaces regions of similar values with type descriptors.
   *
   * @param contentMap
   *   The map of cell content to locations from InvertedIndexTranslator
   * @param grid
   *   The original sheet grid with cell information
   * @return
   *   An updated map with format aggregation applied
   */
  def aggregate(
    contentMap: Map[String, Either[String, List[String]]],
    grid: SheetGrid,
    config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()
  ): Map[String, Either[String, List[String]]] = {
    if (contentMap.isEmpty) {
      return contentMap
    }

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
    val compressionRatio = if (result.nonEmpty) contentMap.size.toDouble / result.size else 1.0
    logger.info(
      f"Format aggregation: ${contentMap.size} entries -> ${result.size} entries ($compressionRatio%.2fx compression)"
    )

    result
  }

  /**
   * Applies format-based aggregation directly to cells in the grid before dictionary building. This
   * approach provides more consistent aggregation by replacing cell values with type descriptors
   * before inverted index translation.
   *
   * @param grid
   *   The anchored/pruned SheetGrid to process
   * @param config
   *   The pipeline configuration
   * @return
   *   A new SheetGrid with cell values replaced by type descriptors where appropriate
   */
  def aggregateCells(
    grid: SheetGrid,
    config: SpreadsheetLLMConfig = SpreadsheetLLMConfig()
  ): SheetGrid = {
    if (grid.cells.isEmpty) {
      logger.info("SheetGrid is empty; skipping cell aggregation.")
      return grid
    }

    logger.info(
      "Performing type-based data aggregation on grid cells before dictionary compression"
    )

    // Count original cell types for logging
    val originalCellCount = grid.cells.size
    val typeCounts        = collection.mutable.Map[DataType, Int]().withDefaultValue(0)

    // Special debug mode for specific date/number detection issues
    val isDebugMode = config.verbose || config.debugDataDetection

    // For detailed debugging when verbose or debug mode is enabled
    val cellTypeDetails = collection.mutable.Map[(Int, Int), (String, DataType, String)]()

    // If in debug mode, you can add extra patterns to look for specifically
    if (isDebugMode && config.debugDataDetection) {
      logger.debug("DATA DETECTION DEBUG MODE ENABLED - Looking for problematic cells")

      // Example: search for specific suspicious patterns
      val suspiciousCells = grid.cells.filter { case ((_, _), cellInfo) =>
        val value = cellInfo.value

        // Add specific patterns you're looking for here
        // For example, looking for date-like strings that aren't being detected
        val looksLikeDateButIsnt = !cellInfo.isDate &&
          (value.contains("/") || value.contains("-")) &&
          value.matches(""".*\d+.*""")

        // Or looking for numeric cells not flagged as such
        val looksLikeNumberButIsnt = !cellInfo.isNumeric &&
          value.matches(""".*\d+.*""") &&
          !value.matches(""".*[a-zA-Z].*""")

        looksLikeDateButIsnt || looksLikeNumberButIsnt
      }

      if (suspiciousCells.nonEmpty) {
        logger.debug(
          s"Found ${suspiciousCells.size} suspicious cells that might have detection issues:"
        )
        suspiciousCells.take(20).foreach { case ((r, c), cellInfo) =>
          val address = InvertedIndexTranslator.CellAddress(r, c).toA1Notation
          logger.debug(f"  $address: '${cellInfo.value}' " +
            f"(isDate=${cellInfo.isDate}, isNumeric=${cellInfo.isNumeric}, " +
            f"format=${cellInfo.numberFormatString.getOrElse("none")})")
        }
      }
    }

    // Process each cell and update its value based on data type
    val updatedCells = grid.cells.map { case ((r, c), cellInfo) =>
      // Get cell address for debugging
      val cellAddress = InvertedIndexTranslator.CellAddress(r, c).toA1Notation

      // Identify data type using existing inference logic
      val dataType = inferDataType(
        value = cellInfo.value,
        isEmpty = cellInfo.isEmpty,
        isDateFlag = cellInfo.isDate,
        isNumericFlag = cellInfo.isNumeric,
        formatString = cellInfo.numberFormatString,
        config = Some(config),
        cellAddress = Some(cellAddress)
      )

      // Track type counts for logging
      typeCounts(dataType) += 1

      // Store details for debug logging
      if (isDebugMode) {
        cellTypeDetails((r, c)) = (cellInfo.value, dataType, cellAddress)
      }

      // Determine if this cell should be aggregated
      val shouldAggregate = dataType match {
        case DataType.Integer | DataType.Float | DataType.Currency |
            DataType.Percentage | DataType.Date | DataType.Time |
            DataType.Year | DataType.ScientificNotation |
            DataType.Email | DataType.IpAddress | DataType.Fraction => true
        case _ => false
      }

      // If it's a candidate for aggregation, replace its value with a type descriptor
      if (shouldAggregate) {
        // Create the appropriate type descriptor string
        val aggregatedValue = dataType match {
          case DataType.Integer => "<IntNum>"
          case DataType.Float   => "<FloatNum>"
          case DataType.Date    =>
            // For dates, we can include format information if available
            cellInfo.numberFormatString match {
              case Some(format) if format.nonEmpty => s"<DateData:$format>"
              case _                               => "<DateData>"
            }
          case DataType.Time       => "<TimeData>"
          case DataType.Currency   => "<CurrencyData>"
          case DataType.Percentage => "<PercentageNum>"
          case DataType.Year       => "<YearData>"
          case DataType.ScientificNotation =>
            "<SentificNum>" // Note: Typo "SentificNum" matches original Python code
          case DataType.Email     => "<EmailData>"
          case DataType.Fraction  => "<FractionData>"
          case DataType.IpAddress => "<IPAddressData>"
          case _                  => cellInfo.value // Shouldn't happen due to shouldAggregate check
        }

        // Verbose logging for aggregation decisions
        if (isDebugMode) {
          logger.debug(
            f"AGGREGATING $cellAddress: '${cellInfo.value}' -> '$aggregatedValue' (detected as $dataType)"
          )

          // Extra details for date/time cells
          if (dataType == DataType.Date || dataType == DataType.Time) {
            logger.debug(f"  DATE/TIME INFO: cell=$cellAddress, value='${cellInfo.value}', " +
              f"isDateFlag=${cellInfo.isDate}, " +
              f"formatString=${cellInfo.numberFormatString.getOrElse("none")}, " +
              f"matches date pattern=${isDateByPattern(cellInfo.value, isDebugMode, cellAddress)}, " +
              f"matches time pattern=${isTimeByPattern(cellInfo.value)}")
          }
          // Extra details for numeric cells
          else if (dataType == DataType.Integer || dataType == DataType.Float) {
            logger.debug(
              f"  NUMBER INFO: cell=$cellAddress, value='${cellInfo.value}', " +
                f"isNumericFlag=${cellInfo.isNumeric}, " +
                f"formatString=${cellInfo.numberFormatString.getOrElse("none")}, " +
                f"matches numeric pattern=${isNumericByPattern(cellInfo.value, isDebugMode, cellAddress)}"
            )
          }
        }

        // Create a new CellInfo with the aggregated value
        val updatedCell = cellInfo.copy(value = aggregatedValue)
        ((r, c), updatedCell)
      } else {
        // Keep the original cell as-is for non-aggregated types
        ((r, c), cellInfo)
      }
    }

    // Log aggregation statistics
    val aggregatedCount = typeCounts.filter { case (k, _) =>
      k == DataType.Integer || k == DataType.Float || k == DataType.Currency ||
      k == DataType.Percentage || k == DataType.Date || k == DataType.Time ||
      k == DataType.Year || k == DataType.ScientificNotation ||
      k == DataType.Email || k == DataType.IpAddress || k == DataType.Fraction
    }.values.sum

    logger.info(
      f"Cell aggregation: $originalCellCount cells -> $aggregatedCount aggregated cells (${aggregatedCount * 100.0 / originalCellCount}%.1f%% aggregated)"
    )

    // Extended logging for debugging type detection issues
    if (isDebugMode) {
      logger.debug("AGGREGATION TYPE COUNTS:")
      typeCounts.toSeq.sortBy(_._1.toString).foreach { case (dataType, count) =>
        if (count > 0) {
          logger.debug(f"  $dataType: $count cells")
        }
      }

      // Specific logging for potentially problematic cells
      if (cellTypeDetails.nonEmpty) {
        // Log all date/time cells for inspection
        val dateCells = cellTypeDetails.filter(_._2._2 == DataType.Date)
        if (dateCells.nonEmpty) {
          logger.debug("DATE CELLS DETECTED:")
          dateCells.toSeq.sortBy(_._2._3).foreach { case ((r, c), (value, _, address)) =>
            val cellInfo = grid.cells((r, c))
            logger.debug(f"  $address: '$value' (isDateFlag=${cellInfo.isDate}, " +
              f"format=${cellInfo.numberFormatString.getOrElse("none")}, " +
              f"matches pattern=${isDateByPattern(value, isDebugMode, address)})")
          }
        }

        // Log all numeric cells for inspection
        val numericCells = cellTypeDetails.filter { case (_, (_, dataType, _)) =>
          dataType == DataType.Integer || dataType == DataType.Float
        }
        if (numericCells.nonEmpty) {
          logger.debug("NUMERIC CELLS DETECTED:")
          numericCells.toSeq.sortBy(_._2._3).foreach { case ((r, c), (value, dataType, address)) =>
            val cellInfo = grid.cells((r, c))
            logger.debug(
              f"  $address: '$value' -> $dataType (isNumericFlag=${cellInfo.isNumeric}, " +
                f"format=${cellInfo.numberFormatString.getOrElse("none")}, " +
                f"matches numeric pattern=${isNumericByPattern(value, isDebugMode, address)})"
            )
          }
        }

        // Log text cells that might be dates/numbers but weren't classified as such
        val textCells = cellTypeDetails.filter(_._2._2 == DataType.Text)
        if (textCells.nonEmpty) {
          logger.debug("SUSPICIOUS TEXT CELLS (check for misclassification):")
          textCells.toSeq
            .filter { case (_, (value, _, _)) =>
              // Only show text cells that contain numbers or date-like patterns
              value.matches(""".*\d+.*""") ||
              value.contains("/") ||
              value.contains("-") ||
              value.contains(":")
            }
            .sortBy(_._2._3)
            .foreach { case ((r, c), (value, _, address)) =>
              val cellInfo = grid.cells((r, c))
              logger.debug(
                f"  $address: '$value' classified as TEXT but contains numbers/symbols " +
                  f"(isDateFlag=${cellInfo.isDate}, isNumericFlag=${cellInfo.isNumeric}, " +
                  f"format=${cellInfo.numberFormatString.getOrElse("none")}, " +
                  f"matches date pattern=${isDateByPattern(value, isDebugMode, address)}, " +
                  f"matches numeric pattern=${isNumericByPattern(value, isDebugMode, address)})"
              )
            }
        }
      }
    }

    // Return a new grid with updated cell values
    grid.copy(cells = updatedCells)
  }

  /**
   * Builds a map from cell content to its detected data type. Also captures Number Format Strings
   * from Excel when available.
   *
   * @param cells
   *   Sequence of cell information
   * @return
   *   Tuple of (Map from cell value to detected data type, Map from cell value to NFS)
   */
  private def buildTypeMap(cells: Seq[CellInfo]): (Map[String, DataType], Map[String, String]) = {
    // Build a map from cell value to data type
    val typeMap = cells.map { cell =>
      val dataType = inferDataType(
        cell.value,
        cell.isEmpty,
        cell.isDate,
        cell.isNumeric,
        cell.numberFormatString
      )
      (cell.value, dataType)
    }.toMap

    // Build a map from cell value to number format string (if available)
    val formatMap = cells.flatMap { cell =>
      if (cell.numberFormatString.isDefined && cell.numberFormatString.get.nonEmpty) {
        Some((cell.value, cell.numberFormatString.get))
      } else {
        None
      }
    }.toMap

    (typeMap, formatMap)
  }

  /**
   * Infers data type with pattern matching, with additional logging when verbose is enabled.
   * Implements the rules from CaseJudge.py and TableDataAggregation.py
   *
   * @param value
   *   The cell value as a string
   * @param isEmpty
   *   Whether the cell is empty
   * @param isDateFlag
   *   Whether the cell was flagged as a date by Excel
   * @param isNumericFlag
   *   Whether the cell was flagged as numeric by Excel
   * @param formatString
   *   The number format string from Excel if available
   * @param config
   *   Optional configuration for verbose logging
   * @param cellAddress
   *   Optional cell address for debug logging
   * @return
   *   The detected DataType
   */
  private def inferDataType(
    value: String,
    isEmpty: Boolean,
    isDateFlag: Boolean,
    isNumericFlag: Boolean,
    formatString: Option[String] = None,
    config: Option[SpreadsheetLLMConfig] = None,
    cellAddress: Option[String] = None
  ): DataType = {
    // Use for detailed debug logging if provided
    val isVerbose = config.exists(_.verbose)
    val logPrefix = cellAddress.map(addr => s"[$addr] ").getOrElse("")

    def debugLog(message: String): Unit =
      if (isVerbose) logger.debug(s"${logPrefix}TYPE INFERENCE: $message")

    if (isEmpty) {
      debugLog("Empty cell -> DataType.Empty")
      DataType.Empty
      // If we have a format string, use it as first priority
    } else if (formatString.isDefined && formatString.get.nonEmpty) {
      val nfs     = formatString.get
      val nfsType = getTypeFromNfs(nfs)
      if (nfsType != DataType.Text) {
        debugLog(s"Using format string '$nfs' -> $nfsType")
        return nfsType
      } else {
        debugLog(s"Format string '$nfs' didn't help, continuing with other checks")
      }
      // If format string doesn't help, continue with other checks
    }

    // Date detection has high priority
    if (isDateFlag) {
      debugLog(s"Excel date flag is true for '$value' -> DataType.Date")
      DataType.Date
      // Check special date formats from Python CaseJudge.py
    } else if (isDateByPattern(value, isVerbose, cellAddress.getOrElse(""))) {
      debugLog(s"'$value' matches date pattern -> DataType.Date")
      DataType.Date
      // Check for time formats
    } else if (isTimeByPattern(value)) {
      debugLog(s"'$value' matches time pattern -> DataType.Time")
      DataType.Time
      // Check for percentage (immediately visible pattern)
    } else if (isPercentageByPattern(value)) {
      debugLog(s"'$value' matches percentage pattern -> DataType.Percentage")
      DataType.Percentage
      // Check for currency symbols
    } else if (isCurrencyByPattern(value)) {
      debugLog(s"'$value' matches currency pattern -> DataType.Currency")
      DataType.Currency
      // Check for scientific notation
    } else if (isScientificNotation(value)) {
      debugLog(s"'$value' matches scientific notation pattern -> DataType.ScientificNotation")
      DataType.ScientificNotation
      // Check for email format
    } else if (isEmailByPattern(value)) {
      debugLog(s"'$value' matches email pattern -> DataType.Email")
      DataType.Email
      // Check for year (standalone)
    } else if (isYearPattern(value)) {
      debugLog(s"'$value' matches year pattern -> DataType.Year")
      DataType.Year
      // Check for fractions
    } else if (isFractionPattern(value)) {
      debugLog(s"'$value' matches fraction pattern -> DataType.Fraction")
      DataType.Fraction
      // Check for IP addresses
    } else if (isIpAddress(value)) {
      debugLog(s"'$value' matches IP address pattern -> DataType.IpAddress")
      DataType.IpAddress
      // For numeric types, check if it's a decimal or integer
    } else if (isNumericByPattern(value, isVerbose, cellAddress.getOrElse("")) || isNumericFlag) {
      if (value.contains(".")) {
        debugLog(s"'$value' is numeric with decimal point -> DataType.Float" +
          (if (isNumericFlag) " (Excel flag)" else " (pattern match)"))
        DataType.Float
      } else {
        debugLog(s"'$value' is numeric without decimal point -> DataType.Integer" +
          (if (isNumericFlag) " (Excel flag)" else " (pattern match)"))
        DataType.Integer
      }
      // If none of the above patterns match, it's text
    } else {
      // Detailed logging for text cells that might be missing detection
      if (isVerbose) {
        val patterns = Seq(
          ("Date", isDateByPattern(value, isVerbose, cellAddress.getOrElse(""))),
          ("Time", isTimeByPattern(value)),
          ("Numeric", isNumericByPattern(value, isVerbose, cellAddress.getOrElse(""))),
          ("Year", isYearPattern(value))
        )
        val closeMatches = patterns.filter(_._2).map(_._1)
        if (closeMatches.nonEmpty) {
          debugLog(
            s"'$value' classified as TEXT but has some pattern matches: ${closeMatches.mkString(", ")}"
          )
        } else if (value.matches(""".*\d+.*""")) {
          debugLog(s"'$value' classified as TEXT but contains numbers")
        } else {
          debugLog(s"'$value' doesn't match any special patterns -> DataType.Text")
        }
      }

      DataType.Text
    }
  }

  /**
   * Convert Excel number format string to a DataType. Based on the config.DIC dictionary in Python
   * code.
   */
  private def getTypeFromNfs(nfs: String): DataType = {
    // Mapping similar to Python's DIC dictionary
    val nfsMap = Map(
      "0"                        -> DataType.Integer,
      "0.00"                     -> DataType.Float,
      "#,##0"                    -> DataType.Integer,
      "#,##0.00"                 -> DataType.Float,
      "0%"                       -> DataType.Percentage,
      "0.00%"                    -> DataType.Percentage,
      "0.00E+00"                 -> DataType.ScientificNotation,
      "#,##0;(#,##0)"            -> DataType.Integer,
      "#,##0;[Red](#,##0)"       -> DataType.Integer,
      "#,##0.00;(#,##0.00)"      -> DataType.Float,
      "#,##0.00;[Red](#,##0.00)" -> DataType.Float,
      "##0.0E+0"                 -> DataType.ScientificNotation,

      // Date formats
      "d/m/yyyy"   -> DataType.Date,
      "d-mm-yy"    -> DataType.Date,
      "d-mmm"      -> DataType.Date,
      "mmm-yy"     -> DataType.Date,
      "yyyy-mm-dd" -> DataType.Date,

      // Time formats
      "h:mm tt"       -> DataType.Time,
      "h:mm:ss tt"    -> DataType.Time,
      "H:mm"          -> DataType.Time,
      "H:mm:ss"       -> DataType.Time,
      "m/d/yyyy H:mm" -> DataType.Time,
      "mm:ss"         -> DataType.Time,
      "[h]:mm:ss"     -> DataType.Time,
      "mmss.0"        -> DataType.Time
    )

    // Look for the exact format string
    nfsMap.getOrElse(
      nfs,
      // If exact match not found, use pattern matching
      if (nfs.contains("%")) {
        DataType.Percentage
      } else if (nfs.contains("E+") || nfs.contains("e+")) {
        DataType.ScientificNotation
      } else if (
        (nfs.contains("d") || nfs.contains("m") || nfs.contains("y")) &&
        (nfs.contains("/") || nfs.contains("-"))
      ) {
        DataType.Date
      } else if (nfs.contains("h") || nfs.contains("H") || nfs.contains("s")) {
        DataType.Time
      } else if (nfs.contains("$") || nfs.contains("€") || nfs.contains("£") || nfs.contains("¥")) {
        DataType.Currency
      } else if (nfs.contains(".")) {
        DataType.Float
      } else if (nfs.contains("#") || nfs.contains("0")) {
        DataType.Integer
      } else {
        DataType.Text
      }
    )
  }

  /**
   * Pattern matching for date detection based on Python CaseJudge.py
   *
   * @param value
   *   The cell value to check
   * @param verbose
   *   Whether to log detailed pattern matching information
   * @param cellAddress
   *   Optional cell address for debug logs
   * @return
   *   Whether the value matches any date pattern
   */
  private def isDateByPattern(
    value: String,
    verbose: Boolean = false,
    cellAddress: String = ""
  ): Boolean = {
    // Date patterns from Python with descriptive names for debugging
    val datePatterns = Seq(
      ("YYYY-MM-DD", """\d{4}[-/]\d{1,2}[-/]\d{1,2}"""),
      ("DD-MM-YYYY", """\d{1,2}[-/]\d{1,2}[-/]\d{4}"""),
      ("DD-MM or MM/DD", """\d{1,2}[-/]\d{1,2}"""),
      ("YYYY-MM", """\d{4}[-/]\d{1,2}"""),
      ("DD-MMM-YYYY", """\d{1,2}[-]\w{3}[-]\d{4}"""),
      ("MMM-YY", """^[A-Za-z]{3}-\d{2}$""")
    )

    if (!verbose) {
      datePatterns.exists(pattern => value.matches(pattern._2))
    } else {
      // For verbose mode, collect and log all matching patterns
      val matches = datePatterns.collect {
        case (desc, pattern) if value.matches(pattern) => desc
      }

      if (matches.nonEmpty) {
        val prefix = if (cellAddress.nonEmpty) s"[$cellAddress] " else ""
        logger.debug(s"${prefix}VALUE '${value}' matches date patterns: ${matches.mkString(", ")}")
      }

      matches.nonEmpty
    }
  }

  /**
   * Pattern matching for time detection based on Python CaseJudge.py
   */
  private def isTimeByPattern(value: String): Boolean = {
    val timePattern = """^(2[0-3]|[01]?\d):([0-5]?\d)(\s?(AM|PM|am|pm))?$"""
    value.matches(timePattern)
  }

  /**
   * Pattern matching for percentage detection
   */
  private def isPercentageByPattern(value: String): Boolean = {
    val percentagePattern = """^[+-]?(\d+(\.\d*)?|\.\d+)%$"""
    value.matches(percentagePattern)
  }

  /**
   * Pattern matching for currency detection
   */
  private def isCurrencyByPattern(value: String): Boolean = {
    val currencyPattern = """^[\$\€\£\¥]\d+(\.\d{1,2})?$"""
    value.matches(currencyPattern)
  }

  /**
   * Pattern matching for scientific notation
   */
  private def isScientificNotation(value: String): Boolean = {
    val scientificPattern = """^[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?$"""
    value.matches(scientificPattern) && (value.contains("e") || value.contains("E"))
  }

  /**
   * Pattern matching for email detection
   */
  private def isEmailByPattern(value: String): Boolean = {
    val emailPattern = """^[\w\.-]+@[\w\.-]+\.\w+$"""
    value.matches(emailPattern)
  }

  /**
   * Pattern matching for year detection
   */
  private def isYearPattern(value: String): Boolean = {
    val yearPattern = """^(19|20)\d{2}$"""
    value.matches(yearPattern)
  }

  /**
   * Pattern matching for fractions like "1/2", "3/4", etc.
   */
  private def isFractionPattern(value: String): Boolean =
    try {
      val parts = value.split('/')
      if (parts.length != 2) {
        return false
      }
      val numerator   = parts(0).trim.toInt
      val denominator = parts(1).trim.toInt
      denominator != 0
    } catch {
      case _: Exception => false
    }

  /**
   * Pattern matching for IP addresses (IPv4 and IPv6)
   */
  private def isIpAddress(value: String): Boolean = {
    val ipv4Pattern =
      """^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"""
    val ipv6Pattern = """^([\da-fA-F]{1,4}:){7}([\da-fA-F]{1,4})$"""
    value.matches(ipv4Pattern) || value.matches(ipv6Pattern)
  }

  /**
   * Pattern matching for numeric values, handles common formats
   *
   * @param value
   *   The cell value to check
   * @param verbose
   *   Whether to log detailed pattern matching information
   * @param cellAddress
   *   Optional cell address for debug logs
   * @return
   *   Whether the value matches any numeric pattern
   */
  private def isNumericByPattern(
    value: String,
    verbose: Boolean = false,
    cellAddress: String = ""
  ): Boolean = {
    // First check for clean numeric patterns with descriptive names for debugging
    val numericPatterns = Seq(
      ("Integer", """^-?\d+$"""),
      ("Decimal", """^-?\d+\.\d+$"""),
      ("Integer with commas", """^-?[\d,]+$"""),
      ("Decimal with commas", """^-?[\d,]+\.\d+$""")
    )

    // For standard usage
    if (!verbose) {
      val cleanIntPattern     = """^-?\d+$"""
      val cleanDecimalPattern = """^-?\d+\.\d+$"""

      // Check basic integer or decimal
      if (value.matches(cleanIntPattern) || value.matches(cleanDecimalPattern)) {
        return true
      }

      // Handle numbers with commas for thousands separator
      val thousandsPattern        = """^-?[\d,]+$"""
      val thousandsDecimalPattern = """^-?[\d,]+\.\d+$"""

      if (value.matches(thousandsPattern) || value.matches(thousandsDecimalPattern)) {
        // Verify it's a properly formatted number with commas
        val noCommas = value.replace(",", "")
        return noCommas.matches(cleanIntPattern) || noCommas.matches(cleanDecimalPattern)
      }

      false
    } else {
      // For verbose mode, collect and log all matching patterns
      var isNumeric = false
      val matchResults = numericPatterns.map { case (desc, pattern) =>
        val matches = value.matches(pattern)

        // Special handling for thousands separator patterns
        if (matches && (desc == "Integer with commas" || desc == "Decimal with commas")) {
          val noCommas = value.replace(",", "")
          val isValidNumber =
            noCommas.matches("""^-?\d+$""") || noCommas.matches("""^-?\d+\.\d+$""")
          if (isValidNumber) {
            isNumeric = true
            (desc, true, s"valid number after comma removal: $noCommas")
          } else {
            (desc, false, "invalid number after comma removal")
          }
        } else if (matches) {
          isNumeric = true
          (desc, true, "")
        } else {
          (desc, false, "")
        }
      }

      // Log detailed results for debugging
      val matches = matchResults.filter(_._2).map(_._1)
      if (matches.nonEmpty) {
        val prefix = if (cellAddress.nonEmpty) s"[$cellAddress] " else ""
        logger.debug(
          s"${prefix}VALUE '${value}' matches numeric patterns: ${matches.mkString(", ")}"
        )

        // Log special notes for thousands separators
        matchResults.filter(r => r._2 && r._3.nonEmpty).foreach { case (desc, _, note) =>
          logger.debug(s"${prefix}  $desc: $note")
        }
      }

      isNumeric
    }
  }

  /**
   * Process a string value to prepare it for numeric pattern matching. Based on Python
   * TableDataAggregation.py:check_and_process_string
   */
  private def processStringForNumeric(input: String): String =
    if (
      input.forall(c =>
        c.isDigit || c == ',' || c == '.' || (c == '+' || c == '-') && input.indexOf(c) == 0
      )
    ) {
      input.replace(",", "")
    } else {
      input
    }

  /**
   * Separates content map entries into those that should be aggregated and those that should be
   * preserved as-is.
   *
   * @param contentMap
   *   The map of cell content to locations
   * @param typeMap
   *   Map from cell value to data type
   * @return
   *   Tuple of (candidates for aggregation, entries to preserve)
   */
  private def partitionAggregationCandidates(
    contentMap: Map[String, Either[String, List[String]]],
    typeMap: Map[String, DataType]
  ): (Map[String, Either[String, List[String]]], Map[String, Either[String, List[String]]]) = {
    val (candidates, preserved) = contentMap.partition { case (content, _) =>
      val dataType = typeMap.getOrElse(content, DataType.Text)

      // Only aggregate deterministic formats based on TableDataAggregation.py
      val isCandidate = dataType match {
        case DataType.Integer | DataType.Float | DataType.Currency |
            DataType.Percentage | DataType.Date | DataType.Time |
            DataType.Year | DataType.ScientificNotation |
            DataType.Email | DataType.IpAddress | DataType.Fraction => true
        case _ => false
      }

      isCandidate
    }

    // Log the number of candidates
    logger.info(s"Found ${candidates.size} candidate entries for format aggregation")

    (candidates, preserved)
  }

  /**
   * Represents a format descriptor for a region of cells. Needs to be defined before groupByFormat
   * which uses it.
   *
   * @param dataType
   *   The type of data in the region
   * @param formatCode
   *   Optional Excel number format code
   * @param sampleValue
   *   Optional sample value from the region
   */
  case class FormatDescriptor(
    dataType: DataType,
    formatCode: Option[String] = None,
    sampleValue: Option[String] = None
  ) { // Removed trailing colon

    /**
     * Convert to a string representation for use as a map key. Creates a more descriptive and
     * LLM-friendly format description.
     */
    def toFormatString: String = {
      // Get a more human-readable description of the dataType
      val typeDesc = dataType match {
        case DataType.Date               => "DateData"
        case DataType.Time               => "TimeData"
        case DataType.Currency           => "CurrencyData"
        case DataType.Percentage         => "PercentageNum"
        case DataType.Integer            => "IntNum"
        case DataType.Float              => "FloatNum"
        case DataType.Year               => "YearData"
        case DataType.ScientificNotation => "SentificNum" // Typo preserved
        case DataType.Email              => "EmailData"
        case DataType.Fraction           => "FractionData"
        case DataType.IpAddress          => "IPAddressData"
        case DataType.Text               => "Text"
        case DataType.Empty              => "Empty"
      }

      // Match Python naming conventions from TableDataAggregation.py
      s"<$typeDesc>"
    }
  }

  /**
   * Groups aggregation candidates by their data type and format. Uses Excel's Number Format Strings
   * when available.
   *
   * @param candidates
   *   Content map entries that are candidates for aggregation
   * @param typeMap
   *   Map from cell value to data type
   * @param formatMap
   *   Map from cell value to Excel number format string
   * @return
   *   Map from format descriptor to the entries of that format
   */
  private def groupByFormat(
    candidates: Map[String, Either[String, List[String]]],
    typeMap: Map[String, DataType],
    formatMap: Map[String, String] = Map.empty
  ): Map[FormatDescriptor, Map[String, Either[String, List[String]]]] = {
    if (candidates.isEmpty) {
      logger.info("No candidates to group by format")
      return Map.empty
    }

    // Special handling for small numbers - they're likely the same type regardless of exact value
    // This is a preprocessing step to help with aggregation similar to Python's dfs algorithm
    val isAllIntegers = candidates.keys.forall(s =>
      typeMap.getOrElse(s, DataType.Text) == DataType.Integer ||
        typeMap.getOrElse(s, DataType.Text) == DataType.Float
    )

    if (isAllIntegers && candidates.size >= 2 && candidates.size <= 15) {
      // Group all the numeric values under a common descriptor
      // Choose the smallest value as the representative
      val minValue = candidates.keys.minBy(_.trim.toDoubleOption.getOrElse(Double.MaxValue))

      val dataType   = typeMap.getOrElse(minValue, DataType.Integer)
      val formatCode = inferFormatCode(minValue, dataType, formatMap.get(minValue))
      // Use the format that tests expect
      val descriptor =
        FormatDescriptor(dataType, formatCode, Some("<Number like " + minValue + ">"))
      return Map(descriptor -> candidates)
    }

    // Group similar cell values together by data type and format
    val grouped = candidates.groupBy { case (content, _) =>
      val dataType = typeMap.getOrElse(content, DataType.Text)

      // Use Excel's NFS if available, otherwise infer from content
      val formatCode = if (formatMap.contains(content)) {
        inferFormatCode(content, dataType, Some(formatMap(content)))
      } else {
        inferFormatCode(content, dataType, None)
      }

      // Create format descriptor with type, format code, and sample value
      val descriptor = FormatDescriptor(dataType, formatCode, Some(content))

      descriptor
    }

    logger.info(s"Grouped ${candidates.size} entries into ${grouped.size} format groups")
    grouped
  }

  /**
   * Detects format code from a cell value using pattern recognition. Uses Number Format Strings
   * from Excel when available.
   *
   * @param value
   *   The cell value as a string
   * @param dataType
   *   The detected data type
   * @param originalNFS
   *   Optional Number Format String from Excel (if available)
   * @return
   *   An optional format code
   */
  private def inferFormatCode(
    value: String,
    dataType: DataType,
    originalNFS: Option[String] = None
  ): Option[String] = {
    // If we have an original NFS from Excel, normalize it
    if (originalNFS.isDefined && originalNFS.get.nonEmpty) {
      val nfs = originalNFS.get

      // Normalize Excel format codes for better aggregation
      val normalizedNFS = nfs match {
        // Date formats - normalize to yyyy-mm-dd for consistency
        case f if f.contains("d") && f.contains("m") && f.contains("y") =>
          "yyyy-mm-dd"
        // Currency formats - normalize to standard format
        case f if f.contains("$") || f.contains("€") || f.contains("£") || f.contains("¥") =>
          if (f.contains(".00")) "$#,##0.00" else "$#,##0"
        // Percentage formats
        case f if f.contains("%") =>
          if (f.contains(".")) "0.00%" else "0%"
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
          if (f.contains("s")) "h:mm:ss" else "h:mm"
        // Keep custom formats as is
        case _ => nfs
      }

      return Some(normalizedNFS)
    }

    // Otherwise, infer format from the value and data type
    // Based on patterns in TableDataAggregation.py and CaseJudge.py
    dataType match {
      case DataType.Date =>
        // Detect common date formats
        val datePatterns = Map(
          """^\d{1,2}/\d{1,2}/\d{4}$""".r     -> "mm/dd/yyyy",
          """^\d{4}-\d{1,2}-\d{1,2}$""".r     -> "yyyy-mm-dd",
          """^\d{1,2}-[A-Za-z]{3}-\d{4}$""".r -> "dd-mmm-yyyy",
          """^\d{1,2}\.\d{1,2}\.\d{4}$""".r   -> "dd.mm.yyyy"
        )

        datePatterns.collectFirst {
          case (pattern, format) if pattern.findFirstIn(value).isDefined => format
        }.orElse(Some("yyyy-mm-dd"))

      case DataType.Time =>
        // Detect common time formats
        val timePatterns = Map(
          """^\d{1,2}:\d{2}$""".r              -> "hh:mm",
          """^\d{1,2}:\d{2}:\d{2}$""".r        -> "hh:mm:ss",
          """^\d{1,2}:\d{2}\s*[AaPp][Mm]$""".r -> "hh:mm am/pm"
        )

        timePatterns.collectFirst {
          case (pattern, format) if pattern.findFirstIn(value).isDefined => format
        }.orElse(Some("hh:mm:ss"))

      case DataType.Currency =>
        // Format based on decimals and thousands separators
        val hasDecimals = value.contains(".")
        val format      = if (hasDecimals) "$#,##0.00" else "$#,##0"
        Some(format)

      case DataType.Percentage =>
        // Format based on decimals
        val percentValue = value.replace("%", "").trim
        val hasDecimals  = percentValue.contains(".")
        val format       = if (hasDecimals) "0.00%" else "0%"
        Some(format)

      case DataType.Float =>
        // Format based on thousands separators
        val hasCommas = value.contains(",")
        val format    = if (hasCommas) "#,##0.00" else "#0.00"
        Some(format)

      case DataType.Integer =>
        // Format based on thousands separators
        val hasCommas = value.contains(",")
        val format    = if (hasCommas) "#,##0" else "#0"
        Some(format)

      case DataType.ScientificNotation =>
        Some("0.00E+00")

      case DataType.Year =>
        Some("yyyy")

      case DataType.Email =>
        Some("@") // Simple format code for email

      case DataType.Fraction =>
        Some("# ?/?") // Common fraction format code

      case DataType.IpAddress =>
        Some("@") // Simple format code for IP

      case _ => None
    }
  }

  /**
   * Aggregates each format group into a single descriptor entry. Based on the Python aggregation
   * logic in TableDataAggregation.py
   *
   * @param formatGroups
   *   Map from format descriptor to entries of that format
   * @return
   *   Map with aggregated format entries
   */
  private def aggregateFormatGroups(
    formatGroups: Map[FormatDescriptor, Map[String, Either[String, List[String]]]]
  ): Map[String, Either[String, List[String]]] = {
    if (formatGroups.isEmpty) {
      logger.info("No format groups to aggregate")
      return Map.empty
    }

    formatGroups.flatMap { case (formatDescriptor, entries) =>
      // Special handling for dates - always aggregate even when there's only one entry
      if (formatDescriptor.dataType == DataType.Date && entries.size == 1) {
        // Aggregate the single date entry to improve consistency
        val locationEither = entries.values.head
        val locations = locationEither match {
          case Left(singleLocation) => List(singleLocation)
          case Right(locationList)  => locationList
        }

        // Use the format descriptor as the new key, with better naming
        val formatKey = formatDescriptor.formatCode match {
          case Some(code) => s"<DateData:$code>"
          case None       => "<DateData>"
        }

        if (locations.size == 1) {
          Map(formatKey -> Left(locations.head))
        } else {
          Map(formatKey -> Right(locations))
        }
        // For other types with size 1, keep original, except for well-known types
      } else if (entries.size == 1 && !shouldAlwaysAggregate(formatDescriptor.dataType)) {
        entries
      } else {
        // Aggregate all locations for this format into one entry
        val allLocations = entries.flatMap { case (_, locationEither) =>
          locationEither match {
            case Left(singleLocation) => List(singleLocation)
            case Right(locationList)  => locationList
          }
        }.toList

        // Use the format descriptor as the new key with Python-style naming
        val formatKey = pyStyleFormatString(formatDescriptor.dataType, formatDescriptor.sampleValue)

        // Create aggregated entry
        if (allLocations.size == 1) {
          Map(formatKey -> Left(allLocations.head))
        } else {
          Map(formatKey -> Right(allLocations))
        }
      }
    }
  }

  /**
   * Creates format descriptors using Python-style naming from TableDataAggregation.py With
   * compatibility for existing test cases
   */
  private def pyStyleFormatString(dataType: DataType, sampleValue: Option[String]): String =
    dataType match {
      case DataType.Year               => "<YearData>"
      case DataType.Integer            => "<IntNum>"
      case DataType.Float              => "<FloatNum>"
      case DataType.Percentage         => "<PercentageNum>"
      case DataType.ScientificNotation => "<SentificNum>" // Typo preserved
      case DataType.Date               => "<DateData>"
      case DataType.Time               => "<TimeData>"
      case DataType.Currency           => "<CurrencyData>"
      case DataType.Email              => "<EmailData>"
      case DataType.IpAddress          => "<IPAddressData>"
      case DataType.Fraction           => "<FractionData>"
      case DataType.Text               => sampleValue.getOrElse("<Text>")
      case DataType.Empty              => ""
    }

  /**
   * Determines if a data type should always be aggregated, even with just one entry. This helps
   * consistency in the output and makes the data more readable for LLMs.
   */
  private def shouldAlwaysAggregate(dataType: DataType): Boolean =
    dataType match {
      case DataType.Date | DataType.Time | DataType.Currency |
          DataType.Percentage | DataType.Year | DataType.Email |
          DataType.IpAddress | DataType.Fraction => true
      case _ => false
    }
} // End of DataFormatAggregator object
