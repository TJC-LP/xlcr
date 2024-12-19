# XLCR 
## e**X**tensible **L**anguage **C**omputation **R**untime

XLCR is a powerful and flexible command-line tool designed for language processing and computation tasks. It provides a runtime environment for extracting, analyzing, and transforming textual content from various file formats.

## Features

- Extensible architecture for language computation tasks
- Supports multiple input file formats (PDF, Word, Excel, PowerPoint, etc.)
- Extracts and processes textual content from input files
- Outputs processed content in either plain text or XML format
- Customizable processing pipelines
- Simple command-line interface

## Prerequisites

- Java 11 or higher
- SBT (Scala Build Tool)

## Installation

1. Clone the repository:
   ```
   git clone https://github.com/TJC-LP/xlcr.git
   cd xlcr
   ```

2. Build the project:
   ```
   sbt compile
   ```

## Usage

To run XLCR, use the following command within the SBT console:

```
sbt
> run --input "<input_file_path>" --output "<output_file_path>"
```

For example:

```
> run --input "data/import/Sample Document.pdf" --output data/export/output.xml
```

### Command-line Options

- `--input` or `-i`: Specify the input file path (required)
- `--output` or `-o`: Specify the output file path (required)

The output format (text or XML) is determined by the file extension of the output file.

## Extending XLCR

XLCR is designed to be extensible. You can add new language processing modules by...
[Add specific instructions or refer to a development guide]

## Development

To run tests:

```
sbt test
```

To create a distributable package:

```
sbt package
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
