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
    ExcelReference.Col(s.toUpperCase.foldLeft(0)((acc, c) => acc * 26 + c.toInt - 'A'.toInt + 1) - 1)
