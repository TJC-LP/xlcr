package com.tjclp.xlcr
package types

enum OutputFormat:
  case XML, TXT

  def fileExtension: String = this match
    case XML => "xml"
    case TXT => "txt"
