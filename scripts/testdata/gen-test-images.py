#!/usr/bin/env python3
"""Generate minimal test images for native-image tracing agent tests.

Generates JPEG, PNG, and TIFF files with basic content and EXIF metadata.
Usage: gen-test-images.py <output-directory>
"""
import sys
import os
from PIL import Image, ImageDraw

outdir = sys.argv[1] if len(sys.argv) > 1 else "."

# Create a simple 200x100 image with text-like content
img = Image.new("RGB", (200, 100), color=(255, 255, 255))
draw = ImageDraw.Draw(img)
draw.rectangle([10, 10, 190, 90], outline=(0, 0, 0), width=2)
draw.text((20, 40), "XLCR Test", fill=(0, 0, 0))

# Save as JPEG
jpeg_path = os.path.join(outdir, "test.jpg")
img.save(jpeg_path, "JPEG", quality=85)
print(f"Generated {jpeg_path}")

# Save as PNG
png_path = os.path.join(outdir, "test-gen.png")
img.save(png_path, "PNG")
print(f"Generated {png_path}")

# Save as TIFF
tiff_path = os.path.join(outdir, "test.tiff")
img.save(tiff_path, "TIFF")
print(f"Generated {tiff_path}")
