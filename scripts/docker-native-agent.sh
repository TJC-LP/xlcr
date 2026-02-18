#!/usr/bin/env bash
# docker-native-agent.sh
# Host-side script (macOS/Linux) that orchestrates the Docker-based
# GraalVM tracing agent to capture native-image metadata.
#
# Steps:
#   1. Build Docker image with assembly JAR + GraalVM
#   2. Run all conversion paths with tracing agent
#   3. Extract accumulated metadata from container
#   4. Copy to xlcr/src/main/resources/META-INF/native-image/
#
# Usage:
#   make docker-native-agent
#   # or directly:
#   ./scripts/docker-native-agent.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

DOCKER_IMAGE="xlcr-agent-builder"
DOCKER_CONTAINER="xlcr-agent-run"
DOCKER_MEMORY="16g"
METADATA_DEST="$PROJECT_DIR/xlcr/src/main/resources/META-INF/native-image"

cd "$PROJECT_DIR"

echo "╔══════════════════════════════════════════════════════════╗"
echo "║  XLCR Native Image Tracing Agent                       ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  Builds assembly JAR + runs GraalVM tracing agent for  ║"
echo "║  all conversion paths to capture metadata.              ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# ── Step 1: Build Docker image ──────────────────────────────────────
echo "Step 1/4: Building Docker image..."
docker build --memory="$DOCKER_MEMORY" -f Dockerfile.agent -t "$DOCKER_IMAGE" .
echo ""

# ── Step 2: Run tracing agent ───────────────────────────────────────
echo "Step 2/4: Running tracing agent for all conversion paths..."
docker rm "$DOCKER_CONTAINER" 2>/dev/null || true
docker run --memory="$DOCKER_MEMORY" --name "$DOCKER_CONTAINER" "$DOCKER_IMAGE"
echo ""

# ── Step 3: Extract metadata ───────────────────────────────────────
echo "Step 3/4: Extracting metadata from container..."

# Back up existing metadata
if [ -d "$METADATA_DEST" ] && [ "$(ls -A "$METADATA_DEST" 2>/dev/null)" ]; then
    BACKUP_DIR="$METADATA_DEST.bak.$(date +%Y%m%d-%H%M%S)"
    echo "  Backing up existing metadata to: $BACKUP_DIR"
    cp -r "$METADATA_DEST" "$BACKUP_DIR"
fi

mkdir -p "$METADATA_DEST"

# Extract from container
TEMP_DIR=$(mktemp -d)
docker cp "$DOCKER_CONTAINER:/metadata-output/." "$TEMP_DIR/"
docker rm "$DOCKER_CONTAINER"

# Copy metadata files
cp -v "$TEMP_DIR"/* "$METADATA_DEST/" 2>/dev/null || true
rm -rf "$TEMP_DIR"
echo ""

# ── Step 4: Report ──────────────────────────────────────────────────
echo "Step 4/4: Metadata summary"
echo ""

if [ -f "$METADATA_DEST/reachability-metadata.json" ]; then
    ENTRY_COUNT=$(grep -c '"type"' "$METADATA_DEST/reachability-metadata.json" 2>/dev/null || echo "0")
    echo "  Metadata location: $METADATA_DEST/"
    echo "  Files:"
    ls -lh "$METADATA_DEST/" | grep -v '^total' | sed 's/^/    /'
    echo ""
    echo "  Reflection entries: $ENTRY_COUNT"
else
    echo "  WARNING: No reachability-metadata.json found!"
    echo "  Check Docker logs: docker logs $DOCKER_CONTAINER"
fi

echo ""
echo "Next steps:"
echo "  1. Review metadata: less $METADATA_DEST/reachability-metadata.json"
echo "  2. Rebuild native image: make docker-native"
echo "  3. Test: docker run --rm xlcr-native-builder /xlcr/out/xlcr/3.3.4/nativeImage.dest/native-executable --help"
