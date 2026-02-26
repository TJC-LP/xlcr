# Dockerfile
# Multi-stage build for XLCR native image workflow.
#
# Stages:
#   base      - GraalVM 25.0.2 + Mill 1.1.2 + source + Python fixtures
#   assembly  - Build assembly JAR + download GraalVM for agent
#   agent     - Run GraalVM tracing agent to capture native-image metadata
#   native    - Build native binary via Mill nativeImage
#   runtime   - Self-contained container with JVM JAR + native binary + fixtures
#
# Usage:
#   docker compose build              # Build all stages
#   docker compose run --rm test      # Run test suite
#   docker compose run --rm bench     # Run benchmarks
#   docker compose run --rm agent     # Run tracing agent
#
# Or with make:
#   make docker-test
#   make docker-bench
#   make docker-agent
#   make docker-cycle

# ============================================================================
# Stage 1: base — shared foundation
# ============================================================================
FROM ubuntu:24.04 AS base

# Install build tools, Python, and external tools for comprehensive tracing
RUN apt-get update && apt-get install -y \
    curl \
    make \
    build-essential \
    zlib1g-dev \
    python3 \
    python3-pip \
    file \
    tesseract-ocr \
    libimage-exiftool-perl \
    imagemagick \
    ffmpeg \
    fonts-liberation \
    fonts-dejavu-core \
    libreoffice-core \
    libreoffice-writer \
    libreoffice-calc \
    libreoffice-impress \
    && pip3 install --break-system-packages openpyxl python-docx fpdf2 Pillow \
    && rm -rf /var/lib/apt/lists/*

# Install GraalVM CE as the system JDK (matches jvmId in package.mill)
ARG GRAALVM_VERSION=25.0.2
RUN ARCH=$(dpkg --print-architecture) && \
    if [ "$ARCH" = "arm64" ]; then GRAAL_ARCH="aarch64"; else GRAAL_ARCH="x64"; fi && \
    curl -fL "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_VERSION}/graalvm-community-jdk-${GRAALVM_VERSION}_linux-${GRAAL_ARCH}_bin.tar.gz" \
      | tar xz -C /opt && \
    ln -s /opt/graalvm-community-openjdk-* /opt/graalvm

ENV JAVA_HOME=/opt/graalvm
ENV PATH="${JAVA_HOME}/bin:${PATH}"
# libawt.so depends on libjvm.so (in lib/server/) — needed by native binary for AWT
ENV LD_LIBRARY_PATH="${JAVA_HOME}/lib:${JAVA_HOME}/lib/server"

# Install Mill (same version as .mill-version)
ARG MILL_VERSION=1.1.2
RUN curl -fLo /usr/local/bin/mill \
      "https://repo1.maven.org/maven2/com/lihaoyi/mill-dist/${MILL_VERSION}/mill-dist-${MILL_VERSION}-mill.sh" && \
    chmod +x /usr/local/bin/mill

WORKDIR /xlcr

# Copy build definitions first for better layer caching
COPY build.mill .mill-version .mill-jvm-opts .scalafmt.conf ./
COPY mill ./
COPY core/package.mill core/package.mill
COPY core-aspose/package.mill core-aspose/package.mill
COPY core-libreoffice/package.mill core-libreoffice/package.mill
COPY xlcr/package.mill xlcr/package.mill

# Copy source code and resources
COPY core/src core/src
COPY core-aspose/src core-aspose/src
COPY core-aspose/resources core-aspose/resources
COPY core-libreoffice/src core-libreoffice/src
COPY xlcr/src xlcr/src

# Copy Aspose license if available (glob trick: no-op if missing)
COPY Aspose.Total.Java.li[c] core-aspose/resources/

# Generate test fixtures
COPY scripts/testdata/test.html /xlcr/testdata/test.html
COPY scripts/testdata/gen-test-xlsx.py /xlcr/testdata/gen-test-xlsx.py
COPY scripts/testdata/gen-test-docx.py /xlcr/testdata/gen-test-docx.py
COPY scripts/testdata/gen-test-pdf.py /xlcr/testdata/gen-test-pdf.py
COPY scripts/testdata/gen-test-eml.py /xlcr/testdata/gen-test-eml.py
COPY scripts/testdata/gen-test-csv.py /xlcr/testdata/gen-test-csv.py
COPY scripts/testdata/gen-test-zip.py /xlcr/testdata/gen-test-zip.py
COPY scripts/testdata/gen-test-images.py /xlcr/testdata/gen-test-images.py
RUN python3 /xlcr/testdata/gen-test-xlsx.py /xlcr/testdata/test.xlsx
RUN python3 /xlcr/testdata/gen-test-docx.py /xlcr/testdata/test.docx
RUN python3 /xlcr/testdata/gen-test-pdf.py /xlcr/testdata/test.pdf
RUN python3 /xlcr/testdata/gen-test-eml.py /xlcr/testdata/test.eml
RUN python3 /xlcr/testdata/gen-test-csv.py /xlcr/testdata/test.csv
RUN python3 /xlcr/testdata/gen-test-zip.py /xlcr/testdata/test.zip
RUN python3 /xlcr/testdata/gen-test-images.py /xlcr/testdata

# ============================================================================
# Stage 2: assembly — build JAR + download GraalVM for agent
# ============================================================================
FROM base AS assembly

# Build assembly JAR
RUN mill xlcr.assembly

# Download GraalVM (needed for tracing agent library)
RUN mill xlcr.nativeImageTool

# ============================================================================
# Stage 3: agent — run GraalVM tracing agent
# ============================================================================
FROM assembly AS agent

COPY scripts/test-conversions.sh /xlcr/scripts/test-conversions.sh
RUN chmod +x /xlcr/scripts/test-conversions.sh

# Create metadata output directory
RUN mkdir -p /metadata-output
ENV METADATA_DIR=/metadata-output

ENTRYPOINT ["/xlcr/scripts/test-conversions.sh", "--mode", "agent"]

# ============================================================================
# Stage 4: native — build native binary
# ============================================================================
FROM base AS native

# Build native binary - Mill downloads GraalVM CE 25.0.2 automatically
RUN mill xlcr.nativeImage

# Binary is at /xlcr/out/xlcr/nativeImage.dest/native-executable

# ============================================================================
# Stage 5: runtime — self-contained test/benchmark container
# ============================================================================
FROM base AS runtime

# Copy assembly JAR from assembly stage (only the assembly output, not all of /xlcr/out)
COPY --from=assembly /xlcr/out/xlcr /xlcr/out/xlcr

# Copy native binary from native stage
COPY --from=native /xlcr/out/xlcr/nativeImage.dest/native-executable /xlcr/xlcr-native

# Copy test/benchmark script
COPY scripts/test-conversions.sh /xlcr/scripts/test-conversions.sh
RUN chmod +x /xlcr/scripts/test-conversions.sh

# Server port (for use as HTTP server)
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=3 --start-period=30s \
  CMD curl -sf http://localhost:8080/health || exit 1

# Default: run test suite
CMD ["/xlcr/scripts/test-conversions.sh", "--mode", "both"]
