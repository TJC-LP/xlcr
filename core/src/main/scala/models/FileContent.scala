package com.tjclp.xlcr
package models

import java.nio.file.{ Files, Path }

import org.apache.tika.Tika

import types.MimeType

class FileContent[+T <: MimeType](
  val data: Array[Byte],
  val mimeType: T
) extends Model

object FileContent {
  private lazy val tika = new Tika()

  def apply[T <: MimeType](data: Array[Byte], mimeType: T): FileContent[T] =
    new FileContent[T](data, mimeType)

  def fromPath[T <: MimeType](path: Path): FileContent[T] = {
    val bytes = Files.readAllBytes(path)
    val detectedMime = MimeType.fromString(tika.detect(path.toFile))
      .getOrElse(throw new RuntimeException(s"Unsupported mime type for file: $path"))
    new FileContent[T](bytes, detectedMime.asInstanceOf[T])
  }

  def fromBytes[T <: MimeType](bytes: Array[Byte]): FileContent[T] = {
    val detectedMime = MimeType.fromString(tika.detect(bytes))
      .getOrElse(throw new RuntimeException("Could not detect mime type from bytes"))
    new FileContent[T](bytes, detectedMime.asInstanceOf[T])
  }
}
