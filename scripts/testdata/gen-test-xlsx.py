#!/usr/bin/env python3
"""Generate a minimal multi-sheet XLSX for native-image tracing agent tests."""
import sys
from openpyxl import Workbook

output = sys.argv[1] if len(sys.argv) > 1 else "test.xlsx"

wb = Workbook()

# Sheet 1: Data
ws1 = wb.active
ws1.title = "Data"
ws1.append(["Name", "Value", "Category"])
ws1.append(["Alpha", 100, "A"])
ws1.append(["Beta", 200, "B"])
ws1.append(["Gamma", 300, "A"])

# Sheet 2: Summary (exercises multi-sheet splitting)
ws2 = wb.create_sheet("Summary")
ws2.append(["Category", "Total"])
ws2.append(["A", "=SUMIF(Data!C2:C4,A2,Data!B2:B4)"])
ws2.append(["B", "=SUMIF(Data!C2:C4,A3,Data!B2:B4)"])

wb.save(output)
print(f"Generated {output}")
