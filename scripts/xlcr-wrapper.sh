#!/usr/bin/env bash
# XLCR - Cross-format document conversion CLI
# Wrapper script that handles JAR location and Java execution

set -e

# Find JAR location
if [[ -f "$HOME/lib/xlcr/xlcr.jar" ]]; then
    XLCR_JAR="$HOME/lib/xlcr/xlcr.jar"
elif [[ -f "/usr/local/lib/xlcr/xlcr.jar" ]]; then
    XLCR_JAR="/usr/local/lib/xlcr/xlcr.jar"
else
    echo "Error: xlcr.jar not found" >&2
    echo "Expected locations:" >&2
    echo "  ~/lib/xlcr/xlcr.jar (user install)" >&2
    echo "  /usr/local/lib/xlcr/xlcr.jar (system install)" >&2
    echo "" >&2
    echo "Run 'make install' or 'make install-user' from the XLCR directory to install." >&2
    exit 1
fi

# Check Java is available
if ! command -v java &> /dev/null; then
    echo "Error: Java not found" >&2
    echo "Please install Java 17 or later." >&2
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [[ "$JAVA_VERSION" -lt 17 ]]; then
    echo "Error: Java 17+ required (found Java $JAVA_VERSION)" >&2
    echo "Please upgrade your Java installation." >&2
    exit 1
fi

# JVM options for Scala 3 LazyVals and module access
JVM_OPTS=(
    "--add-opens=java.base/sun.misc=ALL-UNNAMED"
    "-XX:+IgnoreUnrecognizedVMOptions"
)

# Memory options (can be overridden via JAVA_OPTS)
MEMORY_OPTS=(
    "-Xmx2g"
)

# Run XLCR (filter out JVM module warnings)
java "${JVM_OPTS[@]}" "${MEMORY_OPTS[@]}" ${JAVA_OPTS:-} -jar "$XLCR_JAR" "$@" 2> >(grep -v "^WARNING: package sun.misc not in java.base$" >&2)
