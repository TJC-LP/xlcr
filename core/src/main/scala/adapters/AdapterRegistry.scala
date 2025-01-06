package com.tjclp.xlcr
package adapters

import bridges.excel.{ExcelInputBridge, ExcelJsonOutputBridge}
import bridges.{InputBridge, OutputBridge}
import models.Model
import types.MimeType

object AdapterRegistry {
  def convert[I <: MimeType, O <: MimeType](
                                             inputBytes: Array[Byte],
                                             from: I,
                                             to: O
                                           )(implicit conv: Conversion[I, _ <: Model, O]): Either[String, Array[Byte]] = {
    val model = conv.input.parse(inputBytes)
    Right(conv.output.render(model))
  }

  sealed trait Conversion[I <: MimeType, M <: Model, O <: MimeType] {
    def input: InputBridge[I, M]

    def output: OutputBridge[M, O]
  }

  object Conversion {
    def apply[I <: MimeType, M <: Model, O <: MimeType](
                                                         implicit in: InputBridge[I, M],
                                                         out: OutputBridge[M, O]
                                                       ): Conversion[I, M, O] = new Conversion[I, M, O] {
      def input: InputBridge[I, M] = in

      def output: OutputBridge[M, O] = out
    }
  }

  object implicits {
    // Excel -> JSON conversion
    implicit val excelToJson: Conversion[
      MimeType.ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      models.excel.SheetsData,
      MimeType.ApplicationJson.type
    ] = Conversion(ExcelInputBridge, ExcelJsonOutputBridge)
  }
}