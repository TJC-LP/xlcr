package com.tjclp.xlcr
package pipeline.spark

import scala.collection.concurrent.TrieMap

object SparkPipelineRegistry {
  private val steps = TrieMap.empty[String, SparkPipelineStep]

  def register(step: SparkPipelineStep): Unit =
    steps.update(step.name, step)

  def get(id: String): SparkPipelineStep =
    steps.getOrElse(id, throw new NoSuchElementException(s"Spark step '$id' not found"))

  def list: Seq[String] = steps.keys.toSeq.sorted
}
