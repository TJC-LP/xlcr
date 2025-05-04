package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.SparkPipelineRegistry
import types.MimeType

object ToPng extends ConvertStep(MimeType.ImagePng) {
  SparkPipelineRegistry.register(this)
}
