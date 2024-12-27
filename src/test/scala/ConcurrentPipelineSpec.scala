package com.tjclp.xlcr

import parsers.excel.ExcelParserTestCommon
import utils.FileUtils

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentHashMap, CountDownLatch, TimeUnit}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters.*

class ConcurrentPipelineSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  // Track active test runs to ensure cleanup
  private val activeTests = new ConcurrentHashMap[String, CountDownLatch]()

  override def afterAll(): Unit = {
    // Wait for any lingering tests to complete
    activeTests.values().asScala.foreach(_.await(5, TimeUnit.SECONDS))
    super.afterAll()
  }

  "Pipeline" should "handle concurrent processing safely" in {
    val testLatch = new CountDownLatch(5)
    val counter = new AtomicInteger(0)
    activeTests.put("concurrent-processing", testLatch)

    val futures = (1 to 5).map { i =>
      Future {
        try {
          FileUtils.withTempFile(s"input$i", ".xlsx") { inputPath =>
            FileUtils.withTempFile(s"output$i", ".json") { outputPath =>
              // Create test Excel file
              ExcelParserTestCommon.createComplexWorkbook(inputPath)

              // Process the file
              val result = Pipeline.run(inputPath.toString, outputPath.toString)

              // Verify the result
              result.contentType shouldBe "application/json"
              Files.exists(outputPath) shouldBe true
              val content = new String(Files.readAllBytes(outputPath))
              content should include("DataTypes")

              counter.incrementAndGet()
            }
          }
        } finally {
          testLatch.countDown()
        }
      }
    }

    // Wait for all futures to complete
    Await.result(Future.sequence(futures), 30.seconds)

    // Verify all files were processed
    counter.get() shouldBe 5
    activeTests.remove("concurrent-processing")
  }

  it should "handle concurrent access to the same input file" in {
    val testLatch = new CountDownLatch(3)
    activeTests.put("concurrent-reads", testLatch)

    FileUtils.withTempFile("shared", ".xlsx") { inputPath =>
      ExcelParserTestCommon.createComplexWorkbook(inputPath)

      val futures = (1 to 3).map { i =>
        Future {
          try {
            FileUtils.withTempFile(s"output$i", ".json") { outputPath =>
              val result = Pipeline.run(inputPath.toString, outputPath.toString)
              result.contentType shouldBe "application/json"
              Files.exists(outputPath) shouldBe true
            }
          } finally {
            testLatch.countDown()
          }
        }
      }

      // All concurrent reads should succeed
      noException should be thrownBy {
        Await.result(Future.sequence(futures), 30.seconds)
      }
    }

    activeTests.remove("concurrent-reads")
  }

  it should "handle concurrent writes to different output files" in {
    val testLatch = new CountDownLatch(3)
    activeTests.put("concurrent-writes", testLatch)
    val outputPaths = (1 to 3).map { i =>
      Files.createTempFile(s"output$i", ".json")
    }

    try {
      FileUtils.withTempFile("input", ".xlsx") { inputPath =>
        ExcelParserTestCommon.createComplexWorkbook(inputPath)

        val futures = outputPaths.map { outputPath =>
          Future {
            try {
              Pipeline.run(inputPath.toString, outputPath.toString)
            } finally {
              testLatch.countDown()
            }
          }
        }

        // All concurrent writes should succeed
        val results = Await.result(Future.sequence(futures), 30.seconds)
        results.foreach { result =>
          result.contentType shouldBe "application/json"
        }

        // Verify all output files exist and have content
        outputPaths.foreach { path =>
          Files.exists(path) shouldBe true
          Files.size(path) should be > 0L
        }
      }
    } finally {
      // Clean up
      outputPaths.foreach(Files.deleteIfExists)
      activeTests.remove("concurrent-writes")
    }
  }
}