package com.tjclp.xlcr

case class Config(input: String = "", output: String = "")

object Config:
  def parse(args: Array[String]): Config =
    if args.length != 2 then
      throw new IllegalArgumentException("Expected 2 args")
    Config(args(0), args(1))