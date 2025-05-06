package com.tjclp.xlcr
package renderers.excel

import models.excel.SheetsData
import renderers.Renderer
import types.MimeType

/**
 * Base trait for renderers that convert SheetsData models to various output formats.
 */
trait SheetsDataRenderer[I <: MimeType] extends Renderer[SheetsData, I]
