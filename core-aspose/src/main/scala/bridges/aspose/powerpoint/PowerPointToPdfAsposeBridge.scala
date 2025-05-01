package com.tjclp.xlcr
package bridges.aspose.powerpoint

import bridges.SimpleBridge
import types.MimeType.{ApplicationPdf, ApplicationVndMsPowerpoint}

/** Bridge that converts PPT (Legacy PowerPoint) files to PDF using Aspose.Slides.
  */
object PowerPointToPdfAsposeBridge
    extends SimpleBridge[ApplicationVndMsPowerpoint.type, ApplicationPdf.type]
    with PowerPointPptToPdfAsposeBridgeImpl
