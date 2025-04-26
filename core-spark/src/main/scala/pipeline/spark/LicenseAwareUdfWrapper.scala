package com.tjclp.xlcr
package pipeline.spark

import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.{SparkSession, functions => F}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{Duration => ScalaDuration}
import scala.reflect.runtime.universe.TypeTag

/** Provides license-aware UDF functions that automatically handle
  * Aspose license initialization on Spark workers.
  */
trait LicenseAwareUdfWrapper {

  private val logger = LoggerFactory.getLogger(getClass)

  import UdfHelpers._

  /** Creates a license-aware UDF that ensures Aspose licenses are initialized
    * on each worker before execution.
    *
    * @param name The name of the UDF (for lineage tracking)
    * @param timeout Timeout for the UDF execution
    * @param implementation Optional implementation info
    * @param params Optional parameters for lineage
    * @param spark SparkSession (implicit)
    * @param f The function to wrap
    * @return A license-aware UserDefinedFunction
    */
  def licenseAwareUdf[A: TypeTag, R: TypeTag](
      name: String,
      timeout: ScalaDuration = ScalaDuration.Inf,
      implementation: Option[String] = None,
      params: Option[Map[String, String]] = None
  )(
      f: A => (R, Option[String], Option[Map[String, String]])
  )(implicit spark: SparkSession): UserDefinedFunction = {

    // Initialize broadcast on the driver
    AsposeBroadcastManager.initBroadcast(spark)

    // Get license status for metadata
    val licenseStatus = AsposeBroadcastManager.getLicenseStatus

    // Create a UDF that ensures licenses are initialized on each worker
    wrapUdf(name, timeout, implementation, params) { a: A =>
      // Initialize licenses on this worker (thread-safe, once per JVM)
      AsposeBroadcastManager.ensureInitialized()

      // Execute the function
      val (result, implName, implParams) = f(a)

      // Merge license status with implementation params if using Aspose
      val finalParams = implName match {
        case Some(impl) if impl.toLowerCase.contains("aspose") =>
          implParams.map(_ ++ licenseStatus).orElse(Some(licenseStatus))
        case _ => implParams
      }

      (result, implName, finalParams)
    }
  }

  /** Two-argument variant of licenseAwareUdf
    */
  def licenseAwareUdf2[A: TypeTag, B: TypeTag, R: TypeTag](
      name: String,
      timeout: ScalaDuration = ScalaDuration.Inf,
      implementation: Option[String] = None,
      params: Option[Map[String, String]] = None
  )(
      f: (A, B) => (R, Option[String], Option[Map[String, String]])
  )(implicit spark: SparkSession): UserDefinedFunction = {

    // Initialize broadcast on the driver
    AsposeBroadcastManager.initBroadcast(spark)

    // Get license status for metadata
    val licenseStatus = AsposeBroadcastManager.getLicenseStatus

    // Create a UDF that ensures licenses are initialized on each worker
    wrapUdf2(name, timeout, implementation, params) { (a: A, b: B) =>
      // Initialize licenses on this worker (thread-safe, once per JVM)
      AsposeBroadcastManager.ensureInitialized()

      // Execute the function
      val (result, implName, implParams) = f(a, b)

      // Merge license status with implementation params if using Aspose
      val finalParams = implName match {
        case Some(impl) if impl.toLowerCase.contains("aspose") =>
          implParams.map(_ ++ licenseStatus).orElse(Some(licenseStatus))
        case _ => implParams
      }

      (result, implName, finalParams)
    }
  }

  /** For compatibility with existing code - auto-wraps standard functions
    */
  def licenseAwareUdf[A: TypeTag, R: TypeTag](
      name: String,
      timeout: ScalaDuration,
      f: A => R
  )(implicit spark: SparkSession): UserDefinedFunction = {
    val wrappedF = (a: A) =>
      (f(a), None: Option[String], None: Option[Map[String, String]])
    licenseAwareUdf(name, timeout, None, None)(wrappedF)
  }

  /** For compatibility with existing code - auto-wraps standard functions
    */
  def licenseAwareUdf2[A: TypeTag, B: TypeTag, R: TypeTag](
      name: String,
      timeout: ScalaDuration,
      f: (A, B) => R
  )(implicit spark: SparkSession): UserDefinedFunction = {
    val wrappedF = (a: A, b: B) =>
      (f(a, b), None: Option[String], None: Option[Map[String, String]])
    licenseAwareUdf2(name, timeout, None, None)(wrappedF)
  }
}
