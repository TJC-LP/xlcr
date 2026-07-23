package com.tjclp.xlcr.server.http

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import zio.http.{Method, Request, URL}

class RequestHandlerSpec extends AnyWordSpec with Matchers:

  private def request(path: String): Request =
    Request(
      method = Method.GET,
      url = URL.decode(path).getOrElse(URL.empty)
    )

  "RequestHandler.parseConvertOptions" should {
    "collect repeated sheet query parameters" in {
      val options = RequestHandler.parseConvertOptions(
        request("/convert?sheet=Q1&sheet=Q2")
      )

      options.sheetNames shouldBe List("Q1", "Q2")
    }

    "preserve comma-separated sheet parsing across repeated params" in {
      val options = RequestHandler.parseConvertOptions(
        request("/convert?sheet=Q1,Q2&sheet=Q3")
      )

      options.sheetNames shouldBe List("Q1", "Q2", "Q3")
    }
  }
