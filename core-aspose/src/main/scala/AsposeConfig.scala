package com.tjclp.xlcr

/**
 * AsposeConfig is a scopt config object for parsing CLI arguments,
 * including paths to individual Aspose product licenses or a total license.
 */
case class AsposeConfig(
                         input: String = "",
                         output: String = "",
                         diffMode: Boolean = false,
                         licenseWords: Option[String] = None,
                         licenseCells: Option[String] = None,
                         licenseEmail: Option[String] = None,
                         licenseSlides: Option[String] = None,
                         licenseTotal: Option[String] = None
                       )
