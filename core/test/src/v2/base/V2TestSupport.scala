package com.tjclp.xlcr.v2.base

import zio.{Runtime, Unsafe, ZIO}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.tjclp.xlcr.v2.transform.TransformError

/**
 * Base trait for v2 tests providing ZIO runtime helpers.
 *
 * Extends this trait to get:
 * - ZIO effect execution helpers (runZIO, runZIOExpectingError)
 * - Common ScalaTest mixins
 */
trait V2TestSupport extends AnyFlatSpec with Matchers:

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
