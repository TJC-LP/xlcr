package com.tjclp.xlcr
package pipeline.spark

import scala.collection.concurrent.TrieMap
import org.slf4j.LoggerFactory

/**
 * Registry for ZSparkStep implementations.
 * Allows dynamic lookup of pipeline steps by name.
 */
object ZSparkStepRegistry {
  private val logger = LoggerFactory.getLogger(getClass)
  private val steps = TrieMap.empty[String, ZSparkStep]

  /**
   * Register a step with the registry.
   */
  def register(step: ZSparkStep): Unit = {
    logger.info(s"Registering ZSparkStep: ${step.name}")
    steps.update(step.name, step)
  }

  /**
   * Get a step by name, throwing an exception if not found.
   */
  def get(name: String): ZSparkStep =
    steps.getOrElse(name, throw new NoSuchElementException(s"ZSparkStep '$name' not found in registry"))

  /**
   * Get a step by name, returning None if not found.
   */
  def find(name: String): Option[ZSparkStep] = steps.get(name)

  /**
   * List all registered step names.
   */
  def list: Seq[String] = steps.keys.toSeq.sorted

  /**
   * Check if a step with the given name exists.
   */
  def exists(name: String): Boolean = steps.contains(name)

  /**
   * Register a step with an alternative name.
   */
  def registerAlias(step: ZSparkStep, alias: String): Unit = {
    logger.info(s"Registering ZSparkStep alias: $alias -> ${step.name}")
    steps.update(alias, step)
  }

  /**
   * Creates a sequential pipeline from a list of step names.
   * Each step is looked up in the registry and composed using andThen.
   */
  def pipeline(stepNames: Seq[String]): ZSparkStep = {
    if (stepNames.isEmpty) {
      ZSparkStep.identity
    } else {
      stepNames.map(get).reduceLeft((a, b) => a.andThen(b).asInstanceOf[ZSparkStep])
    }
  }

  /**
   * Register all the built-in steps.
   * This ensures that all standard steps are available in the registry.
   */
  def registerAll(): Unit = {
    logger.info("Registering all standard ZSparkSteps...")
    
    // We will import each object when they're defined
    // This way we don't have to rely on static imports that might not be available yet
    
    // Count the registered steps
    val initCount = steps.size
    
    // Register steps as they're initialized
    logger.info(s"Registered ${steps.size - initCount} ZSparkSteps")
  }
}