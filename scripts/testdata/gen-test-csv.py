#!/usr/bin/env python3
"""Generate a minimal CSV file for native-image tracing agent tests."""
import sys
import csv

output = sys.argv[1] if len(sys.argv) > 1 else "test.csv"

with open(output, "w", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["Name", "Value", "Category"])
    writer.writerow(["Alpha", "100", "A"])
    writer.writerow(["Beta", "200", "B"])
    writer.writerow(["Gamma", "300", "A"])
    writer.writerow(["Delta", "400", "B"])

print(f"Generated {output}")
