# XLCR Makefile
# Build and install the XLCR document conversion CLI

SCALA_VERSION := 3.3.4
JAR_NAME := xlcr.jar
INSTALL_DIR := /usr/local/bin
JAR_DIR := /usr/local/lib/xlcr
WRAPPER_SCRIPT := scripts/xlcr-wrapper.sh

# Mill builds native binary here
NATIVE_BINARY := out/xlcr/$(SCALA_VERSION)/nativeImage.dest/native-executable
NATIVE_LIB_DIR := /usr/local/lib/xlcr
NATIVE_WRAPPER := scripts/xlcr-native-wrapper.sh

# Docker settings for native build
DOCKER_IMAGE := xlcr-native-builder
DOCKER_CONTAINER := xlcr-native-build
DOCKER_MEMORY := 24g

# License locations to check
LICENSE_ASPOSE_RESOURCES := core-aspose/resources/Aspose.Java.Total.lic
LICENSE_ROOT := Aspose.Java.Total.lic

.PHONY: all build install install-user install-native install-native-user \
        uninstall uninstall-user uninstall-native uninstall-native-user \
        clean test test-all run help check-license compile assembly \
        native native-agent docker-native docker-native-agent \
        docker-test docker-bench docker-agent docker-build docker-cycle \
        docker-extract-metadata docker-clean

all: build

# Check for Aspose license
check-license:
	@echo "Checking for Aspose license..."
	@if [ -f "$(LICENSE_ASPOSE_RESOURCES)" ]; then \
		echo "  Found: $(LICENSE_ASPOSE_RESOURCES) (will be bundled)"; \
	elif [ -f "$(LICENSE_ROOT)" ]; then \
		echo "  Found: $(LICENSE_ROOT) (will be bundled)"; \
		mkdir -p core-aspose/resources; \
		cp "$(LICENSE_ROOT)" "$(LICENSE_ASPOSE_RESOURCES)"; \
	elif [ -n "$$ASPOSE_TOTAL_LICENSE_B64" ]; then \
		echo "  Found: ASPOSE_TOTAL_LICENSE_B64 env var (runtime only, not bundled)"; \
	else \
		echo "  No license found - Aspose will run in evaluation mode"; \
	fi

# Compile all modules
compile:
	@echo "Compiling all modules..."
	./mill '__[$(SCALA_VERSION)].compile'

# Build the fat JAR with all backends
assembly: check-license
	@echo "Building XLCR assembly..."
	./mill 'xlcr[$(SCALA_VERSION)].assembly'

# Full build
build: assembly
	@echo "Build complete: out/xlcr/$(SCALA_VERSION)/assembly.dest/out.jar"
	@if [ -f "$(LICENSE_ASPOSE_RESOURCES)" ]; then \
		echo "License bundled: Aspose will be fully licensed"; \
	else \
		echo "No license bundled: Set ASPOSE_TOTAL_LICENSE_B64 at runtime for licensed mode"; \
	fi

# Build native binary via Mill's NativeImageModule (downloads GraalVM automatically)
native: check-license
	@echo "Building native binary via Mill..."
	./mill 'xlcr[$(SCALA_VERSION)].nativeImage'
	@echo "Native binary built: $(NATIVE_BINARY)"
	@ls -lh $(NATIVE_BINARY) | awk '{print "Size: " $$5}'

# Run with GraalVM tracing agent to generate native-image metadata
# Usage: make native-agent ARGS="--help"
#        make native-agent ARGS="-i test.pdf -o test.html"
# Generated config: xlcr/src/main/resources/META-INF/native-image/
native-agent: check-license
	@echo "Running with tracing agent..."
	@echo "Metadata output: xlcr/src/main/resources/META-INF/native-image/"
	./mill 'xlcr[$(SCALA_VERSION)].nativeImageAgent' $(ARGS)

# Build native binary inside Docker and extract it (no local GraalVM required)
# NOTE: Produces a Linux binary - for macOS, use 'make native'
docker-native:
	@echo "Building native binary in Docker (multi-stage)..."
	docker build --target native -t xlcr-native .
	@echo "Extracting native binary from Docker image..."
	@docker rm $(DOCKER_CONTAINER) 2>/dev/null || true
	docker create --name $(DOCKER_CONTAINER) xlcr-native
	mkdir -p out
	docker cp $(DOCKER_CONTAINER):/xlcr/out/xlcr/$(SCALA_VERSION)/nativeImage.dest/native-executable out/xlcr-native
	docker rm $(DOCKER_CONTAINER)
	@echo "Native binary extracted: out/xlcr-native"
	@ls -lh out/xlcr-native | awk '{print "Size: " $$5}'
	@echo "NOTE: This is a Linux binary. For macOS, use 'make native'."

