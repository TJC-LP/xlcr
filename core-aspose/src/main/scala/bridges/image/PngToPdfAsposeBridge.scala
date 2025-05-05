package com.tjclp.xlcr
package bridges.image

import types.MimeType.ImagePng

/** Bridge that converts PNG images to PDF using Aspose.PDF.
  *
  * This object extends the common implementation trait that contains all the business logic,
  * making it compatible with both Scala 2 and Scala 3.
  */
object PngToPdfAsposeBridge extends ImageToPdfAsposeBridgeImpl[ImagePng.type]