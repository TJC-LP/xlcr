#!/usr/bin/env python3
"""Generate a minimal PDF for native-image tracing agent tests."""
import sys
from fpdf import FPDF

output = sys.argv[1] if len(sys.argv) > 1 else "test.pdf"

pdf = FPDF()
pdf.add_page()
pdf.set_font("Helvetica", size=16)
pdf.cell(text="XLCR Test Document")
pdf.ln(12)
pdf.set_font("Helvetica", size=10)
pdf.cell(
    text="This is a minimal test PDF used by the GraalVM tracing agent "
    "to capture PDFBox reflection metadata for native image builds."
)

pdf.output(output)
print(f"Generated {output}")
