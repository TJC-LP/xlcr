package com.tjclp.xlcr
package pipeline.spark.steps

import pipeline.spark.SparkStep
import org.apache.spark.sql.{DataFrame, SparkSession}

/**
 * Utility functions for creating and composing SparkSteps.
 * This replaces the now-deprecated SparkPipelineRegistry.
 */
object SparkStepUtils {
  
  /**
   * Build a pipeline of multiple steps.
   * This is a convenience method that composes multiple steps together.
   * 
   * @param steps The steps to compose in order
   * @return A single SparkStep that runs all the given steps in sequence
   */
  def buildPipeline(steps: SparkStep*): SparkStep = {
    require(steps.nonEmpty, "Cannot build a pipeline with no steps")
    
    steps.reduce((a, b) => a.andThen(b))
  }
  
  /**
   * Run a pipeline on a DataFrame.
   * This is a convenience method that ensures initialization before running.
   * 
   * @param df The input DataFrame
   * @param step The step or pipeline to run
   * @param spark The SparkSession (implicit)
   * @return The transformed DataFrame
   */
  def runPipeline(df: DataFrame, step: SparkStep)(implicit spark: SparkSession): DataFrame = {
    // Run the pipeline
    step.apply(df)
  }
}