package com.tjclp.xlcr
package bridges

/** Marker trait for bridge-specific configurations. */
trait BridgeConfig extends Serializable

/** Configuration specific to PDF to Image conversions. */
case class PdfToImageConfig(
  // Reusing parameters already parsed by CLI for splitting
  maxWidthPixels: Int = 2000,
  maxHeightPixels: Int = 2000,
  maxSizeBytes: Long = 1024 * 1024 * 5, // 5MB default limit
  dpi: Int = 300,
  jpegQuality: Float = 0.85f
) extends BridgeConfig

// Add other specific config case classes here as needed
// e.g., case class ExcelToOdsConfig(...) extends BridgeConfig