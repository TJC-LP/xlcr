package com.tjclp.xlcr
package bridges.image

import types.MimeType.ImageJpeg

/**
 * Concrete implementation of PDF to JPEG conversion using PDFBox.
 */
object PdfToJpegBridge extends PdfBoxImageBridge[ImageJpeg.type] {
  override val targetMime: ImageJpeg.type = ImageJpeg
}
