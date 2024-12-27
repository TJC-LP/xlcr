package com.tjclp.xlcr

import scopt.OParser

@main
def main(args: String*): Unit =
  val builder = OParser.builder[Config]
  val parser =
    import builder.*
    OParser.sequence(
      programName("xlcr"),
      head("xlcr", "1.0"),
      opt[String]('i', "input")
        .required()
        .valueName("<file>")
        .action((x, c) => c.copy(input = x))
        .text("input file path"),
      opt[String]('o', "output")
        .required()
        .valueName("<file>")
        .action((x, c) => c.copy(output = x))
        .text("output file path")
    )

  OParser.parse(parser, args, Config()) match
    case Some(config) =>
      // Call the Pipeline with the parsed configuration
      Pipeline.run(config.input, config.output)
    case _ =>
      // arguments are bad, error message will have been displayed
      sys.exit(1)
