package com.tjclp.xlcr
package utils.aspose

/**
 * Thread-local context for passing configuration options to Aspose bridges. Since bridges are
 * discovered via ServiceLoader and don't receive direct parameters, this context allows passing
 * runtime configuration like stripMasters flag.
 */
object BridgeContext {

  private val context = new ThreadLocal[BridgeContextData]() {
    override def initialValue(): BridgeContextData = BridgeContextData()
  }

  /**
   * Get the current thread's bridge context
   */
  def get(): BridgeContextData = context.get()

  /**
   * Set the bridge context for the current thread
   */
  def set(data: BridgeContextData): Unit = context.set(data)

  /**
   * Clear the bridge context for the current thread
   */
  def clear(): Unit = context.remove()

  /**
   * Execute a block of code with a specific bridge context, automatically clearing it afterward
   */
  def withContext[T](data: BridgeContextData)(block: => T): T =
    try {
      set(data)
      block
    } finally
      clear()
}

/**
 * Data stored in the bridge context
 *
 * @param stripMasters
 *   Whether to remove unused master slides/templates during conversions
 */
case class BridgeContextData(
  stripMasters: Boolean = false
)
