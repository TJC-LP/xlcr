package com.tjclp.xlcr
package renderers.excel

import types.MimeType
import renderers.Renderer
import models.excel.SheetsData

trait SheetsDataRenderer[I <: MimeType] extends Renderer[SheetsData, I]
