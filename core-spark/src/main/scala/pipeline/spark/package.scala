package com.tjclp.xlcr
package pipeline

package object spark {

  /**
   * SparkPipelineStep is kept for backward compatibility with existing code.
   */
  @deprecated("core-spark will be removed in 0.3.0", "0.2.1")
  type SparkPipelineStep = SparkStep
}
