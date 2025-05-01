package com.tjclp.xlcr
package bridges.aspose.excel

import bridges.aspose.HighPrioritySimpleBridge
import types.MimeType.{
  ApplicationPdf,
  ApplicationVndOpenXmlFormatsSpreadsheetmlSheet
}

import scala.reflect.ClassTag

/** Scala 2.12 implementation of ExcelToPdfAsposeBridge.
  * Simply extends the common implementation and provides required ClassTags.
  */
object ExcelToPdfAsposeBridge
    extends HighPrioritySimpleBridge[
      ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type,
      ApplicationPdf.type
    ]
    with ExcelToPdfAsposeBridgeImpl {

  // For Scala 2.12 compatibility, provide required ClassTags
  override implicit val mTag: ClassTag[M] = implicitly[ClassTag[M]]
  implicit val iTag
      : ClassTag[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type] =
    implicitly[ClassTag[ApplicationVndOpenXmlFormatsSpreadsheetmlSheet.type]]
  implicit val oTag: ClassTag[ApplicationPdf.type] =
    implicitly[ClassTag[ApplicationPdf.type]]
}
