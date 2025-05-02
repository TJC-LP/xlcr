package com.tjclp.xlcr
package bridges.aspose.email

import types.MimeType.MessageRfc822

/** Bridge that converts RFC822 Email files (EML, etc.) to PDF using Aspose.Email.
  */
object EmailEmlToPdfAsposeBridge
    extends EmailToPdfAsposeBridgeImpl[MessageRfc822.type]
