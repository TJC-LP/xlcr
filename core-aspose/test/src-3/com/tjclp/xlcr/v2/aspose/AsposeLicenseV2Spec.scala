package com.tjclp.xlcr.v2.aspose

import java.util.concurrent.{ CountDownLatch, TimeUnit }
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AsposeLicenseV2Spec extends AnyWordSpec with Matchers:

  "AsposeLicenseV2.initOnce" should {
    "run initialization once and block concurrent callers until completion" in {
      val guard      = new AtomicBoolean(false)
      val initRuns   = new AtomicInteger(0)
      val initEntered = new CountDownLatch(1)
      val allowFinish = new CountDownLatch(1)
      val secondDone  = new CountDownLatch(1)

      val t1 = new Thread(() =>
        AsposeLicenseV2.initOnce(guard) {
          initRuns.incrementAndGet()
          initEntered.countDown()
          allowFinish.await(3, TimeUnit.SECONDS) shouldBe true
        }
      )

      val t2 = new Thread(() =>
        initEntered.await(3, TimeUnit.SECONDS) shouldBe true
        AsposeLicenseV2.initOnce(guard) {
          initRuns.incrementAndGet()
        }
        secondDone.countDown()
      )

      t1.start()
      t2.start()

      // Second caller must wait while first caller is still inside init.
      secondDone.await(150, TimeUnit.MILLISECONDS) shouldBe false

      allowFinish.countDown()
      t1.join(3000)
      t2.join(3000)

      initRuns.get() shouldBe 1
      guard.get() shouldBe true
      secondDone.getCount shouldBe 0
    }

    "mark initialization complete even when init throws so waiters are released" in {
      val guard       = new AtomicBoolean(false)
      val initEntered = new CountDownLatch(1)
      val allowFinish = new CountDownLatch(1)
      val secondDone  = new CountDownLatch(1)
      val thrown      = new AtomicReference[Throwable](null)

      val t1 = new Thread(() =>
        try
          AsposeLicenseV2.initOnce(guard) {
            initEntered.countDown()
            allowFinish.await(3, TimeUnit.SECONDS) shouldBe true
            throw new RuntimeException("boom")
          }
        catch
          case ex: Throwable => thrown.set(ex)
      )

      val t2 = new Thread(() =>
        initEntered.await(3, TimeUnit.SECONDS) shouldBe true
        AsposeLicenseV2.initOnce(guard) {
          fail("second caller must not execute init")
        }
        secondDone.countDown()
      )

      t1.start()
      t2.start()

      secondDone.await(150, TimeUnit.MILLISECONDS) shouldBe false

      allowFinish.countDown()
      t1.join(3000)
      t2.join(3000)

      guard.get() shouldBe true
      secondDone.getCount shouldBe 0
      thrown.get() should not be null
      thrown.get().getMessage shouldBe "boom"
    }
  }
