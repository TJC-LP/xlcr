#!/usr/bin/env python3
"""Generate a minimal ZIP file for native-image tracing agent tests."""
import sys
import zipfile

output = sys.argv[1] if len(sys.argv) > 1 else "test.zip"

with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as zf:
    zf.writestr("readme.txt",
        "This is a minimal test ZIP used by the GraalVM tracing agent "
        "to capture java.util.zip reflection metadata for native image builds."
    )
    zf.writestr("data.csv", "Name,Value\nAlpha,100\nBeta,200\n")

print(f"Generated {output}")
