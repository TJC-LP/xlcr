package com.tjclp.xlcr
package bridges.image

import types.MimeType.ImageJpeg

/**
 * Bridge that converts JPEG images to PDF using Aspose.PDF.
 *
 * This object extends the common implementation trait that contains all the business logic, making
 * it compatible with both Scala 2 and Scala 3.
 */
object JpegToPdfAsposeBridge extends ImageToPdfAsposeBridgeImpl[ImageJpeg.type]
