package com.tjclp.xlcr
package bridges

import parsers.ParserConfig
import renderers.RendererConfig

/**
 * Utilities for converting between config types. This helps with translating BridgeConfig to the
 * appropriate ParserConfig and RendererConfig.
 */
object ConfigConversion {

  /** Represents the capability to convert from a BridgeConfig to a ParserConfig */
  trait BridgeToParserConfig[B <: BridgeConfig] {

    /**
     * Convert a bridge config to a parser config
     *
     * @param config
     *   The bridge config to convert
     * @return
     *   A parser config derived from the bridge config
     */
    def toParserConfig(config: B): ParserConfig
  }

  /** Represents the capability to convert from a BridgeConfig to a RendererConfig */
  trait BridgeToRendererConfig[B <: BridgeConfig] {

    /**
     * Convert a bridge config to a renderer config
     *
     * @param config
     *   The bridge config to convert
     * @return
     *   A renderer config derived from the bridge config
     */
    def toRendererConfig(config: B): RendererConfig
  }

  /** Represents a config class that can be transformed into both parser and renderer configs */
  trait DualConverter[B <: BridgeConfig]
      extends BridgeToParserConfig[B]
      with BridgeToRendererConfig[B]

  /** Implicit class to add conversion methods to BridgeConfig */
  implicit class BridgeConfigOps[B <: BridgeConfig](config: B) {

    /**
     * Convert to ParserConfig if there is an implicit converter available
     *
     * @param converter
     *   The implicit converter from BridgeConfig to ParserConfig
     * @return
     *   The converted ParserConfig
     */
    def asParserConfig(implicit converter: BridgeToParserConfig[B]): ParserConfig =
      converter.toParserConfig(config)

    /**
     * Convert to RendererConfig if there is an implicit converter available
     *
     * @param converter
     *   The implicit converter from BridgeConfig to RendererConfig
     * @return
     *   The converted RendererConfig
     */
    def asRendererConfig(implicit converter: BridgeToRendererConfig[B]): RendererConfig =
      converter.toRendererConfig(config)
  }

  /**
   * Convert an Option[BridgeConfig] to an Option[ParserConfig] if there is an implicit converter
   *
   * @param configOpt
   *   The optional bridge config to convert
   * @param tagger
   *   A ClassTag for B to help with type inference
   * @param converter
   *   The implicit converter from BridgeConfig to ParserConfig
   * @tparam B
   *   The specific BridgeConfig type
   * @return
   *   An Option containing the converted ParserConfig, or None if the input was None or could not
   *   be cast to type B
   */
  def toParserConfig[B <: BridgeConfig](
    configOpt: Option[BridgeConfig]
  )(implicit
    tagger: scala.reflect.ClassTag[B],
    converter: BridgeToParserConfig[B]
  ): Option[ParserConfig] =
    configOpt.collect {
      case config: B => converter.toParserConfig(config)
    }

  /**
   * Convert an Option[BridgeConfig] to an Option[RendererConfig] if there is an implicit converter
   *
   * @param configOpt
   *   The optional bridge config to convert
   * @param tagger
   *   A ClassTag for B to help with type inference
   * @param converter
   *   The implicit converter from BridgeConfig to RendererConfig
   * @tparam B
   *   The specific BridgeConfig type
   * @return
   *   An Option containing the converted RendererConfig, or None if the input was None or could not
   *   be cast to type B
   */
  def toRendererConfig[B <: BridgeConfig](
    configOpt: Option[BridgeConfig]
  )(implicit
    tagger: scala.reflect.ClassTag[B],
    converter: BridgeToRendererConfig[B]
  ): Option[RendererConfig] =
    configOpt.collect {
      case config: B => converter.toRendererConfig(config)
    }

  /**
   * Helper method to extract parser and renderer configs from a bridge config
   *
   * This method is convenient for bridge implementations that need to convert a bridge config to
   * both parser and renderer configs.
   *
   * @param configOpt
   *   The optional bridge config to convert
   * @param tagger
   *   A ClassTag for B to help with type inference
   * @param converter
   *   The dual converter that can transform to both parser and renderer configs
   * @tparam B
   *   The specific BridgeConfig type
   * @return
   *   A tuple of (Option[ParserConfig], Option[RendererConfig])
   */
  def extractConfigs[B <: BridgeConfig](
    configOpt: Option[BridgeConfig]
  )(implicit
    tagger: scala.reflect.ClassTag[B],
    converter: DualConverter[B]
  ): (Option[ParserConfig], Option[RendererConfig]) = {
    val parserConfig   = toParserConfig[B](configOpt)
    val rendererConfig = toRendererConfig[B](configOpt)
    (parserConfig, rendererConfig)
  }
}
