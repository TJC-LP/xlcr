package com.tjclp.xlcr.output

import java.io.{ ByteArrayOutputStream, OutputStream }
import java.util.zip.{ ZipEntry, ZipOutputStream }

import com.tjclp.xlcr.types.DynamicFragment

/**
 * Utilities for building ZIP archives from split fragments.
 *
 * Provides consistent ZIP output with standardized naming across CLI and Server.
 */
object ZipBuilder:

  /**
   * Build a ZIP archive from fragments as a byte array.
   *
   * Each fragment is named using the standard convention: {paddedIndex}__{sanitizedName}.{ext}
   *
   * @param fragments
   *   Sequence of fragments to include in the ZIP
   * @param defaultExt
   *   Default extension if MIME type is unknown (default: "bin")
   * @return
   *   ZIP archive as byte array
   */
  def buildZip(fragments: Seq[DynamicFragment], defaultExt: String = "bin"): Array[Byte] =
    val baos = new ByteArrayOutputStream()
    writeZip(fragments, baos, defaultExt)
    baos.toByteArray

  /**
   * Build a ZIP archive from a ZIO Chunk of fragments.
   *
   * @param fragments
   *   Chunk of fragments to include in the ZIP
   * @param defaultExt
   *   Default extension if MIME type is unknown (default: "bin")
   * @return
   *   ZIP archive as byte array
   */
  def buildZip(fragments: zio.Chunk[DynamicFragment], defaultExt: String): Array[Byte] =
    buildZip(fragments.toList, defaultExt)

  /**
   * Build a ZIP archive from a ZIO Chunk of fragments with default extension.
   *
   * @param fragments
   *   Chunk of fragments to include in the ZIP
   * @return
   *   ZIP archive as byte array
   */
  def buildZip(fragments: zio.Chunk[DynamicFragment]): Array[Byte] =
    buildZip(fragments.toList, "bin")

  /**
   * Write a ZIP archive to an output stream.
   *
   * Each fragment is named using the standard convention: {paddedIndex}__{sanitizedName}.{ext}
   *
   * @param fragments
   *   Sequence of fragments to include in the ZIP
   * @param out
   *   Output stream to write the ZIP archive to
   * @param defaultExt
   *   Default extension if MIME type is unknown (default: "bin")
   */
  def writeZip(
    fragments: Seq[DynamicFragment],
    out: OutputStream,
    defaultExt: String = "bin"
  ): Unit =
    val zos = new ZipOutputStream(out)
    try
      val total = fragments.size
      fragments.zipWithIndex.foreach { case (fragment, idx) =>
        // Use 1-based index for display
        val index = idx + 1
        // Get fragment name, falling back to index-based name
        val name = fragment.name.getOrElse(s"fragment_$index")
        // Get extension from MIME type or use default
        val ext = MimeExtensions.getExtensionOrDefault(fragment.mime, defaultExt)
        // Build the standardized filename
        val filename = FragmentNaming.buildFilename(index, total, name, ext)
        // Write the entry
        zos.putNextEntry(new ZipEntry(filename))
        zos.write(fragment.data.toArray)
        zos.closeEntry()
      }
    finally zos.close()

  /**
   * Write a ZIP archive from a ZIO Chunk to an output stream.
   *
   * @param fragments
   *   Chunk of fragments to include in the ZIP
   * @param out
   *   Output stream to write the ZIP archive to
   * @param defaultExt
   *   Default extension if MIME type is unknown (default: "bin")
   */
  def writeZip(
    fragments: zio.Chunk[DynamicFragment],
    out: OutputStream,
    defaultExt: String
  ): Unit =
    writeZip(fragments.toList, out, defaultExt)
