package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.SparkPipelineRegistry
import types.MimeType.ApplicationPdf

// Convenience singletons for common conversions
object ToPdf extends ConvertStep(ApplicationPdf) {
  SparkPipelineRegistry.register(this)
}