# Run GraalVM tracing agent in Docker to capture native-image metadata
# for all conversion paths (HTML→PDF, HTML→PPTX, PDF→HTML, PDF→PPTX)
docker-native-agent: docker-agent

# Install to system (requires sudo)
install: build
	@echo "Installing XLCR to $(INSTALL_DIR)..."
	sudo mkdir -p $(JAR_DIR)
	sudo cp out/xlcr/$(SCALA_VERSION)/assembly.dest/out.jar $(JAR_DIR)/$(JAR_NAME)
	sudo cp $(WRAPPER_SCRIPT) $(INSTALL_DIR)/xlcr
	sudo chmod +x $(INSTALL_DIR)/xlcr
	@echo ""
	@echo "XLCR installed successfully!"
	@echo "  JAR: $(JAR_DIR)/$(JAR_NAME)"
	@echo "  CLI: $(INSTALL_DIR)/xlcr"
	@echo ""
	@echo "Run 'xlcr --help' to get started."

# Install to user directory (no sudo required)
install-user: build
	@echo "Installing XLCR to ~/bin..."
	mkdir -p ~/lib/xlcr ~/bin
	cp out/xlcr/$(SCALA_VERSION)/assembly.dest/out.jar ~/lib/xlcr/$(JAR_NAME)
	cp $(WRAPPER_SCRIPT) ~/bin/xlcr
	chmod +x ~/bin/xlcr
	@echo ""
	@echo "XLCR installed successfully!"
	@echo "  JAR: ~/lib/xlcr/$(JAR_NAME)"
	@echo "  CLI: ~/bin/xlcr"
	@echo ""
	@echo "Ensure ~/bin is in your PATH, then run 'xlcr --help' to get started."

# Install native binary to system (requires sudo)
# On Linux: installs binary to /usr/local/lib/xlcr/ and wrapper to /usr/local/bin/xlcr
# On macOS: installs binary directly (no wrapper needed, AWT not required)
install-native:
	@if [ ! -f "$(NATIVE_BINARY)" ]; then \
		echo "Error: Native binary not found at $(NATIVE_BINARY)"; \
		echo "Run 'make native' or 'make docker-native' first."; \
		exit 1; \
	fi
	@echo "Installing native XLCR to $(INSTALL_DIR)..."
	@if [ "$$(uname)" = "Linux" ]; then \
		sudo mkdir -p $(NATIVE_LIB_DIR); \
		sudo cp $(NATIVE_BINARY) $(NATIVE_LIB_DIR)/xlcr; \
		sudo chmod +x $(NATIVE_LIB_DIR)/xlcr; \
		sudo cp $(NATIVE_WRAPPER) $(INSTALL_DIR)/xlcr; \
		sudo chmod +x $(INSTALL_DIR)/xlcr; \
		echo "  Binary: $(NATIVE_LIB_DIR)/xlcr"; \
		echo "  Wrapper: $(INSTALL_DIR)/xlcr"; \
	else \
		sudo cp $(NATIVE_BINARY) $(INSTALL_DIR)/xlcr; \
		sudo chmod +x $(INSTALL_DIR)/xlcr; \
		echo "  CLI: $(INSTALL_DIR)/xlcr"; \
	fi
	@echo ""
	@echo "XLCR native binary installed!"
	@echo "Run 'xlcr --help' to get started."

# Install native binary to user directory (no sudo required)
install-native-user:
	@if [ ! -f "$(NATIVE_BINARY)" ]; then \
		echo "Error: Native binary not found at $(NATIVE_BINARY)"; \
		echo "Run 'make native' or 'make docker-native' first."; \
		exit 1; \
	fi
	@echo "Installing native XLCR to ~/bin..."
	@if [ "$$(uname)" = "Linux" ]; then \
		mkdir -p ~/lib/xlcr ~/bin; \
		cp $(NATIVE_BINARY) ~/lib/xlcr/xlcr; \
		chmod +x ~/lib/xlcr/xlcr; \
		sed 's|/usr/local/lib/xlcr|$(HOME)/lib/xlcr|g' $(NATIVE_WRAPPER) > ~/bin/xlcr; \
		chmod +x ~/bin/xlcr; \
		echo "  Binary: ~/lib/xlcr/xlcr"; \
		echo "  Wrapper: ~/bin/xlcr"; \
	else \
		mkdir -p ~/bin; \
		cp $(NATIVE_BINARY) ~/bin/xlcr; \
		chmod +x ~/bin/xlcr; \
		echo "  CLI: ~/bin/xlcr"; \
	fi
	@echo ""
	@echo "XLCR native binary installed!"
	@echo "Ensure ~/bin is in your PATH."

