package com.tjclp.xlcr
package base

import com.tjclp.xlcr.ParserError
import com.tjclp.xlcr.models.{ FileContent, Model }
import com.tjclp.xlcr.parsers.Parser
import com.tjclp.xlcr.types.MimeType

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait ParserSpec extends AnyFlatSpec with Matchers {

  /**
   * Helper method to parse FileContent with given parser and compare with expected Model result.
   */
  def parseAndCompare[I <: MimeType, M <: Model](
    parser: Parser[I, M],
    input: FileContent[I],
    expected: M
  ): Unit = {
    val result = parser.parse(input)
    result shouldBe expected
  }

  /**
   * Check for expected ParserError on invalid input
   */
  def parseWithError[I <: MimeType, M <: Model](
    parser: Parser[I, M],
    input: FileContent[I],
    errorSubstring: String
  ): Unit = {
    val ex = intercept[ParserError] {
      parser.parse(input)
    }
    ex.message should include(errorSubstring)
  }
}
