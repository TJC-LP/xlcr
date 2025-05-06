# Scala Version Compatibility

This project supports both Scala 2.12 and Scala 3, with the following compatibility matrix:

| Module              | Scala 2.12 | Scala 2.13  | Scala 3 |
|---------------------|------------|-------------|---------|
| core                | ✅         | ✅           | ✅       |
| core-aspose         | ✅         | ✅           | ✅       |
| core-spreadsheetllm | ✅         | ✅           | ✅       |
| core-spark          | ✅         | ✅           | ✅       |
| server              | ✅         | ✅           | ✅       |

## Why Different Scala Versions?

- **Scala 3** is the future of Scala, providing better type safety, cleaner syntax, and better tooling.
- **Scala 2.12** remains the default for broad compatibility with existing libraries.
- **Scala 2.13** is required for newer Spark versions (core-spark module only).

## Compiling with Different Scala Versions

The project provides custom SBT commands to compile for specific Scala versions:

```bash
# Compile all modules with Scala 2.12 (default)
sbt compileScala2

# Compile all modules with Scala 2.13
sbt compileScala213

# Compile all modules with Scala 3.3.4
sbt compileScala3
```

## Code Organization

Source code is organized to support multiple Scala versions:

- `src/main/scala`: Common code that works in all Scala versions
- `src/main/scala-2`: Scala 2.x specific code
- `src/main/scala-3`: Scala 3.x specific code

The build system automatically selects the appropriate source directories based on the Scala version being used.

## Spark Module Support for Scala 3

The `core-spark` module is now fully compatible with Scala 3! Key implementations include:

1. A Scala 3 compatible version of `UdfHelpers` that uses `ClassTag` instead of `TypeTag`
2. Explicit schema definitions for UDFs in Scala 3 to avoid reflection limitations
3. Updated lambda syntax compatible with Scala 3's stricter requirements

Since Apache Spark doesn't provide native Scala 3 artifacts yet, we use the Scala 2.13 artifacts with Scala 3 through the `CrossVersion.for3Use2_13` setting in the build configuration. This works because Scala 3 maintains binary compatibility with Scala 2.13.

### Spark Module Implementation Details

- Using the Scala 3 compatible UDF wrapper requires lambda syntax with parentheses:
  ```scala
  // Scala 3 compatible lambda syntax for UDFs:
  wrapUdf(name, timeout) { (bytes: Array[Byte]) => ... }
  
  // Instead of Scala 2 style:
  wrapUdf(name, timeout) { bytes: Array[Byte] => ... }
  ```
  
- The UDF implementation for Scala 3 uses explicit schema definition to work around the lack of TypeTag support in Scala 3