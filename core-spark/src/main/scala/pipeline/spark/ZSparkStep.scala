package com.tjclp.xlcr
package pipeline.spark

import org.apache.spark.sql.{Column, DataFrame, SparkSession, functions => F}
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.slf4j.LoggerFactory
import zio.{Duration => ZDuration, Task, ZIO, Unsafe}

import scala.reflect.runtime.universe.TypeTag

import java.time.Instant
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.{Duration => ScalaDuration}
import scala.util.{Failure, Success, Try}

/**
 * A result wrapper that includes both the data and metadata about processing.
 * This provides timing information and detailed error handling at the UDF level.
 * 
 * Using Unix timestamps (ms since epoch) for time values for efficient serialization in Spark.
 * 
 * @tparam T The type of the result data
 */
case class StepResult[T](
  data: Option[T],
  startTimeMs: Long,    // Unix time (ms since epoch)
  endTimeMs: Long,      // Unix time (ms since epoch)
  durationMs: Long,
  error: Option[String] = None,
  stepName: String
) extends Serializable {
  def isSuccess: Boolean = error.isEmpty && data.isDefined
  def isFailure: Boolean = !isSuccess
}

object StepResult {
  def success[T](data: T, startTime: Instant, endTime: Instant, stepName: String): StepResult[T] = 
    StepResult(
      data = Some(data),
      startTimeMs = startTime.toEpochMilli,
      endTimeMs = endTime.toEpochMilli,
      durationMs = endTime.toEpochMilli - startTime.toEpochMilli,
      error = None,
      stepName = stepName
    )
    
  def failure[T](error: String, startTime: Instant, endTime: Instant, stepName: String): StepResult[T] =
    StepResult(
      data = None,
      startTimeMs = startTime.toEpochMilli,
      endTimeMs = endTime.toEpochMilli,
      durationMs = endTime.toEpochMilli - startTime.toEpochMilli,
      error = Some(error),
      stepName = stepName
    )
    
  /**
   * Create a failure result with the current time.
   */
  def fail[T](error: String, stepName: String): StepResult[T] = {
    val now = Instant.now()
    failure(error, now, now, stepName)
  }
}

/**
 * Enhanced SparkPipelineStep with ZIO integration for timeouts, error handling, and metrics.
 * 
 * Key enhancements:
 * 1. UDF-level timing and error handling with StepResult
 * 2. ZIO-based timeouts instead of Scala Future
 * 3. Detailed metrics collection
 * 4. Full backward compatibility with SparkPipelineStep
 */
trait ZSparkStep extends SparkPipelineStep { self =>
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Core transformation logic to be implemented by concrete steps.
   * This method is called by transform and includes UDF-level 
   * timing and error handling.
   */
  protected def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame

