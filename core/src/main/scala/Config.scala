package com.tjclp.xlcr

case class Config(input: String = "", output: String = "", diffMode: Boolean = false)

object Config:
  def parse(args: Array[String]): Config =
    if args.length < 2 then
      throw new IllegalArgumentException("Expected at least 2 args")
    else if args.length == 3 then
      Config(args(0), args(1), args(2).toBoolean)
    else
      Config(args(0), args(1))