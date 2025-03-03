# XLCR Development Guide

## Build Commands
- `sbt compile` - Compile the project
- `sbt assembly` - Create executable JAR files
- `sbt run` - Run the application
- `sbt "server/run"` - Run the server component

## Test Commands
- `sbt test` - Run all tests
- `sbt "testOnly com.tjclp.xlcr.ConfigSpec"` - Run a single test class
- `sbt "testOnly com.tjclp.xlcr.ConfigSpec -- -z 'parse valid command line arguments'"` - Run a specific test case

## Code Style
- Scala 3 with functional programming principles
- Immutable data structures preferred
- Package organization follows com.tjclp.xlcr convention
- CamelCase for methods/variables, PascalCase for classes/objects
- Prefer Option/Either for error handling over exceptions
- Bridges follow standard patterns (SimpleBridge, SymmetricBridge, etc.)
- Make illegal states unrepresentable through type system
- Models should be immutable case classes with well-defined interfaces
- Parser/Renderer pairs should be symmetric