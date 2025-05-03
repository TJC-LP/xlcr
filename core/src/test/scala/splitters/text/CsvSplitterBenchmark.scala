package com.tjclp.xlcr
package splitters
package text

import models.FileContent
import types.MimeType

/** Simple benchmark for CSV splitting performance.
  * Not a unit test - meant to be run manually for profiling.
  */
object CsvSplitterBenchmark {

  def main(args: Array[String]): Unit = {
    // Generate a large CSV file in memory
    val header = "col1,col2,col3,col4,col5,col6,col7,col8,col9,col10"
    val rowTemplate =
      "value1,value2,value3,value4,value5,value6,value7,value8,value9,value10"

    // First test with a small file (100 rows)
    benchmarkWithSize(100, header, rowTemplate)

    // Then with a medium file (1,000 rows)
    benchmarkWithSize(1000, header, rowTemplate)

    // Then with a large file (10,000 rows)
    benchmarkWithSize(10000, header, rowTemplate)

    // Finally with a very large file (100,000 rows)
    benchmarkWithSize(100000, header, rowTemplate)
  }

  def benchmarkWithSize(
      rowCount: Int,
      header: String,
      rowTemplate: String
  ): Unit = {
    println(s"\n===== Benchmarking with $rowCount rows =====")

    // Generate CSV content
    val sb = new StringBuilder()
    sb.append(header).append("\n")
    for (i <- 1 to rowCount) {
      sb.append(rowTemplate.replace("value1", s"row$i")).append("\n")
    }
    val csvContent = sb.toString()
    val csvBytes = csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)

    // Create file content and configs
    val content = FileContent(csvBytes, MimeType.TextCsv)
      .asInstanceOf[FileContent[MimeType.TextCsv.type]]
    val rowConfig = SplitConfig(strategy = Some(SplitStrategy.Row))
    val chunkConfig = SplitConfig(maxChars = 1000) // 1000 rows per chunk

    // Create splitter
    val splitter = CsvSplitter

    // Measure chunk splitting performance
    println("Testing splitIntoChunks...")
    val chunkStart = System.currentTimeMillis()
    val chunks = splitter.split(content, chunkConfig)
    val chunkEnd = System.currentTimeMillis()
    println(s"Generated ${chunks.length} chunks in ${chunkEnd - chunkStart}ms")

    // Measure row splitting performance
    println("Testing splitByRows...")
    val rowStart = System.currentTimeMillis()
    val rows = splitter.split(content, rowConfig)
    val rowEnd = System.currentTimeMillis()
    println(s"Generated ${rows.length} row chunks in ${rowEnd - rowStart}ms")

    // Calculate performance ratio
    val ratio = (rowEnd - rowStart).toDouble / (chunkEnd - chunkStart).max(1)
    println(s"Row splitting is ${ratio.round}x slower than chunk splitting")
  }
}
