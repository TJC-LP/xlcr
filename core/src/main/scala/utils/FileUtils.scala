package com.tjclp.xlcr
package utils

import types.MimeType

import org.apache.tika.config.TikaConfig
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.{HttpHeaders, Metadata}
import org.slf4j.LoggerFactory

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.util.{Failure, Success, Try, Using}

/**
 * A central utility object for file operations (reading, writing,
 * extension detection, etc.). Consolidating these methods here
 * helps reduce boilerplate and code duplication.
 */
object FileUtils:
  private lazy val tikaConfig = new TikaConfig()
  private lazy val detector = tikaConfig.getDetector
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Recursively delete a directory and all its contents.
   */
  def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      if (Files.isDirectory(path)) {
        Files.list(path).forEach(deleteRecursively)
      }
      Files.delete(path)
    }
  }

  /**
   * Write the given byte array to the specified path, overwriting if necessary.
   * Wrapped in a Try to handle any I/O exceptions.
   *
   * @param path The Path where data should be written.
   * @param data The byte array to be written.
   * @return A Try indicating success or failure.
   */
  def writeBytes(path: Path, data: Array[Byte]): Try[Unit] = Try:
    val parent = path.getParent
    if parent != null && !Files.exists(parent) then
      Files.createDirectories(parent)

    Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

  /**
   * Execute a function with a temporary file, ensuring the file
   * is deleted afterward, regardless of success or failure.
   *
   * @param prefix Prefix string to be used in generating the file's name.
   * @param suffix Suffix string to be used in generating the file's name (e.g. ".xlsx").
   * @param f      A function that takes a temporary Path and returns a T.
   * @tparam T The result type of the function.
   * @return The result of the function f.
   */
  def withTempFile[T](prefix: String, suffix: String)(f: Path => T): T =
    val tempFile = Files.createTempFile(prefix, suffix)
    try f(tempFile)
    finally Files.deleteIfExists(tempFile)

  /**
   * Read a JSON file and return its content as a String.
   *
   * @param path The Path of the JSON file.
   * @return A Try containing the JSON string, or a Failure with the exception.
   */
  def readJsonFile(path: Path): Try[String] =
    readBytes(path).map(bytes => new String(bytes, StandardCharsets.UTF_8))

  /**
   * Read all bytes from a file into memory, wrapped in a Try for error handling.
   *
   * @param path The Path of the file to read.
   * @return A Try containing the file's bytes, or a Failure with the exception.
   */
  def readBytes(path: Path): Try[Array[Byte]] =
    Try {
      if !fileExists(path) then
        throw new IOException(s"File does not exist: $path")
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
   * Write a JSON string to a file with UTF-8 encoding.
   *
   * @param path        The Path where JSON should be written.
   * @param jsonContent The JSON string to write.
   * @return A Try indicating success or failure.
   */
  def writeJsonFile(path: Path, jsonContent: String): Try[Unit] =
    Try {
      val parent = path.getParent
      if parent != null && !Files.exists(parent) then
        Files.createDirectories(parent)

      // Ensure UTF-8 encoding
      val utf8Bytes = jsonContent.getBytes(StandardCharsets.UTF_8)
      Files.write(path, utf8Bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }

  /**
   * Detect MIME type using Tika. If Tika returns text/plain,
   * fallback to extension-based detection for known file types.
   */
  def detectMimeType(path: Path): MimeType =
    detectMimeTypeWithTika(path) match
      case Success(mimeType) =>
        logger.debug(s"Tika detected MIME type: ${mimeType.mimeType} for file: $path")
        if mimeType == MimeType.TextPlain then
          val extMime = detectMimeTypeFromExtension(path)
          if extMime != MimeType.TextPlain then extMime else mimeType
        else mimeType
      case Failure(exception) =>
        logger.warn(s"Tika detection failed for $path, falling back to extension-based detection", exception)
        detectMimeTypeFromExtension(path)

  /**
   * Detect MIME type by extension, throwing an exception if none is found and strict mode is true.
   */
  def detectMimeTypeFromExtension(path: Path, strict: Boolean = false): MimeType =
    val extension = getExtension(path.toString)
    import types.FileType

    FileType.fromExtension(extension).map(_.getMimeType).getOrElse {
      if strict then
        throw UnknownExtensionException(path, extension)
      else
        logger.debug(s"No MIME type found for extension of $path, defaulting to text/plain")
        MimeType.TextPlain
    }

  /**
   * Retrieves the lowercase extension without the dot. Returns an empty string if none is found.
   */
  def getExtension(filePath: String): String =
    val name = filePath.toLowerCase
    val lastDotIndex = name.lastIndexOf('.')
    if lastDotIndex > 0 && lastDotIndex < name.length - 1 then
      name.substring(lastDotIndex + 1)
    else ""

  /**
   * Try to use Tika to detect the MIME type. Returns Success or Failure.
   */
  private def detectMimeTypeWithTika(filePath: Path): Try[MimeType] = Try {
    if !Files.exists(filePath) then
      throw new IOException(s"File does not exist: $filePath")

    val metadata = new Metadata()
    metadata.set(HttpHeaders.CONTENT_LOCATION, filePath.getFileName.toString)

    Using.resource(TikaInputStream.get(filePath)) { tikaStream =>
      val mediaType = detector.detect(tikaStream, metadata)
      val mimeString = mediaType.toString
      MimeType.values.find(_.mimeType == mimeString).getOrElse {
        logger.debug(s"Unrecognized MIME type from Tika: $mimeString, falling back to text/plain")
        MimeType.TextPlain
      }
    }
  }