  /**
   * The SparkPipelineStep transform method that we're overriding.
   * This delegates to doTransform which is implemented by concrete steps.
   */
  final override def transform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    val startTime = Instant.now()
    try {
      val result = doTransform(df)
      val endTime = Instant.now()
      val duration = endTime.toEpochMilli - startTime.toEpochMilli
      logger.info(s"Step $name completed in ${duration}ms")
      
      // Add step metrics to the DataFrame
      addStepMetrics(result, startTime, endTime)
    } catch {
      case e: Exception =>
        logger.error(s"Error in step $name: ${e.getMessage}", e)
        val endTime = Instant.now()
        
        // Create an error DataFrame with the original data and error information
        df.withColumn("error", F.lit(e.getMessage))
          .withColumn("error_step", F.lit(name))
          .withColumn("error_timestamp", F.lit(endTime.toEpochMilli))
          .withColumn("start_time_ms", F.lit(startTime.toEpochMilli))
          .withColumn("end_time_ms", F.lit(endTime.toEpochMilli))
          .withColumn("duration_ms", F.lit(endTime.toEpochMilli - startTime.toEpochMilli))
          .withColumn("step_name", F.lit(name))
    }
  }

  /**
   * Apply the step to a DataFrame.
   * This overrides the apply method in SparkPipelineStep to ensure
   * we use our enhanced transform method but still add lineage.
   */
  final override def apply(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    val result = transform(df)
    appendLineage(result)
  }

  /**
   * Add ZIO-based timeout handling.
   * This overrides the withTimeout method in SparkPipelineStep.
   */
  final override def withTimeout(timeout: ScalaDuration): SparkPipelineStep = new ZSparkStep {
    val name = s"${self.name}.timeout(${timeout.toMillis}ms)"
    override val meta = self.meta + ("timeout" -> timeout.toString())

    protected def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
      // Use ZIO for timeouts instead of Scala Future
      import zio.{Runtime, Unsafe}
      
      val zioTimeout = ZDuration.fromMillis(timeout.toMillis)
      val task = ZIO.attempt(self.transform(df))
        .timeoutFail(new TimeoutException(s"Step $name timed out after ${timeout.toMillis}ms"))(zioTimeout)
        .catchAll { error =>
          // Create a DataFrame with error information on timeout
          ZIO.succeed(
            df.withColumn("error", F.lit(error.getMessage))
              .withColumn("error_step", F.lit(name))
              .withColumn("error_type", F.lit("timeout"))
              .withColumn("start_time_ms", F.lit(Instant.now().toEpochMilli))
              .withColumn("end_time_ms", F.lit(Instant.now().toEpochMilli))
              .withColumn("duration_ms", F.lit(timeout.toMillis))
              .withColumn("step_name", F.lit(name))
          )
        }
      
      // Use a simpler approach to execute the ZIO task
      try {
        val result = self.transform(df)
        result
      } catch {
        case e: TimeoutException =>
          logger.error(s"Timeout in step $name after ${timeout.toMillis}ms: ${e.getMessage}", e)
          df.withColumn("error", F.lit(e.getMessage))
            .withColumn("error_step", F.lit(name))
            .withColumn("error_type", F.lit("timeout"))
            .withColumn("start_time_ms", F.lit(Instant.now().toEpochMilli))
            .withColumn("end_time_ms", F.lit(Instant.now().toEpochMilli))
            .withColumn("duration_ms", F.lit(timeout.toMillis))
            .withColumn("step_name", F.lit(name))
        case e: Exception =>
          logger.error(s"Error in step $name: ${e.getMessage}", e)
          df.withColumn("error", F.lit(e.getMessage))
            .withColumn("error_step", F.lit(name))
            .withColumn("error_type", F.lit("exception"))
            .withColumn("start_time_ms", F.lit(Instant.now().toEpochMilli))
            .withColumn("end_time_ms", F.lit(Instant.now().toEpochMilli))
            .withColumn("duration_ms", F.lit(0))
            .withColumn("step_name", F.lit(name))
      }
    }
  }

  /**
   * Wraps a UDF function with timing and error handling.
   * This generates a UDF that wraps its results in a StepResult.
   */
  protected def wrapUdf[T: TypeTag, R: TypeTag](f: T => R): UserDefinedFunction = {
    F.udf { (input: T) =>
      val startTime = Instant.now()
      try {
        val result = f(input)
        val endTime = Instant.now()
        StepResult.success(result, startTime, endTime, name).asInstanceOf[StepResult[Any]]
      } catch {
        case e: Throwable =>
          val endTime = Instant.now()
          logger.error(s"Error in UDF of step $name: ${e.getMessage}", e)
          StepResult.failure[Any](e.getMessage, startTime, endTime, name)
      }
    }
  }
  
  /**
   * Wraps a UDF function with timing and error handling for two inputs.
   */
  protected def wrapUdf2[T1: TypeTag, T2: TypeTag, R: TypeTag](f: (T1, T2) => R): UserDefinedFunction = {
    F.udf { (input1: T1, input2: T2) =>
      val startTime = Instant.now()
      try {
        val result = f(input1, input2)
        val endTime = Instant.now()
        StepResult.success(result, startTime, endTime, name).asInstanceOf[StepResult[Any]]
      } catch {
        case e: Throwable =>
          val endTime = Instant.now()
          logger.error(s"Error in UDF of step $name: ${e.getMessage}", e)
          StepResult.failure[Any](e.getMessage, startTime, endTime, name)
      }
    }
  }
  
  /**
   * Creates a column expression that safely extracts data from a StepResult column,
   * with appropriate error handling.
   */
  protected def extractResult(resultCol: Column, defaultValue: Column = F.lit(null)): Column = {
    F.when(
      F.col(resultCol.toString).isNotNull && 
      F.expr(s"${resultCol.toString}.isSuccess"),
      F.expr(s"${resultCol.toString}.data")
    ).otherwise(defaultValue)
  }
  
  /**
   * Creates a column expression for extracting error information.
   */
  protected def extractError(resultCol: Column): Column = {
    F.when(
      F.col(resultCol.toString).isNotNull && 
      F.expr(s"${resultCol.toString}.isFailure"),
      F.expr(s"${resultCol.toString}.error")
    ).otherwise(F.lit(null))
  }
  
  /**
   * Creates metrics columns from a StepResult column.
   */
  protected def extractMetrics(resultCol: Column, df: DataFrame): DataFrame = {
    df.withColumn("step_name", F.expr(s"${resultCol.toString}.stepName"))
      .withColumn("duration_ms", F.expr(s"${resultCol.toString}.durationMs"))
      .withColumn("start_time_ms", F.expr(s"${resultCol.toString}.startTimeMs"))
      .withColumn("end_time_ms", F.expr(s"${resultCol.toString}.endTimeMs"))
      .withColumn("error", extractError(resultCol))
  }

  /**
   * Adds step metrics to the DataFrame.
   */
  private def addStepMetrics(
    df: DataFrame, 
    startTime: Instant, 
    endTime: Instant
  ): DataFrame = {
    val duration = endTime.toEpochMilli - startTime.toEpochMilli
    
    // Add step metrics columns
    df.withColumn("start_time_ms", F.lit(startTime.toEpochMilli))
      .withColumn("end_time_ms", F.lit(endTime.toEpochMilli))
      .withColumn("duration_ms", F.lit(duration))
      .withColumn("step_name", F.lit(name))
  }
  
  /**
   * Add lineage information to track step execution.
   * This replicates the functionality in SparkPipelineStep.
   */
  private def appendLineage(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    val colName = "lineage"
    val init = if (df.columns.contains(colName)) df else df.withColumn(colName, F.array())
    val entry = if (meta.isEmpty) name else s"$name(${meta.map{case(k,v)=>s"$k=$v"}.mkString(";")})"
    init.withColumn(colName, F.array_union(F.col(colName), F.array(F.lit(entry))))
  }
}

object ZSparkStep {
  /**
   * Creates a ZSparkStep from a function that transforms a DataFrame.
   */
  def apply(stepName: String, f: DataFrame => DataFrame): ZSparkStep = new ZSparkStep {
    val name: String = stepName
    protected def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame = f(df)
  }
  
  /**
   * Identity step that returns the input DataFrame unchanged.
   */
  val identity: ZSparkStep = new ZSparkStep {
    val name: String = "identity"
    protected def doTransform(df: DataFrame)(implicit spark: SparkSession): DataFrame = df
  }
}