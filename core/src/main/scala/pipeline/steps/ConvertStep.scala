package com.tjclp.xlcr
package pipeline.steps

import types.MimeType

/**
 * A thin adapter that delegates to an existing `Bridge` held inside [[BridgeRegistry]]. It lets us
 * use a bridge as a composable pipeline step without changing any of the bridge code.
 */
final case class ConvertStep[O <: MimeType](override val targetMime: O)
    extends BridgeStep[O]
