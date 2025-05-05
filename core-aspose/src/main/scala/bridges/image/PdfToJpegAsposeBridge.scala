package com.tjclp.xlcr
package bridges.image

import types.MimeType.ImageJpeg

/** Concrete implementation of PDF to JPEG conversion using Aspose.
 */
object PdfToJpegAsposeBridge extends PdfAsposeImageBridge[ImageJpeg.type] {
  override val targetMime: ImageJpeg.type = ImageJpeg
}
