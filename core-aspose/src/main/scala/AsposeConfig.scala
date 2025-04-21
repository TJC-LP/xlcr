package com.tjclp.xlcr

/**
 * AsposeConfig is a scopt config object for parsing CLI arguments,
 * including paths to individual Aspose product licenses or a total license.
 * 
 * If no license paths are specified, the system will automatically try to detect
 * license files from:
 * 1. The current working directory (where the command is run)
 * 2. The classpath resources
 *
 * License files it will look for:
 * - Aspose.Java.Total.lic (for all products together)
 * - Aspose.Java.Words.lic (for Word-specific license)
 * - Aspose.Java.Cells.lic (for Excel-specific license)
 * - Aspose.Java.Email.lic (for Email-specific license)
 * - Aspose.Java.Slides.lic (for PowerPoint-specific license)
 * - Aspose.Java.Zip.lic (for ZIP/archive-specific license)
 */
case class AsposeConfig(
                       input: String = "",
                       output: String = "",
                       diffMode: Boolean = false,
                       splitMode: Boolean = false,
                       splitStrategy: Option[String] = None,
                       outputType: Option[String] = None,
                       outputFormat: Option[String] = None,
                       maxImageWidth: Int = 2000,
                       maxImageHeight: Int = 2000,
                       maxImageSizeBytes: Long = 1024 * 1024 * 5, // 5MB default
                       imageDpi: Int = 300,
                       jpegQuality: Float = 0.85f,
                       recursiveExtraction: Boolean = false,
                       maxRecursionDepth: Int = 5,
                       licenseWords: Option[String] = None,
                       licenseCells: Option[String] = None,
                       licenseEmail: Option[String] = None,
                       licenseSlides: Option[String] = None,
                       licenseZip: Option[String] = None,
                       licenseTotal: Option[String] = None
                     )