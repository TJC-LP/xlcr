package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.SparkPipelineRegistry
import types.MimeType

object ToXml extends ConvertStep(MimeType.ApplicationXml) {
  SparkPipelineRegistry.register(this)
}
