package com.tjclp.xlcr
package bridges.aspose.email

import bridges.SimpleBridge
import types.MimeType.{ApplicationPdf, ApplicationVndMsOutlook}

/** Bridge that converts Outlook MSG files to PDF using Aspose.Email.
  */
object OutlookMsgToPdfAsposeBridge
    extends SimpleBridge[ApplicationVndMsOutlook.type, ApplicationPdf.type]
    with OutlookMsgToPdfAsposeBridgeImpl
