package com.tjclp.xlcr

import scopt.OParser

import cli.{ AbstractMain, CommonCLI }
import cli.CommonCLI.BaseConfig

/**
 * Main entry point for the core XLCR application. Extends AbstractMain to leverage common CLI
 * parsing and execution logic.
 */
object Main extends AbstractMain[BaseConfig] {

  override protected def programName: String     = "xlcr"
  override protected def programVersion: String  = "0.1.0-RC14"
  override protected def emptyConfig: BaseConfig = BaseConfig()

  // Getter methods to extract fields from BaseConfig
  override protected def getInput(config: BaseConfig): String                 = config.input
  override protected def getOutput(config: BaseConfig): String                = config.output
  override protected def getDiffMode(config: BaseConfig): Boolean             = config.diffMode
  override protected def getSplitMode(config: BaseConfig): Boolean            = config.splitMode
  override protected def getSplitStrategy(config: BaseConfig): Option[String] = config.splitStrategy
  override protected def getOutputType(config: BaseConfig): Option[String]    = config.outputType
  override protected def getMappings(config: BaseConfig): Seq[String]         = config.mappings
  override protected def getFailureMode(config: BaseConfig): Option[String]   = config.failureMode
  override protected def getChunkRange(config: BaseConfig): Option[String]    = config.chunkRange
  override protected def getThreads(config: BaseConfig): Int                  = config.threads
  override protected def getErrorMode(config: BaseConfig): Option[String]     = config.errorMode
  override protected def getEnableProgress(config: BaseConfig): Boolean  = config.enableProgress
  override protected def getProgressIntervalMs(config: BaseConfig): Long = config.progressIntervalMs
  override protected def getVerbose(config: BaseConfig): Boolean         = config.verbose
  override protected def getBackend(config: BaseConfig): Option[String]  = config.backend

  /**
   * Builds all CLI options using CommonCLI utilities
   */
  override protected def buildAllOptions: OParser[_, BaseConfig] =
    CommonCLI.baseParser(programName, programVersion)

}
