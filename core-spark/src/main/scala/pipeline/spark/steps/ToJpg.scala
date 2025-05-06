package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.SparkPipelineRegistry
import types.MimeType.ImageJpeg

object ToJpg extends ConvertStep(ImageJpeg) {
  SparkPipelineRegistry.register(this)
}
