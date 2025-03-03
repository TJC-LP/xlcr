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
      val dataType = 
        if cell.isEmpty then DataType.Empty
        else if cell.isDate then DataType.Date
        else if cell.isNumeric then
          if cell.value.contains(".") then DataType.Float
          else DataType.Integer
        else DataType.Text
      
      (cell.value, dataType)
    }.toMap
  
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
   *
   * @param candidates Content map entries that are candidates for aggregation
   * @param typeMap Map from cell value to data type
   * @return Map from format descriptor to the entries of that format
   */
  private def groupByFormat(
    candidates: Map[String, Either[String, List[String]]],
    typeMap: Map[String, DataType]
  ): Map[FormatDescriptor, Map[String, Either[String, List[String]]]] =
    // Group by inferred data type
    candidates.groupBy { case (content, _) =>
      val dataType = typeMap.getOrElse(content, DataType.Text)
      
      // Create a format descriptor without format code (could be enhanced later)
      FormatDescriptor(dataType, None, Some(content))
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
   * (Future improvement) Detects format code from a cell value.
   * This would enhance the format detection beyond simple type inference.
   */
  private def inferFormatCode(value: String, dataType: DataType): Option[String] =
    // Future: Implement more sophisticated format code detection
    dataType match
      case DataType.Date => Some("yyyy-mm-dd")
      case DataType.Time => Some("hh:mm:ss")
      case DataType.Currency => Some("$#,##0.00")
      case DataType.Percentage => Some("0.00%")
      case DataType.Float => Some("#,##0.00")
      case DataType.Integer => Some("#,##0")
      case _ => None