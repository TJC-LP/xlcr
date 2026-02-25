#!/usr/bin/env bash
# xlcr-native-wrapper.sh
# Wrapper for the XLCR native binary that sets java.home for AWT font support.
# Required on Linux because native-image AWT needs java.home to locate fonts.
#
# Deploy this script alongside the native binary:
#   /usr/local/bin/xlcr         <- this script
#   /usr/local/lib/xlcr/xlcr   <- the native binary

set -e

NATIVE_BINARY_DIR="/usr/local/lib/xlcr"
NATIVE_BINARY="${XLCR_NATIVE_BINARY:-$NATIVE_BINARY_DIR/xlcr}"

if [[ ! -f "$NATIVE_BINARY" ]]; then
    # Fall back to searching alongside this script
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    NATIVE_BINARY="$SCRIPT_DIR/xlcr"
    if [[ ! -f "$NATIVE_BINARY" ]]; then
        echo "Error: xlcr native binary not found" >&2
        exit 1
    fi
fi

# Resolve java.home for AWT font support
# Try: JAVA_HOME env, then detect from 'java' on PATH, then common locations
if [[ -n "$JAVA_HOME" ]]; then
    XLCR_JAVA_HOME="$JAVA_HOME"
elif command -v java &>/dev/null; then
    XLCR_JAVA_HOME=$(java -XshowSettings:property -version 2>&1 | awk '/java.home/{print $NF}')
elif [[ -d "/usr/lib/jvm/java-21-openjdk-arm64" ]]; then
    XLCR_JAVA_HOME="/usr/lib/jvm/java-21-openjdk-arm64"
elif [[ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]]; then
    XLCR_JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
elif [[ -d "/usr/lib/jvm/default-java" ]]; then
    XLCR_JAVA_HOME="/usr/lib/jvm/default-java"
else
    echo "Warning: java.home not found - font rendering may fail" >&2
fi

exec "$NATIVE_BINARY" \
    ${XLCR_JAVA_HOME:+-Djava.home="$XLCR_JAVA_HOME"} \
    -Djava.awt.headless=true \
    "$@"
