package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.SparkPipelineRegistry
import types.MimeType.TextPlain

object ToText extends ConvertStep(TextPlain) {
  SparkPipelineRegistry.register(this)
}