# Uninstall from system
uninstall:
	@echo "Uninstalling XLCR from $(INSTALL_DIR)..."
	sudo rm -f $(INSTALL_DIR)/xlcr
	sudo rm -rf $(JAR_DIR)
	@echo "XLCR uninstalled"

# Uninstall from user directory
uninstall-user:
	rm -f ~/bin/xlcr
	rm -rf ~/lib/xlcr
	@echo "XLCR uninstalled from ~/bin"

# Uninstall native binary from system
uninstall-native:
	sudo rm -f $(INSTALL_DIR)/xlcr
	@echo "XLCR native binary uninstalled from $(INSTALL_DIR)"

# Uninstall native binary from user directory
uninstall-native-user:
	rm -f ~/bin/xlcr
	@echo "XLCR native binary uninstalled from ~/bin"

# Clean build artifacts
clean:
	./mill clean
	@echo "Build artifacts cleaned"

# Run tests for xlcr module
test:
	./mill 'xlcr[$(SCALA_VERSION)].test'

# Run all tests
test-all:
	./mill '__[$(SCALA_VERSION)].test'

# Run the CLI directly (for development)
run:
	./mill 'xlcr[$(SCALA_VERSION)].run'

# ── Docker Compose targets (multi-stage Dockerfile) ──────────────────

# Run test suite in Docker (validates file output for JVM + native)
docker-test:
	docker compose run --rm test

# Run benchmarks in Docker (JVM vs native timing comparison)
docker-bench:
	docker compose run --rm bench

# Run GraalVM tracing agent in Docker to capture metadata
docker-agent:
	docker compose run --rm agent

# Build native image stage in Docker
docker-build:
	docker compose build --build-arg BUILDKIT_INLINE_CACHE=1 test

# Full pipeline: agent → extract metadata → build native → test
docker-cycle: docker-agent docker-extract-metadata docker-build docker-test
	@echo "Full native image cycle complete."

# Extract tracing agent metadata from Docker volume to source tree
docker-extract-metadata:
	docker compose run --rm \
		--entrypoint sh \
		-v $(PWD)/xlcr/src/main/resources/META-INF/native-image:/out \
		agent -c 'cp /metadata-output/* /out/ 2>/dev/null || echo "No metadata found in volume"'

# Clean up Docker resources
docker-clean:
	docker compose down -v --rmi local

# Show help
help:
	@echo "XLCR Makefile targets:"
	@echo ""
	@echo "  Build:"
	@echo "    make build          - Build the XLCR fat JAR"
	@echo "    make compile        - Compile all modules (faster, no assembly)"
	@echo "    make assembly       - Build the assembly JAR"
	@echo "    make native         - Build native binary (Mill downloads GraalVM)"
	@echo "    make native-agent   - Run with tracing agent to generate metadata"
	@echo "    make docker-native  - Build native binary in Docker (Linux)"
	@echo "    make docker-native-agent - Run tracing agent in Docker (capture metadata)"
	@echo ""
	@echo "  Docker Compose (multi-stage):"
	@echo "    make docker-test    - Run test suite (validates file output)"
	@echo "    make docker-bench   - Run benchmarks (JVM vs native timing)"
	@echo "    make docker-agent   - Run tracing agent (capture metadata)"
	@echo "    make docker-build   - Build all Docker stages"
	@echo "    make docker-cycle   - Full pipeline: agent → build → test"
	@echo "    make docker-extract-metadata - Copy metadata from volume to source"
	@echo "    make docker-clean   - Remove Docker resources"
	@echo ""
	@echo "  Install (JAR mode - requires Java 17+ at runtime):"
	@echo "    make install        - Install to /usr/local/bin (requires sudo)"
	@echo "    make install-user   - Install to ~/bin (no sudo)"
	@echo "    make uninstall      - Remove from /usr/local/bin"
	@echo "    make uninstall-user - Remove from ~/bin"
	@echo ""
	@echo "  Install (native mode - no Java required at runtime):"
	@echo "    make install-native      - Install native binary to /usr/local/bin"
	@echo "    make install-native-user - Install native binary to ~/bin"
	@echo "    make uninstall-native    - Remove native binary from /usr/local/bin"
	@echo "    make uninstall-native-user - Remove native binary from ~/bin"
	@echo ""
	@echo "  Development:"
	@echo "    make test           - Run xlcr module tests"
	@echo "    make test-all       - Run all module tests"
	@echo "    make run            - Run the CLI directly via Mill"
	@echo "    make clean          - Clean build artifacts"
	@echo ""
	@echo "  Information:"
	@echo "    make check-license  - Check for Aspose license"
	@echo "    make help           - Show this help message"
	@echo ""
	@echo "Environment variables:"
	@echo "  ASPOSE_TOTAL_LICENSE_B64 - Base64-encoded Aspose license"
	@echo "  LIBREOFFICE_HOME         - Path to LibreOffice installation"
