package com.tjclp.xlcr
package bridges.aspose.word

import bridges.SimpleBridge
import models.FileContent
import types.MimeType
import types.MimeType.{ApplicationMsWord, ApplicationPdf}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/**
 * Scala 3 implementation of WordToPdfAsposeBridge.
 * Extends the common implementation without any Scala 3 specific customizations.
 */
object WordToPdfAsposeBridge extends SimpleBridge[ApplicationMsWord.type, ApplicationPdf.type] with WordToPdfAsposeBridgeImpl