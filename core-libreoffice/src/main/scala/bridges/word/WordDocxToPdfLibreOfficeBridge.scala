package com.tjclp.xlcr
package bridges.word

import types.MimeType.ApplicationVndOpenXmlFormatsWordprocessingmlDocument

/**
 * Bridge that converts Microsoft Word .docx documents to PDF using LibreOffice.
 *
 * This object extends the common implementation trait that contains all the business logic,
 * making it compatible with both Scala 2 and Scala 3.
 *
 * Priority: DEFAULT (fallback to Aspose if available)
 */
object WordDocxToPdfLibreOfficeBridge
    extends WordToPdfLibreOfficeBridgeImpl[ApplicationVndOpenXmlFormatsWordprocessingmlDocument.type]
