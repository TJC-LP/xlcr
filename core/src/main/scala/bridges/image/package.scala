package com.tjclp.xlcr
package bridges

/**
 * Image bridges package providing conversion between PDF and various image formats.
 *
 * This package contains bridges for converting PDF documents to various image formats such as PNG
 * and JPEG, with support for different rendering engines (PDFBox, Aspose).
 */
package object image {

  /**
   * Type alias for backwards compatibility.
   *
   * This allows existing code that uses PdfToImageConfig to continue working with the new
   * ImageRenderConfig without changes.
   */
  type PdfToImageConfig = ImageRenderConfig
}
