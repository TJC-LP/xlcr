package com.tjclp.xlcr
package utils

import org.slf4j.LoggerFactory

/**
 * Utility for filtering and normalizing file paths, particularly for archives.
 *
 * This class provides methods to:
 *   - Filter out macOS-specific metadata directories and files
 *   - Clean paths for display purposes
 *   - Normalize path separators across platforms
 *   - Remove hidden file prefixes
 */
object PathFilter:
  private val logger = LoggerFactory.getLogger(getClass)

  // Patterns for filtering macOS metadata files and directories
  private val macOsPatterns = Seq(
    "^__MACOSX",          // __MACOSX directory at root
    "/__MACOSX/",         // __MACOSX directory in any subdirectory
    "/\\._",              // macOS resource fork files with ._ prefix
    "^\\._",              // ._ prefixed files at root
    "\\.DS_Store$",       // macOS directory structure files
    "\\.AppleDouble$",    // macOS resource fork storage
    "\\.AppleDB$",        // macOS database
    "\\.Spotlight-V100$", // Spotlight metadata
    "\\.Trashes$",        // macOS trash files
    "\\.apdisk$",         // macOS disk image
    "\\.fseventsd$"       // macOS file system events
  )

  // Compile the patterns as regexes
  private val macOsRegexes = macOsPatterns.map(_.r)

  /**
   * Determines if a path is a macOS metadata file or directory.
   *
   * @param path
   *   The file path to check
   * @return
   *   True if the path is a macOS metadata file or directory, false otherwise
   */
  def isMacOsMetadata(path: String): Boolean =
    macOsRegexes.exists(regex => regex.findFirstIn(path).isDefined)

  /**
   * Cleans a path for display by:
   *   1. Removing macOS-specific hidden file prefix "._" 2. Taking only the last path component
   *      (filename) 3. Handling special characters
   *
   * @param path
   *   The file path to clean
   * @return
   *   The cleaned path for display
   */
  def cleanPathForDisplay(path: String): String =
    // Get the last path component (filename)
    val lastComponent = path.split("/").last

    // Remove the macOS resource fork prefix if present
    if lastComponent.startsWith("._") then
      lastComponent.substring(2)
    else
      lastComponent

  /**
   * Normalizes path separators to forward slashes.
   *
   * @param path
   *   The file path to normalize
   * @return
   *   The path with normalized separators
   */
  def normalizeSeparators(path: String): String =
    path.replace('\\', '/')

  /**
   * Filters out certain paths based on patterns.
   *
   * @param paths
   *   The collection of paths to filter
   * @param includeMacOsFilter
   *   Whether to include the macOS metadata filter
   * @param additionalFilters
   *   Optional additional regex patterns to filter out
   * @return
   *   The filtered list of paths
   */
  def filterPaths(
    paths: Seq[String],
    includeMacOsFilter: Boolean = true,
    additionalFilters: Seq[String] = Seq.empty
  ): Seq[String] =
    // Compile additional regex patterns
    val additionalRegexes = additionalFilters.map(_.r)

    // Create the combined filter function
    val filterFn: String => Boolean = path =>
      val normalizedPath = normalizeSeparators(path)

      // Check macOS filter if enabled
      val macOsFiltered = if includeMacOsFilter then
        !isMacOsMetadata(normalizedPath)
      else
        true

      // Check additional filters
      val additionalFiltered =
        !additionalRegexes.exists(regex => regex.findFirstIn(normalizedPath).isDefined)

      macOsFiltered && additionalFiltered

    // Apply the filter
    paths.filter(filterFn)
  end filterPaths
end PathFilter
