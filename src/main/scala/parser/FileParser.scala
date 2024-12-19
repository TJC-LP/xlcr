package com.tjclp.xlcr
package parser

import types.FileType

import org.apache.tika.Tika
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.{Metadata, TikaCoreProperties}
import org.apache.tika.parser.{AutoDetectParser, ParseContext}
import org.apache.tika.sax.{BodyContentHandler, ToXMLContentHandler, WriteOutContentHandler}
import org.slf4j.LoggerFactory

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.file.Path
import scala.util.Try


object FileParser:
  private val logger = LoggerFactory.getLogger(getClass)
  private val tika = new Tika()

  def parseFromPath(path: Path): Try[FileType] =
    Try {
      val file = path.toFile
      val mimeType = tika.detect(file)
      getFileTypeFromMimeType(mimeType)
    }

  def parseFromBytes(bytes: Array[Byte]): Try[FileType] =
    Try {
      val inputStream = new ByteArrayInputStream(bytes)
      val mimeType = tika.detect(inputStream)
      getFileTypeFromMimeType(mimeType)
    }

  private def getFileTypeFromMimeType(mimeType: String): FileType =
    FileType.values.find(_.getMimeType == mimeType) match
      case Some(fileType) => fileType
      case None => throw new UnsupportedOperationException(s"Unsupported MIME type: $mimeType")

  def parseFromInputStream(inputStream: InputStream): Try[FileType] =
    Try {
      val mimeType = tika.detect(inputStream)
      getFileTypeFromMimeType(mimeType)
    }

  def parseFromExtension(fileName: String): Option[FileType] =
    getExtension(fileName).flatMap(FileType.fromExtension)

  private def getExtension(fileName: String): Option[String] =
    val lastDotIndex = fileName.lastIndexOf('.')
    if lastDotIndex > 0 && lastDotIndex < fileName.length - 1 then
      Some(fileName.substring(lastDotIndex + 1).toLowerCase)
    else
      None

  def extractContent(path: Path, enableXMLOutput: Boolean = false, writeLimit: Int = -1): Try[TikaContent] =
    Try {
      logger.info(s"Extracting content from file: ${path.getFileName}")
      val stream = TikaInputStream.get(path)
      val filename = path.getFileName.toString

      val handler = if (enableXMLOutput) {
        logger.debug("XML output enabled")
        val xmlHandler = new ToXMLContentHandler()
        new WriteOutContentHandler(xmlHandler, writeLimit)
      } else {
        logger.debug("Plain text output enabled")
        new BodyContentHandler(writeLimit)
      }

      val parser = new AutoDetectParser()
      val metadata = new Metadata()
      val parseContext = new ParseContext()

      try {
        val contentType = retrieveContentType(parser, metadata, stream, filename)
        logger.debug(s"Detected content type: $contentType")

        parser.parse(stream, handler, metadata, parseContext)
        val extractedContent = handler.toString

        logger.info("Content extraction completed successfully")
        TikaContent(
          extractedContent,
          contentType,
          metadata.names().map(name => (name, metadata.get(name))).toMap
        )
      } finally {
        stream.close()
      }
    }

  private def retrieveContentType(parser: AutoDetectParser,
                                  metadata: Metadata,
                                  stream: TikaInputStream,
                                  filename: String): String = {
    metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, filename)
    parser.getDetector.detect(stream, metadata).toString
  }
