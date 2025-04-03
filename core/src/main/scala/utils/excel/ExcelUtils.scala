package com.tjclp.xlcr
package utils.excel

import models.excel.ExcelReference

import scala.annotation.tailrec

object ExcelUtils {
  def columnToString(col: ExcelReference.Col): String = {
    @tailrec
    def loop(n: Int, acc: String = ""): String = {
      if (n < 0) acc
      else {
        val char = ('A' + (n % 26)).toChar
        loop(n / 26 - 1, char.toString + acc)
      }
    }

    loop(col)
  }

  def a1ToReference(a1: String): (ExcelReference.Row, ExcelReference.Col) = {
    val cellRef = a1ToSheetAndAddress(a1)._2
    val pattern = """([A-Za-z]+)(\d+)""".r
    cellRef match {
      case pattern(col, row) =>
        (ExcelReference.Row(row.toInt - 1), stringToColumn(col))
      case _ => throw new IllegalArgumentException(s"Invalid A1 reference format: $a1")
    }
  }

  def a1ToSheetAndAddress(a1: String): (Option[String], String) = {
    if (a1.contains('!')) {
      val result = a1.split('!')
      (Some(result(0)), result(1))
    } else {
      (None, a1)
    }
  }

  def stringToColumn(s: String): ExcelReference.Col = {
    require(s.nonEmpty && s.forall(_.isLetter), s"Column reference must contain only letters, got '$s'")

    val upperS = s.toUpperCase
    require(upperS.forall(c => c >= 'A' && c <= 'Z'),
      s"Column reference must contain only A-Z letters, got '$s'")

    ExcelReference.Col(
      upperS.foldLeft(0)((acc, c) => acc * 26 + c.toInt - 'A'.toInt + 1) - 1
    )
  }
}
