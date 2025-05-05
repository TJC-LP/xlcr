package com.tjclp.xlcr
package bridges.image

import types.MimeType.ImagePng

/** Concrete implementation of PDF to PNG conversion using PDFBox.
  */
object PdfToPngBridge extends PdfBoxImageBridge[ImagePng.type] {
  override val targetMime: ImagePng.type = ImagePng
}
