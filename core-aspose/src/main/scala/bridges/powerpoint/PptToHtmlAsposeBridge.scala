package com.tjclp.xlcr
package bridges.powerpoint

import types.MimeType.ApplicationVndMsPowerpoint

/**
 * Bridge that converts PPT (PowerPoint 97-2003) files to HTML using Aspose.Slides.
 */
object PptToHtmlAsposeBridge
    extends PowerPointToHtmlAsposeBridgeImpl[ApplicationVndMsPowerpoint.type]
