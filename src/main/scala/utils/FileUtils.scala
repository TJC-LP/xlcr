package com.tjclp.xlcr
package utils

import types.MimeType

import java.io.IOException
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.util.Try

/**
 * A central utility object for file operations (reading, writing,
 * extension detection, etc.). Consolidating these methods here
 * helps reduce boilerplate and code duplication.
 */
object FileUtils:

  /**
   * Write the given byte array to the specified path, overwriting if necessary.
   * Wrapped in a Try to handle any I/O exceptions.
   *
   * @param path The Path where data should be written.
   * @param data The byte array to be written.
   * @return A Try indicating success or failure.
   */
  def writeBytes(path: Path, data: Array[Byte]): Try[Unit] = Try:
    // If parent directory doesn't exist, create it (optional behavior)
    val parent = path.getParent
    if (parent != null && !Files.exists(parent)) {
      Files.createDirectories(parent)
    }

    Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

  /**
   * Execute a function with a temporary file, ensuring the file
   * is deleted afterward, regardless of success or failure.
   *
   * Example usage:
   * {{{
   *   FileUtils.withTempFile("myPrefix", ".txt") { tempPath =>
   *     // use tempPath
   *     // automatically deleted after the function completes or throws
   *   }
   * }}}
   *
   * @param prefix Prefix string to be used in generating the file's name.
   * @param suffix Suffix string to be used in generating the file's name (e.g. ".xlsx").
   * @param f      A function that takes a temporary Path and returns a T.
   * @tparam T The result type of the function.
   * @return The result of the function f.
   */
  def withTempFile[T](prefix: String, suffix: String)(f: Path => T): T =
    val tempFile = Files.createTempFile(prefix, suffix)
    try
      f(tempFile)
    finally
      // Attempt cleanup
      Files.deleteIfExists(tempFile)

  /**
   * Read a JSON file and return its content as a String.
   *
   * @param path The Path of the JSON file.
   * @return A Try containing the JSON string, or a Failure with the exception.
   */
  def readJsonFile(path: Path): Try[String] =
    readBytes(path).map(bytes => new String(bytes, "UTF-8"))

  /**
   * Read all bytes from a file into memory, wrapped in a Try for error handling.
   *
   * @param path The Path of the file to read.
   * @return A Try containing the file's bytes, or a Failure with the exception.
   */
  def readBytes(path: Path): Try[Array[Byte]] =
    Try {
      if (!fileExists(path)) {
        throw new IOException(s"File does not exist: $path")
      }
      Files.readAllBytes(path)
    }

  /**
   * Check if a file exists at the given Path.
   *
   * @param path The Path of the file to check.
   * @return True if the file exists, false otherwise.
   */
  def fileExists(path: Path): Boolean =
    Files.exists(path)

  /**
   * Write a JSON string to a file.
   *
   * @param path        The Path where JSON should be written.
   * @param jsonContent The JSON string to write.
   * @return A Try indicating success or failure.
   */
  def writeJsonFile(path: Path, jsonContent: String): Try[Unit] =
    Try {
      // If parent directory doesn't exist, create it
      val parent = path.getParent
      if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent)
      }

      Files.write(path, jsonContent.getBytes("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }

  def detectMimeType(path: Path): MimeType =
    import types.FileType
    FileType.fromExtension(getExtension(path.toString))
      .map(_.getMimeType)
      .getOrElse(MimeType.TextPlain)

  /**
   * Safely retrieve the lowercase extension of a file path.
   *
   * @param filePath The file path as a string (e.g. "/my/folder/data.json").
   * @return The extension without the dot (e.g. "json"), or "" if none found.
   */
  def getExtension(filePath: String): String =
    val name = filePath.toLowerCase
    val lastDotIndex = name.lastIndexOf('.')
    if lastDotIndex > 0 && lastDotIndex < name.length - 1 then
      name.substring(lastDotIndex + 1)
    else
      ""
