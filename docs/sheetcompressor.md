# Detailed Description of SheetCompressor Framework Image

The image illustrates the SheetCompressor framework, showing a step-by-step process of how spreadsheet data is compressed through three techniques.

## Overview
The image is divided into three main sections from left to right, representing the three sequential compression techniques:
1. Structural-anchor-based Extraction
2. Inverted-index Translation
3. Data-format-aware Aggregation

## Left Section: Structural-anchor-based Extraction
This section shows three stages (a, b, c) of the first compression technique:

- Image (a): The original spreadsheet with many rows and columns. It appears as a dense grid with lots of data cells. At the bottom is a small table showing sample data with companies (MindMeld, AquaQuest) and numerical values.

- Image (b): The same spreadsheet with highlighted "candidate boundary" areas marked in yellow. Green text labels explain this step: "Propose candidate boundary as structural anchors"

- Image (c): A significantly reduced spreadsheet after removing distant rows/columns. Text explains: "Remove rows/cols that are k row/cols away from anchors". The resulting sheet is much smaller (24×8) with proper coordinate rearrangement (noted at top).

Below image (c) is a text example of the encoded data format with actual values:
```
|A4, El Dorado|B4, QuantumMind|C4, 1,172,295|D4, 20-Aug|
|F4, |F4, QuantumMind|G4, |H4, |\n
|A5, Lemuria|B5, DreamSculpt|C5, |D5, 20-Aug|
|E5, |F5, Atlantis|G5, 1,797,915|H5, 9.13%|\n
...
```

## Middle Section: Inverted-index Translation
This shows the output of the second compression technique:

- A visualization of data structured in a different format, with values grouped together
- Sample categories like "Sub Region", "Atlantis", "QuantumMind" shown as boxes with cell references
- JSON-like encoding format shown below:
```
{
  "Sub Region": A1,
  "Atlantis": A2,A7,F5,A10,...
  "QuantumMind": B2:B4,F4,
  "20-Aug": D2:D18,D21:D23,
  "1064955": C2,
  "19700822": G16,
  "9.13%": H5,
  "100.00%": H16, ...
}
```

## Right Section: Data-format-aware Aggregation
This shows the final compression technique:

- Similar visualization with boxes but now with data types instead of specific values
- Values have been replaced with format types (e.g., "yy-mm", "IntNum", "Percentage")
- Final JSON-like encoding format:
```
{
  "Sub Region": A1,F1,
  "QuantumMind": B2:B4,F4,
  "yy-mm": D2:D18,D21:D23,
  "IntNum": C2:C4,C6:C12,...
  "Percentage": H5:H6,H8:H9,...
  ...
}
```

## Bottom Labels
Clear labels identify each of the three main techniques:
1. Structural-anchor-based Extraction
2. Inverted-index Translation
3. Data-format-aware Aggregation

The overall visual demonstrates how a large spreadsheet with 576 rows and 23 columns (requiring 61,240 tokens in vanilla encoding) is progressively compressed to just 708 tokens while preserving its essential structure and meaning.