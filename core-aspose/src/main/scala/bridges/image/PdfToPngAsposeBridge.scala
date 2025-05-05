package com.tjclp.xlcr
package bridges.image

import types.MimeType.ImagePng

/** Concrete implementation of PDF to PNG conversion using Aspose.
 */
object PdfToPngAsposeBridge extends PdfAsposeImageBridge[ImagePng.type] {
  override val targetMime: ImagePng.type = ImagePng
}
