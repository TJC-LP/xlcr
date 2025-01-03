package com.tjclp.xlcr
package utils.excel

import models.ExcelReference

import scala.annotation.tailrec

object ExcelUtils:
  def columnToString(col: ExcelReference.Col): String =
    @tailrec
    def loop(n: Int, acc: String = ""): String =
      if n < 0 then acc
      else
        val char = ('A' + (n % 26)).toChar
        loop(n / 26 - 1, char.toString + acc)

    loop(col.value)

  def stringToColumn(s: String): ExcelReference.Col =
    require(s.nonEmpty && s.forall(_.isLetter), s"Column reference must contain only letters, got '$s'")

    val upperS = s.toUpperCase
    require(upperS.forall(c => c >= 'A' && c <= 'Z'),
      s"Column reference must contain only A-Z letters, got '$s'")

    ExcelReference.Col(
      upperS.foldLeft(0)((acc, c) => acc * 26 + c.toInt - 'A'.toInt + 1) - 1
    )
