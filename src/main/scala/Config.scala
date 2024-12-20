package com.tjclp.xlcr

case class Config(input: String = "", output: String = "")

object Config:
  def parse(args: Array[String]): Config =
    if args.length != 2 then
      println("Usage: xlcr <input-file> <output-file>")
      sys.exit(1)

    Config(
      input = args(0),
      output = args(1)
    )
