# XLCR Makefile
# Build and install the XLCR document conversion CLI

SCALA_VERSION := 3.3.4
JAR_NAME := xlcr.jar
INSTALL_DIR := /usr/local/bin
JAR_DIR := /usr/local/lib/xlcr

# License locations to check
LICENSE_ASPOSE_RESOURCES := core-aspose/resources/Aspose.Java.Total.lic
LICENSE_ROOT := Aspose.Java.Total.lic

.PHONY: all build install uninstall clean test help check-license compile assembly

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

# Install to system (requires sudo)
install: build
	@echo "Installing XLCR to $(INSTALL_DIR)..."
	sudo mkdir -p $(JAR_DIR)
	sudo cp out/xlcr/$(SCALA_VERSION)/assembly.dest/out.jar $(JAR_DIR)/$(JAR_NAME)
	sudo cp bin/xlcr $(INSTALL_DIR)/xlcr
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
	cp bin/xlcr ~/bin/xlcr
	chmod +x ~/bin/xlcr
	@echo ""
	@echo "XLCR installed successfully!"
	@echo "  JAR: ~/lib/xlcr/$(JAR_NAME)"
	@echo "  CLI: ~/bin/xlcr"
	@echo ""
	@echo "Ensure ~/bin is in your PATH, then run 'xlcr --help' to get started."

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

# Show help
help:
	@echo "XLCR Makefile targets:"
	@echo ""
	@echo "  Build:"
	@echo "    make build        - Build the XLCR fat JAR"
	@echo "    make compile      - Compile all modules (faster, no assembly)"
	@echo "    make assembly     - Build the assembly JAR"
	@echo ""
	@echo "  Install:"
	@echo "    make install      - Install to /usr/local/bin (requires sudo)"
	@echo "    make install-user - Install to ~/bin (no sudo)"
	@echo "    make uninstall    - Remove from /usr/local/bin"
	@echo "    make uninstall-user - Remove from ~/bin"
	@echo ""
	@echo "  Development:"
	@echo "    make test         - Run xlcr module tests"
	@echo "    make test-all     - Run all module tests"
	@echo "    make run          - Run the CLI directly via Mill"
	@echo "    make clean        - Clean build artifacts"
	@echo ""
	@echo "  Information:"
	@echo "    make check-license - Check for Aspose license"
	@echo "    make help         - Show this help message"
	@echo ""
	@echo "Environment variables:"
	@echo "  ASPOSE_TOTAL_LICENSE_B64 - Base64-encoded Aspose license"
	@echo "  LIBREOFFICE_HOME         - Path to LibreOffice installation"
