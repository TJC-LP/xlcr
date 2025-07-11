package com.tjclp.xlcr
package utils.resource

import scala.language.reflectiveCalls

/**
 * Provides implicit conversions to make various resource types compatible with scala.util.Using.
 * This allows us to use the Using pattern with resources that don't implement AutoCloseable,
 * such as Aspose objects that use dispose() or Apache POI objects that use close().
 */
object ResourceWrappers {

  /**
   * Wrapper for objects that have a dispose() method (common in Aspose libraries).
   * This includes MailMessage, Presentation, Document, etc.
   */
  implicit class DisposableWrapper[T <: { def dispose(): Unit }](val resource: T) extends AutoCloseable {
    override def close(): Unit = resource.dispose()
  }

  /**
   * Wrapper for objects that have a cleanup() method (used by Aspose Word documents).
   */
  implicit class CleanupWrapper[T <: { def cleanup(): Unit }](val resource: T) extends AutoCloseable {
    override def close(): Unit = resource.cleanup()
  }

  /**
   * Wrapper for Apache POI Workbook objects which already have close() but don't implement AutoCloseable.
   */
  implicit class CloseableWrapper[T <: { def close(): Unit }](val resource: T) extends AutoCloseable {
    override def close(): Unit = resource.close()
  }

  /**
   * Wrapper for resources that need custom cleanup logic.
   * Usage: resource.withCleanup(r => r.customCleanupMethod())
   */
  implicit class CustomCleanupWrapper[T](val resource: T) extends AnyVal {
    def withCleanup(cleanup: T => Unit): AutoCloseable = () => cleanup(resource)
  }

  /**
   * Helper to create an AutoCloseable from a cleanup function.
   * Useful for temporary files or other resources that need custom cleanup.
   */
  def autoCloseable(cleanup: => Unit): AutoCloseable = () => cleanup
}