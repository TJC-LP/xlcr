#!/bin/bash

# Save current Java home if set
OLD_JAVA_HOME=$JAVA_HOME

# Set Java 17
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# Set JVM options to fix Spark issues with Java 17
export _JAVA_OPTIONS="--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED"

# Print Java version for verification
echo "Running with Java:"
java -version
echo ""

# Run the sbt command with Java 17 and fix classloader issues
cd "$(dirname "$0")"
sbt 'set coreSpark / Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat' "$@"

# Restore old Java home if it was set
if [ -n "$OLD_JAVA_HOME" ]; then
  export JAVA_HOME=$OLD_JAVA_HOME
  export PATH=$(echo $PATH | sed -e "s|$JAVA_HOME/bin:||g")
fi