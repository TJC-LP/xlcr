package com.tjclp.xlcr
package bridges.powerpoint

import types.MimeType.ApplicationVndMsPowerpoint

/**
 * Bridge that converts PowerPoint .ppt documents to PDF using LibreOffice.
 *
 * Priority: DEFAULT (fallback to Aspose if available)
 */
object PowerPointPptToPdfLibreOfficeBridge
    extends PowerPointToPdfLibreOfficeBridgeImpl[ApplicationVndMsPowerpoint.type]
