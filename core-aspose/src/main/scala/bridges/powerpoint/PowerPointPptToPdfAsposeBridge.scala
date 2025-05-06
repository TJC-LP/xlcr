package com.tjclp.xlcr
package bridges.powerpoint

import types.MimeType.ApplicationVndMsPowerpoint

/**
 * Bridge that converts PPT (Legacy PowerPoint) files to PDF using Aspose.Slides.
 */
object PowerPointPptToPdfAsposeBridge
    extends PowerPointToPdfAsposeBridgeImpl[ApplicationVndMsPowerpoint.type]
