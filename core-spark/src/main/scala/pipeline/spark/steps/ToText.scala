package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.SparkPipelineRegistry
import types.MimeType

object ToText extends ConvertStep(MimeType.TextPlain) {
  SparkPipelineRegistry.register(this)
}
