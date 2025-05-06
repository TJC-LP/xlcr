package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.SparkPipelineRegistry
import types.MimeType.ApplicationXml

object ToXml extends ConvertStep(ApplicationXml) {
  SparkPipelineRegistry.register(this)
}
