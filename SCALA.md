# Scala Version Compatibility

This project supports both Scala 2.12 and Scala 3, with the following compatibility matrix:

| Module             | Scala 2.12 | Scala 2.13 | Scala 3    |
|--------------------|------------|------------|------------|
| core               | ✅         | ❌         | ✅         |
| core-aspose        | ✅         | ❌         | ✅         |
| core-spreadsheetllm| ✅         | ❌         | ✅         |
| core-spark         | ✅         | ✅         | ❌         |
| server             | ✅         | ❌         | ✅         |

## Why Different Scala Versions?

- **Scala 3** is the future of Scala, providing better type safety, cleaner syntax, and better tooling.
- **Scala 2.12** remains the default for broad compatibility with existing libraries.
- **Scala 2.13** is required for newer Spark versions (core-spark module only).

## Compiling with Different Scala Versions

The project provides custom SBT commands to compile for specific Scala versions:

```bash
# Compile all modules with Scala 2.12 (default)
sbt compileScala2

# Compile only Scala 3 compatible modules with Scala 3.3.4
sbt compileScala3
```

## Code Organization

Source code is organized to support multiple Scala versions:

- `src/main/scala`: Common code that works in all Scala versions
- `src/main/scala-2`: Scala 2.x specific code
- `src/main/scala-3`: Scala 3.x specific code

The build system automatically selects the appropriate source directories based on the Scala version being used.

## Spark Module Limitations

The `core-spark` module is only compatible with Scala 2.12 and 2.13 due to:

1. Spark itself only supporting Scala 2.12 and 2.13
2. Some Scala 3 compatibility issues with Spark APIs

If you need to work with the Spark module, make sure to use Scala 2.12 or 2.13.