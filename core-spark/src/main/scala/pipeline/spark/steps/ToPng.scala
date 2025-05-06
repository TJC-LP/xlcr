package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.SparkPipelineRegistry
import types.MimeType.ImagePng

object ToPng extends ConvertStep(ImagePng) {
  SparkPipelineRegistry.register(this)
}
