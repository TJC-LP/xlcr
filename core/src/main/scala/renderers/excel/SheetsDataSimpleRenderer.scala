package com.tjclp.xlcr
package renderers.excel

import models.excel.SheetsData
import renderers.SimpleRenderer
import types.MimeType

/**
 * Base trait for renderers that convert SheetsData models to various output formats.
 */
trait SheetsDataSimpleRenderer[I <: MimeType]
    extends SimpleRenderer[SheetsData, I]
