package com.tjclp.xlcr
package bridges.aspose.email

import bridges.SimpleBridge
import types.MimeType.{ApplicationPdf, MessageRfc822}

/** Bridge that converts RFC822 Email files (EML, etc.) to PDF using Aspose.Email.
  */
object EmailToPdfAsposeBridge
    extends SimpleBridge[MessageRfc822.type, ApplicationPdf.type]
    with EmailRfc822ToPdfAsposeBridgeImpl
