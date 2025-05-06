package com.tjclp.xlcr
package bridges.email

import types.MimeType.ApplicationVndMsOutlook

/**
 * Bridge that converts Outlook MSG files to PDF using Aspose.Email.
 */
object OutlookMsgToPdfAsposeBridge
    extends EmailToPdfAsposeBridgeImpl[ApplicationVndMsOutlook.type]
