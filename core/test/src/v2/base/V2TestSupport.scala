package com.tjclp.xlcr.v2.base

import zio.{Runtime, Unsafe, ZIO}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import com.tjclp.xlcr.v2.registry.{PathFinder, TransformRegistry}
import com.tjclp.xlcr.v2.transform.TransformError

/**
 * Base trait for v2 tests providing ZIO runtime helpers and registry cleanup.
 *
 * Extends this trait to get:
 * - ZIO effect execution helpers (runZIO, runZIOExpectingError)
 * - Automatic registry cleanup between tests
 * - Common ScalaTest mixins
 */
trait V2TestSupport extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll:

  /** The ZIO runtime for executing effects synchronously */
  protected val runtime: Runtime[Any] = Runtime.default

  /**
   * Run a ZIO effect synchronously and return the result.
   *
   * @param zio The effect to run
   * @return The successful result
   * @throws Throwable if the effect fails
   */
  protected def runZIO[E, A](zio: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(zio).getOrThrowFiberFailure()
    }

  /**
   * Run a ZIO effect synchronously expecting failure, returning the error.
   *
   * @param zio The effect expected to fail
   * @return The error that was thrown
   * @throws AssertionError if the effect succeeds
   */
  protected def runZIOExpectingError[E <: TransformError, A](zio: ZIO[Any, E, A]): E =
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(zio.flip).getOrThrowFiberFailure()
    }

  /**
   * Run a ZIO effect synchronously, returning Either for error handling tests.
   *
   * @param zio The effect to run
   * @return Either containing the error or success value
   */
  protected def runZIOEither[E, A](zio: ZIO[Any, E, A]): Either[E, A] =
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(zio.either).getOrThrowFiberFailure()
    }

  /**
   * Clear the transform registry before each test for isolation.
   */
  override def beforeEach(): Unit =
    super.beforeEach()
    TransformRegistry.clear()
    PathFinder.clearCache()

  /**
   * Optional: Clear registry after all tests complete.
   */
  override def afterAll(): Unit =
    super.afterAll()
    TransformRegistry.clear()
    PathFinder.clearCache()
