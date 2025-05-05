package com.tjclp.xlcr
package utils.image

import org.slf4j.LoggerFactory

import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Utility functions for image processing across both Aspose and non-Aspose implementations.
 */
object ImageUtils {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Calculate optimal dimensions that fit within max bounds while preserving aspect ratio.
   *
   * @param originalWidth
   *   Original width in points/pixels
   * @param originalHeight
   *   Original height in points/pixels
   * @param maxWidth
   *   Maximum allowed width
   * @param maxHeight
   *   Maximum allowed height
   * @param logScaling
   *   Whether to log scaling operations (default: true)
   * @return
   *   Tuple of (width, height) respecting max dimensions and aspect ratio
   */
  def calculateOptimalDimensions(
    originalWidth: Int,
    originalHeight: Int,
    maxWidth: Int,
    maxHeight: Int,
    logScaling: Boolean = true
  ): (Int, Int) = {
    // If dimensions are already smaller than max, keep them as is
    if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
      return (originalWidth, originalHeight)
    }

    // Calculate scale factor to fit within bounds while maintaining aspect ratio
    val widthScale  = maxWidth.toDouble / originalWidth
    val heightScale = maxHeight.toDouble / originalHeight
    val scale       = Math.min(widthScale, heightScale).min(1)

    val newWidth  = Math.max(1, Math.round(originalWidth * scale).toInt)
    val newHeight = Math.max(1, Math.round(originalHeight * scale).toInt)

    if (logScaling) {
      logger.debug(
        s"Scaling image from ${originalWidth}x${originalHeight} to ${newWidth}x${newHeight}"
      )
    }

    (newWidth, newHeight)
  }

  /**
   * Scale an image to fit within maximum dimensions while preserving aspect ratio. Uses bilinear
   * interpolation for better quality results.
   *
   * @param image
   *   The source image to resize
   * @param maxWidth
   *   Maximum allowed width
   * @param maxHeight
   *   Maximum allowed height
   * @return
   *   The resized image, or the original if no resizing was needed
   */
  def resizeImage(
    image: BufferedImage,
    maxWidth: Int,
    maxHeight: Int
  ): BufferedImage = {
    val originalWidth  = image.getWidth
    val originalHeight = image.getHeight

    // Calculate optimal dimensions
    val (newWidth, newHeight) = calculateOptimalDimensions(
      originalWidth,
      originalHeight,
      maxWidth,
      maxHeight
    )

    // If no resizing needed, return the original
    if (newWidth == originalWidth && newHeight == originalHeight) {
      return image
    }

    // Create a new BufferedImage with the target dimensions
    val resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)

    // Get graphics context and set rendering hints for better quality
    val g = resizedImage.createGraphics()

    // Set bilinear interpolation for smoother scaling
    g.setRenderingHint(
      RenderingHints.KEY_INTERPOLATION,
      RenderingHints.VALUE_INTERPOLATION_BILINEAR
    )

    // Additional quality hints
    g.setRenderingHint(
      RenderingHints.KEY_RENDERING,
      RenderingHints.VALUE_RENDER_QUALITY
    )

    g.setRenderingHint(
      RenderingHints.KEY_ANTIALIASING,
      RenderingHints.VALUE_ANTIALIAS_ON
    )

    // Draw the scaled image
    g.drawImage(image, 0, 0, newWidth, newHeight, null)
    g.dispose()

    resizedImage
  }
}
