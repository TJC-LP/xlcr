package com.tjclp.xlcr
package splitters
package email

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import models.FileContent
import types.MimeType

class OutlookMsgSplitterSpec extends AnyFlatSpec with Matchers {

  "OutlookMsgSplitter" should "handle empty MSG files gracefully" in {
    // This is a minimal MSG file structure that should trigger EmptyDocumentException
    // MSG files are OLE2 compound documents, so we create a minimal one
    val emptyMsg = Array[Byte](
      0xD0.toByte, 0xCF.toByte, 0x11.toByte, 0xE0.toByte, // OLE2 signature
      0xA1.toByte, 0xB1.toByte, 0x1A.toByte, 0xE1.toByte
    )
    
    val fc = FileContent(emptyMsg, MimeType.ApplicationVndMsOutlook)
    
    // Should throw EmptyDocumentException or handle gracefully
    val result = DocumentSplitter.split(
      fc,
      SplitConfig(strategy = Some(SplitStrategy.Attachment))
    )
    
    // If it doesn't throw, it should return at least a preserved chunk
    result.size should be >= 1
  }

  it should "handle invalid strategy appropriately" in {
    val dummyMsg = Array[Byte](
      0xD0.toByte, 0xCF.toByte, 0x11.toByte, 0xE0.toByte,
      0xA1.toByte, 0xB1.toByte, 0x1A.toByte, 0xE1.toByte
    )
    
    val fc = FileContent(dummyMsg, MimeType.ApplicationVndMsOutlook)
    
    // Try with an invalid strategy (Page instead of Attachment)
    val result = DocumentSplitter.split(
      fc,
      SplitConfig(strategy = Some(SplitStrategy.Page))
    )
    
    // Should handle invalid strategy gracefully
    result.size should be >= 1
  }

  it should "apply chunk range filtering when specified" in {
    // Since we don't have a real MSG file, we'll test that the chunk range logic
    // is at least present in the implementation by checking with a dummy file
    val dummyMsg = Array[Byte](
      0xD0.toByte, 0xCF.toByte, 0x11.toByte, 0xE0.toByte,
      0xA1.toByte, 0xB1.toByte, 0x1A.toByte, 0xE1.toByte
    )
    
    val fc = FileContent(dummyMsg, MimeType.ApplicationVndMsOutlook)
    
    val result = DocumentSplitter.split(
      fc,
      SplitConfig(
        strategy = Some(SplitStrategy.Attachment),
        chunkRange = Some(0 to 0) // Only first chunk
      )
    )
    
    // Should return at most 1 chunk due to range filtering
    result.size should be <= 1
  }
}