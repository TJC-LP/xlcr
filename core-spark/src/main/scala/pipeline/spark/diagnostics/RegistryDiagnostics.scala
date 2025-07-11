package com.tjclp.xlcr
package pipeline.spark.diagnostics

import java.util.ServiceLoader

import scala.jdk.CollectionConverters._

import org.slf4j.LoggerFactory

import bridges.BridgeRegistry
import spi.{ BridgeProvider, SplitterProvider }
import splitters.SplitterRegistry
import types.MimeType

/**
 * Diagnostic utilities for debugging ServiceLoader and registry issues in Spark environments.
 * 
 * Usage in your Spark job:
 * {{{
 * import com.tjclp.xlcr.pipeline.spark.diagnostics.RegistryDiagnostics
 * 
 * // At the start of your Spark job
 * RegistryDiagnostics.logRegistrationStatus()
 * 
 * // To check specific conversions
 * RegistryDiagnostics.logBridgeSupport(
 *   MimeType.ApplicationVndMsExcel, 
 *   MimeType.ApplicationPdf
 * )
 * }}}
 */
object RegistryDiagnostics {
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  /**
   * Logs comprehensive information about ServiceLoader discovery and registry state.
   * This helps diagnose issues where Aspose providers aren't being found.
   */
  def logRegistrationStatus(): Unit = {
    logger.info("=== ServiceLoader Provider Discovery ===")
    
    // Check BridgeProviders
    val bridgeProviders = ServiceLoader.load(classOf[BridgeProvider]).asScala.toList
    logger.info(s"Found ${bridgeProviders.size} BridgeProvider(s):")
    bridgeProviders.foreach { provider =>
      logger.info(s"  - ${provider.getClass.getName}")
      try {
        val bridges = provider.getBridges
        logger.info(s"    Provides ${bridges.size} bridge(s)")
      } catch {
        case e: Exception =>
          logger.error(s"    ERROR loading bridges: ${e.getMessage}")
      }
    }
    
    // Check SplitterProviders
    val splitterProviders = ServiceLoader.load(classOf[SplitterProvider]).asScala.toList
    logger.info(s"Found ${splitterProviders.size} SplitterProvider(s):")
    splitterProviders.foreach { provider =>
      logger.info(s"  - ${provider.getClass.getName}")
      try {
        val splitters = provider.getSplitters
        logger.info(s"    Provides ${splitters.size} splitter(s)")
      } catch {
        case e: Exception =>
          logger.error(s"    ERROR loading splitters: ${e.getMessage}")
      }
    }
    
    // Force registry initialization
    logger.info("=== Registry Initialization ===")
    BridgeRegistry.init()
    SplitterRegistry.init()
    
    // Log registry contents summary
    val bridges = BridgeRegistry.listBridges()
    logger.info(s"BridgeRegistry contains ${bridges.size} bridge(s)")
    
    val splitters = SplitterRegistry.listSplitters()
    logger.info(s"SplitterRegistry contains ${splitters.size} splitter(s)")
    
    // Check for Aspose components specifically
    val asposeBridges = bridges.filter(_._3.contains("Aspose"))
    logger.info(s"Found ${asposeBridges.size} Aspose bridge(s)")
    
    if (asposeBridges.isEmpty) {
      logger.warn("WARNING: No Aspose bridges found! Check that:")
      logger.warn("  1. core-aspose JAR is in the classpath")
      logger.warn("  2. META-INF/services files are properly merged in assembly")
      logger.warn("  3. AsposeRegistrations class is loadable")
    }
  }
  
  /**
   * Logs information about available bridges for a specific conversion.
   */
  def logBridgeSupport(fromMime: MimeType, toMime: MimeType): Unit = {
    logger.info(s"=== Bridge Support: ${fromMime.mimeType} -> ${toMime.mimeType} ===")
    
    val bridges = BridgeRegistry.findAllBridges(fromMime, toMime)
    if (bridges.isEmpty) {
      logger.error(s"No bridges found for conversion!")
      logger.info("Available conversions from this type:")
      BridgeRegistry.listBridges()
        .filter(_._1.mimeType == fromMime.mimeType)
        .foreach { case (from, to, impl, priority) =>
          logger.info(s"  ${from.mimeType} -> ${to.mimeType} via $impl (priority: $priority)")
        }
    } else {
      logger.info(s"Found ${bridges.size} bridge(s):")
      bridges.foreach { bridge =>
        logger.info(s"  - ${bridge.getClass.getName} (priority: ${bridge.priority})")
      }
    }
  }
  
  /**
   * Returns a diagnostic report as a string (useful for including in error messages).
   */
  def getDiagnosticReport(): String = {
    val sb = new StringBuilder
    
    sb.append("=== XLCR Registry Diagnostic Report ===\n")
    
    // ServiceLoader status
    val bridgeProviders = ServiceLoader.load(classOf[BridgeProvider]).asScala.toList
    val splitterProviders = ServiceLoader.load(classOf[SplitterProvider]).asScala.toList
    
    sb.append(s"ServiceLoader found: ${bridgeProviders.size} BridgeProvider(s), ${splitterProviders.size} SplitterProvider(s)\n")
    sb.append(s"Providers: ${bridgeProviders.map(_.getClass.getSimpleName).mkString(", ")}\n")
    
    // Registry status
    val bridges = BridgeRegistry.listBridges()
    val asposeBridges = bridges.filter(_._3.contains("Aspose"))
    
    sb.append(s"Registry contains: ${bridges.size} total bridges, ${asposeBridges.size} Aspose bridges\n")
    
    if (asposeBridges.isEmpty) {
      sb.append("WARNING: No Aspose bridges registered!\n")
    }
    
    sb.toString
  }
}