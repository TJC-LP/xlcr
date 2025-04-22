package com.tjclp.xlcr.pipeline

package object spark {
  /** Alias kept for transitional compatibility while migrating classic
    * Future‑based steps to the unified ZIO implementation.
    */
  type SparkPipelineStep = ZSparkStep
}
