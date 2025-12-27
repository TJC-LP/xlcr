package com.tjclp.xlcr
package bridges.word

import types.MimeType.ApplicationMsWord

/**
 * Bridge that converts Microsoft Word .doc documents to PDF using LibreOffice.
 *
 * This object extends the common implementation trait that contains all the business logic, making
 * it compatible with both Scala 2 and Scala 3.
 *
 * Priority: DEFAULT (fallback to Aspose if available)
 */
object WordDocToPdfLibreOfficeBridge
    extends WordToPdfLibreOfficeBridgeImpl[ApplicationMsWord.type]
