package com.tjclp.xlcr
package bridges.aspose.word

import bridges.SimpleBridge
import types.MimeType.{ApplicationMsWord, ApplicationPdf}

/** Bridge that converts Microsoft Word documents to PDF using Aspose.Words.
  *
  * This object extends the common implementation trait that contains all the business logic,
  * making it compatible with both Scala 2 and Scala 3.
  */
object WordDocToPdfAsposeBridge
    extends SimpleBridge[ApplicationMsWord.type, ApplicationPdf.type]
    with WordToPdfAsposeBridgeImpl[ApplicationMsWord.type]
