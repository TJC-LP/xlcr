package com.tjclp.xlcr
package pipeline.steps

import types.MimeType
import types.MimeType.TextPlain

/**
 * Convert any supported document into *plain text* using the catch‑all Tika bridge that is
 * registered for `* -> text/plain`.
 */
object ExtractTextStep extends BridgeStep[TextPlain.type] {
  override val targetMime: MimeType.TextPlain.type = MimeType.TextPlain
}
