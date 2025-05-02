package com.tjclp.xlcr
package splitters

import types.MimeType

/** A SplitPolicy decides, for a given MimeType, which SplitConfig should be
  * used.  Keeping this logic outside Spark-specific code lets JVM, ZIO and
  * Spark runtimes share the exact same heuristics.
  */
trait SplitPolicy extends (MimeType => SplitConfig)

object SplitPolicy {

  /** Default heuristic: fall back to `SplitConfig.autoForMime` which relies on
    * the mapping in SplitConfig companion.
    */
  object Default extends SplitPolicy {
    override def apply(mime: MimeType): SplitConfig =
      SplitConfig.autoForMime(mime)
  }

  /** Helper to build a policy from a partial function, delegating to another
    * policy for unhandled cases.
    */
  def fromPartial(pf: PartialFunction[MimeType, SplitConfig], fallback: SplitPolicy = Default): SplitPolicy =
    (mime: MimeType) => if (pf.isDefinedAt(mime)) pf(mime) else fallback(mime)
}
