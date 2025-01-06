package com.tjclp.xlcr
package adapters

import bridges.{InputBridge, OutputBridge}
import bridges.excel.{ExcelInputBridge, ExcelJsonOutputBridge}
import models.Model
import types.MimeType

/**
 * AdapterRegistry provides automatic conversion between supported mime types
 * by detecting compatible input and output bridges through their shared model types.
 */
object AdapterRegistry {
  // All available input bridges
  private val inputBridges: Seq[InputBridge[_ <: MimeType, _ <: Model[_]]] = Seq(
    ExcelInputBridge
  )

  // All available output bridges  
  private val outputBridges: Seq[OutputBridge[_ <: Model[_], _ <: MimeType]] = Seq(
    ExcelJsonOutputBridge
  )

  /**
   * High-level convert method: parse => model => render, returning raw bytes or error.
   */
  def convert[I <: MimeType, O <: MimeType](inputBytes: Array[Byte], from: I, to: O): Either[String, Array[Byte]] = {
    findConversion(from, to) match {
      case Some((inBridge, outBridge)) =>
        val model = inBridge.parse(inputBytes)
        Right(outBridge.render(model))
      case None =>
        Left(s"No adapter found that supports $from -> $to")
    }
  }

  /**
   * Finds a suitable input->output bridge combination by matching mime types and model types.
   */
  private def findConversion[I <: MimeType, M <: Model[_], O <: MimeType](inputMime: I, outputMime: O): Option[(InputBridge[I, M], OutputBridge[M, O])] = {
    // Find compatible bridges by matching input/output mime types and model types
    for {
      inBridge <- inputBridges.find(_.inputMimeType == inputMime)
      outBridge <- outputBridges.find { ob =>
        ob.outputMimeType == outputMime &&
        // Check if the model types are compatible
        inBridge.modelType == ob.modelType
      }
    } yield (
      inBridge.asInstanceOf[InputBridge[I, M]], 
      outBridge.asInstanceOf[OutputBridge[M, O]]
    )
  }
}