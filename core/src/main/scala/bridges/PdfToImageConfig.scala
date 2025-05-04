package com.tjclp.xlcr
package bridges

/** Configuration specific to PDF to Image conversions. */
case class PdfToImageConfig(
    // Reusing parameters already parsed by CLI for splitting
    maxWidthPixels: Int = 2000,
    maxHeightPixels: Int = 2000,
    maxSizeBytes: Long = 1024 * 1024 * 5, // 5MB default limit
    dpi: Int = 300,
    jpegQuality: Float = 0.85f
) extends BridgeConfig
