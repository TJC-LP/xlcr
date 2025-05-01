package com.tjclp.xlcr
package bridges.aspose.word

import bridges.SimpleBridge
import types.MimeType.{ApplicationMsWord, ApplicationPdf}

/** Scala 2.12 implementation of WordToPdfAsposeBridge.
  * Extends the common implementation with Scala 2 specific syntax.
  */
object WordToPdfAsposeBridge
    extends SimpleBridge[ApplicationMsWord.type, ApplicationPdf.type]
    with WordToPdfAsposeBridgeImpl
