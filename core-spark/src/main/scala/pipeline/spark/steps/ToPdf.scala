package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.SparkPipelineRegistry
import types.MimeType

// Convenience singletons for common conversions
object ToPdf extends ConvertStep(MimeType.ApplicationPdf) {
  SparkPipelineRegistry.register(this)
}
