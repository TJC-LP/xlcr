package com.tjclp.xlcr
package renderers.excel

import models.excel.SheetsData
import renderers.Renderer
import types.MimeType

trait SheetsDataRenderer[I <: MimeType] extends Renderer[SheetsData, I]
