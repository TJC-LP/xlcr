package com.tjclp.xlcr
package renderers.tika

import types.MimeType.ApplicationXml

/**
 * Renders a TikaModel[ApplicationXml.type] back to application/xml.
 */
class TikaXmlRenderer extends TikaRenderer[ApplicationXml.type]:
  override val mimeType: ApplicationXml.type = ApplicationXml
  