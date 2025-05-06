package com.tjclp.xlcr
package pipeline.steps

import types.MimeType
import types.MimeType.TextXml

/**
 * Convert any supported document into an XML representation using the Tika `* -> application/xml`
 * bridge.
 */
object ExtractXmlStep extends BridgeStep[TextXml.type] {
  override val targetMime: MimeType.TextXml.type = MimeType.TextXml
}
