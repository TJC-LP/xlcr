This file is a merged representation of the entire codebase, combined into a single document by Repomix.

# File Summary

## Purpose
This file contains a packed representation of the entire repository's contents.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.

## File Format
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Multiple file entries, each consisting of:
  a. A header with the file path (## File: path/to/file)
  b. The full contents of the file in a code block

## Usage Guidelines
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.

## Notes
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded

## Additional Info

# Directory Structure
```
code/
  ObjectModel/
    Boundary.cs
    CoreSheet.cs
  CellFeatures.cs
  DetectorFilters.cs
  DetectorTrimmers.cs
  HeaderReco.cs
  HeuristicTableDetector.cs
  RegionGrowthDetector.cs
  SheetMap.cs
  TableDetectionHybrid.cs
  TableDetectionMLHeuHybrid.cs
  TableSense.Heuristic.csproj
  Utils.cs
tool/
  Heuristics_tool/
    result.txt
    SheetCore.xml
  README.txt
```

# Files

## File: code/ObjectModel/Boundary.cs
```csharp
using System;

namespace SpreadsheetLLM.Heuristic
{
    internal struct Boundary : IComparable<Boundary>
    {
        public int top, bottom, left, right;

        public Boundary(int up, int down, int left, int right)
        {
            this.top = up;
            this.bottom = down;
            this.left = left;
            this.right = right;
        }

        public int this[int index]
        {
            get
            {
                switch (index)
                {
                    case 0: return top;
                    case 1: return bottom;
                    case 2: return left;
                    case 3: return right;
                    default: throw new ArgumentOutOfRangeException(nameof(index));
                }
            }
            set
            {
                switch (index)
                {
                    case 0: top = value; return;
                    case 1: bottom = value; return;
                    case 2: left = value; return;
                    case 3: right = value; return;
                    default: throw new ArgumentOutOfRangeException(nameof(index));
                }
            }
        }

        public int CompareTo(Boundary other)
        {
            if (this.top != other.top)
                return this.top.CompareTo(other.top);
            else if (this.bottom != other.bottom)
                return this.bottom.CompareTo(other.bottom);
            else if (this.left != other.left)
                return this.left.CompareTo(other.left);
            else
                return this.right.CompareTo(other.right);
        }

        public override string ToString()
        {
            return top + (top == bottom ? "" : $"[+{bottom - top}]") + "," + left + (left == right ? "" : $"[+{right - left}]");
        }
    }
}
```

## File: code/ObjectModel/CoreSheet.cs
```csharp
using System.Collections.Generic;
using SheetCore;

namespace SpreadsheetLLM.Heuristic
{
    internal class CoreSheet
    {
        private ISheet _sheet;

        public int Height => _sheet.Height;

        public int Width => _sheet.Width;

        /// <summary>
        /// 0-indexed coordinate of merged areas.
        /// </summary>
        public List<Boundary> MergedAreas { get; set; }

        public ICell this[int row, int col] => _sheet.Cell(row + 1, col + 1);
         
        public CoreSheet(SheetCore.ISheet sheet)
        {
            this._sheet = sheet;
            this.MergedAreas = new List<Boundary>();
            foreach (var mergedRegion in sheet.MergedRegions)
            {
                var mergedArea = new Boundary(
                    mergedRegion.FirstRow - sheet.FirstRow,
                    mergedRegion.LastRow - sheet.FirstRow,
                    mergedRegion.FirstColumn - sheet.FirstColumn,
                    mergedRegion.LastColumn - sheet.FirstColumn);

                this.MergedAreas.Add(mergedArea);
            }
        }
    }
}
```

## File: code/CellFeatures.cs
```csharp
using System;
using System.Linq;

namespace SpreadsheetLLM.Heuristic
{
    internal class CellFeatures
    {
        public const string DefaultContentForFomula = "0.00";
        public static readonly CellFeatures EmptyFeatureVec = new CellFeatures();

        public static void ExtractFeatures(CoreSheet sheet, out CellFeatures[,] features, out string[,] cells, out string[,] formula)
        {
            int height = sheet.Height;
            int width = sheet.Width;

            features = new CellFeatures[height, width];
            cells = new string[height, width];
            formula = new string[height, width];

            for (int i = 0; i < height; i++)
            {
                for (int j = 0; j < width; j++)
                {
                    cells[i, j] = sheet[i, j].Value.ToString().Trim();
                    if (cells[i, j] == "" && sheet[i, j].HasFormula)
                        cells[i, j] = DefaultContentForFomula;
                }
            }

            foreach (var area in sheet.MergedAreas)
            {
                for (int i = Math.Max(area.top, 0); i <= area.bottom; i++)
                {
                    for (int j = Math.Max(area.left, 0); j <= area.right; j++)
                    {
                        if (i >= height || j >= width)
                            continue;

                        // In streaming scenario, a merged cell may not have its content (upper-left) cell existing in given chunk.
                        // Then we have to set its cell value to the first avaiable one, which should be an empty cell.
                        cells[i, j] = cells[Math.Max(area.top, 0), Math.Max(area.left, 0)];
                    }
                }
            }

            for (int i = 0; i < height; i++)
            {
                for (int j = 0; j < width; j++)
                {
                    var format = sheet[i, j].Format;
                    var feature = features[i, j] = new CellFeatures()
                    {
                        HasBottomBorder = format.Border.HasBottom,
                        HasTopBorder = format.Border.HasTop,
                        HasLeftBorder = format.Border.HasLeft,
                        HasRightBorder = format.Border.HasRight,
                        HasFillColor = format.FillColor.Name != "Transparent" && format.FillColor.Name != "White",
                        HasFormula = sheet[i, j].HasFormula,
                        TextLength = cells[i, j].Length,
                    };

                    if (feature.TextLength > 0)
                    {
                        feature.AlphabetRatio = (double)cells[i, j].Count(char.IsLetter) / cells[i, j].Length;
                        feature.NumberRatio = (double)cells[i, j].Count(char.IsDigit) / cells[i, j].Length;
                        feature.SpCharRatio = (double)(cells[i, j].Count(x => x == '*' || x == '/' || x == '：' || x == '\\') + cells[i, j].Skip(1).Count(x => x == '-' || x == '+' || x == '(')) / cells[i, j].Length;
                    }

                    // Disable formula processing for now because the absolute address may not reference to actual cell position
                    formula[i, j] = string.Empty/*sheet.Cells[i, j].FormulaA1*/;
                }
            }
        }

        public bool HasBottomBorder { get; set; }

        public bool HasTopBorder { get; set; }

        public bool HasLeftBorder { get; set; }

        public bool HasRightBorder { get; set; }

        public bool HasFillColor { get; set; }

        public bool HasFormula { get; set; }

        public int TextLength { get; set; }

        public bool MarkText => TextLength > 0;

        public double AlphabetRatio { get; set; }

        public double NumberRatio { get; set; }

        public double SpCharRatio { get; set; }
    }
}
```

## File: code/DetectorFilters.cs
```csharp
using System;
using System.Linq;
using System.Collections.Generic;

namespace SpreadsheetLLM.Heuristic
{
    internal partial class TableDetectionHybrid
    {
        private void OverlapPivotFilter()
        {
            // remove candidates overlaps the pivottables, and add all pivot tables to candidates
            var removedBoxes = new HashSet<Boundary>();
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box = _boxes[i];
                if (Utils.isOverlap(box, _sheet.pivotBoxes))
                {
                    removedBoxes.Add(box);
                }
            }
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);

            foreach (var pivotBox in _sheet.pivotBoxes)
            {
                _boxes.Add(pivotBox);
            }
        }

        private void MergeFilter()
        {
            var removedBoxes = new HashSet<Boundary>();
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box = _boxes[i];
                if (Utils.ContainsBox(box, _sheet.mergeBoxes, 2) && Utils.ContainsBox(_sheet.mergeBoxes, box, 2) || _sheet.mergeBoxes.Contains(box))
                {
                    removedBoxes.Add(box);
                }
            }

            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void OverlapBorderCohensionFilter()
        {
            var removedBoxes = new HashSet<Boundary>();
            foreach (var box in _boxes)
            {
                if (Utils.isOverlap(box, _sheet.smallCohensionBorderRegions, exceptForward: true, exceptBackward: true, exceptSuppression: true))
                {
                    removedBoxes.Add(box);
                }
            }
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void OverlapCohensionFilter()
        {
            var removedBoxes = new HashSet<Boundary>();
            foreach (var box in _boxes)
            {
                if (removedBoxes.Contains(box))
                {
                    continue;
                }
                if (Utils.isOverlap(box, _sheet.conhensionRegions, exceptForward: true, exceptBackward: true))
                {
                    //foreach (var forcedRegion in sheet.forcedConhensionRegions)
                    //{
                    //    if (Utils.isOverlap(forcedRegion, box) && !Utils.isContainsBox(forcedRegion, box) && !Utils.isContainsBox(box, forcedRegion)) /// && !IsPart(box, forcedRegion)
                    //    {
                    ////var headerUp = new Boundary ( box.up, box.up, box.left, box.right );
                    ////var headerLeft = new Boundary ( box.up, box.down, box.left, box.left );
                    ////if (Utils.isOverlap(forcedRegion, headerUp) && isHeaderUp(headerUp) && headerUp[1] - headerUp[0] >= 2) { continue; }
                    ////if (Utils.isOverlap(forcedRegion, headerLeft) && isHeaderLeft(headerLeft) && headerLeft[3] - headerLeft[2] >= 2) { continue; }
                    removedBoxes.Add(box);
                    //    break;
                    //}
                    //}
                }
            }
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void EliminateOverlaps()
        {
            var removedBoxes = new HashSet<Boundary>();
            for (int i = 0; i < _boxes.Count - 1; i++)
            {
                var box1 = _boxes[i];
                if (removedBoxes.Contains(box1))
                {
                    continue;
                }
                var removedBoxes1 = new HashSet<Boundary>();
                bool markRemoval = false;
                for (int j = i + 1; j < _boxes.Count; j++)
                {
                    var box2 = _boxes[j];
                    if (removedBoxes.Contains(box2))
                    {
                        continue;
                    }
                    if (Utils.isOverlap(box1, box2))
                    {
                        if (Utils.AreaSize(box1) >= Utils.AreaSize(box2))
                        {
                            removedBoxes1.Add(box2);
                        }
                        else
                        {
                            markRemoval = true;
                            break;
                        }
                    }
                }
                if (markRemoval)
                {
                    removedBoxes.Add(box1);
                }
                else
                {
                    foreach (var box in removedBoxes1)
                    {
                        removedBoxes.Add(box);
                    }
                }
            }
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void ForcedBorderFilter()
        {
            //  preserve the _boxes same as border regions and remove the other _boxes overlap with them
            var removedBoxes = new HashSet<Boundary>();

            //  find out the _boxes same as border regions
            List<Boundary> borderRegions = new List<Boundary>();
            if (_sheet.smallCohensionBorderRegions != null)
            {
                foreach (var box2 in _sheet.smallCohensionBorderRegions)
                {
                    if (_boxes.Contains(box2))
                    {
                        borderRegions.Add(box2);
                    }
                }
            }
            // remove the other _boxes overlap with them
            foreach (var box1 in _boxes)
            {
                if (Utils.isOverlap(box1, borderRegions, exceptForward: true))

                {
                    removedBoxes.Add(box1);
                }
            }
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void LittleBoxesFilter()
        {
            // filter little and sparse _boxes
            var removedBoxes = new HashSet<Boundary>();
            foreach (var box in _boxes)
            {

                // filter thin _boxes
                #region
                if (box.bottom - box.top < 1 || box.right - box.left < 1)
                {
                    removedBoxes.Add(box);
                    continue;
                }
                else if ((box.bottom - box.top < 2 || box.right - box.left < 2) && Utils.AreaSize(box) < 8)
                {
                    if (_sheet.ContentExistValueDensity(box) < 2 * 0.7)
                    {
                        removedBoxes.Add(box);
                        continue;
                    }
                }
                else if ((box.bottom - box.top < 2 || box.right - box.left < 2) && Utils.AreaSize(box) < 24)
                {
                    if (_sheet.ContentExistValueDensity(box) < 2 * 0.6)
                    {
                        removedBoxes.Add(box);
                        continue;
                    }
                }
                else if (box.bottom - box.top < 2 || box.right - box.left < 2)
                {
                    if (_sheet.ContentExistValueDensity(box) < 2 * 0.55)
                    {
                        removedBoxes.Add(box);
                        continue;
                    }
                }
                else if (box.bottom - box.top < 3 || box.right - box.left < 3)
                {
                    if (_sheet.ContentExistValueDensity(box) < 2 * 0.35)
                    {
                        removedBoxes.Add(box);
                        continue;
                    }
                }
                #endregion

                // filter small _boxes
                #region
                if (Utils.AreaSize(box) < 7)
                {
                    removedBoxes.Add(box);
                    continue;
                }
                else if ((box.bottom - box.top < 5 && box.right - box.left < 3) || (box.bottom - box.top < 3 && box.right - box.left < 5))
                {
                    if (_sheet.ContentExistValueDensity(box) < 2 * 0.55)
                    {
                        removedBoxes.Add(box);
                        continue;
                    }
                }
                else if (box.bottom - box.top < 5 && box.right - box.left < 5)
                {
                    if (_sheet.ContentExistValueDensity(box) < 2 * 0.4)
                    {
                        removedBoxes.Add(box);
                        continue;
                    }
                }
                else if (box.bottom - box.top < 8 && box.right - box.left < 8)
                {
                    if (_sheet.ContentExistValueDensity(box) < 2 * 0.35)
                    {
                        removedBoxes.Add(box);
                        continue;
                    }
                }
                else if (box.bottom - box.top < 14 && box.right - box.left < 14)
                {
                    if (_sheet.ContentExistValueDensity(box) < 2 * 0.25)
                    {
                        removedBoxes.Add(box);
                        continue;
                    }
                }

                #endregion

                // filter thin _boxes with continuous empty rows/cols
                #region
                if (box.bottom - box.top == 2)
                {
                    Boundary boxWindow = new Boundary(box.top + 1, box.bottom - 1, box.left, box.right);
                    if (_sheet.sumContentExist.SubmatrixSum(boxWindow) <= 5 && _sheet.ContentExistValueDensity(box) < 2 * 0.45)
                    {
                        removedBoxes.Add(box);
                        continue;
                    }
                }
                if (box.right - box.left == 2)
                {
                    Boundary boxWindow = new Boundary(box.top, box.bottom, box.left + 1, box.right - 1);
                    if (_sheet.sumContentExist.SubmatrixSum(boxWindow) <= 5 && _sheet.ContentExistValueDensity(box) < 2 * 0.45)
                    {
                        removedBoxes.Add(box);
                        continue;
                    }
                }
                if (box.bottom - box.top > 3 && box.bottom - box.top < 4)
                {
                    for (int index = box.top + 1; index < box.bottom; index++)
                    {
                        Boundary boxWindow = new Boundary(index, index + 1, box.left, box.right);
                        if (_sheet.sumContentExist.SubmatrixSum(boxWindow) <= 3 && _sheet.ContentExistValueDensity(box) < 2 * 0.4)
                        {
                            removedBoxes.Add(box);
                            break;
                        }
                    }
                }
                if (box.right - box.left > 2 && box.right - box.left <= 4)
                {
                    for (int index = box.left + 2; index < box.right; index++)
                    {
                        Boundary boxWindow = new Boundary(box.top, box.bottom, index, index + 1);
                        if (_sheet.sumContentExist.SubmatrixSum(boxWindow) <= 3 && _sheet.ContentExistValueDensity(box) < 2 * 0.4)
                        {
                            removedBoxes.Add(box);
                            break;
                        }
                    }
                }
                if (box.right - box.left > 4 && box.right - box.left <= 7)
                {
                    for (int index = box.left + 2; index < box.right - 1; index++)
                    {
                        Boundary boxWindow = new Boundary(box.top, box.bottom, index, index + 1);
                        if (_sheet.sumContentExist.SubmatrixSum(boxWindow) <= 3 && _sheet.ContentExistValueDensity(box) < 2 * 0.5)
                        {
                            removedBoxes.Add(box);
                            break;
                        }
                    }
                }
                if (box.bottom - box.top > 4 && box.bottom - box.top <= 7)
                {
                    for (int index = box.top + 1; index < box.bottom - 1; index++)
                    {
                        Boundary boxWindow = new Boundary(index, index + 1, box.left, box.right);
                        if (_sheet.sumContentExist.SubmatrixSum(boxWindow) <= 3 && _sheet.ContentExistValueDensity(box) < 2 * 0.5)
                        {
                            removedBoxes.Add(box);
                            break;
                        }
                    }
                }
                #endregion

                //if (sheet.valueSumRange(box) / Utils.areaSize(box) < 2 * 0.2)
                //{
                //    removedBoxes.Add(box);
                //    continue;
                //}

            }
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void NoneBorderFilter()
        {
            var removedBoxes = new HashSet<Boundary>();
            foreach (var box in _boxes)
            {
                var boxUp = new Boundary(box.top, box.top, box.left, box.right);
                var boxDown = new Boundary(box.bottom, box.bottom, box.left, box.right);
                var boxLeft = new Boundary(box.top, box.bottom, box.left, box.left);
                var boxRight = new Boundary(box.top, box.bottom, box.right, box.right);
                if (_sheet.sumContent.SubmatrixSum(boxUp) + _sheet.sumColor.SubmatrixSum(boxUp) == 0
                    || _sheet.sumContent.SubmatrixSum(boxDown) + _sheet.sumColor.SubmatrixSum(boxDown) == 0
                    || _sheet.sumContent.SubmatrixSum(boxLeft) + _sheet.sumColor.SubmatrixSum(boxLeft) == 0
                    || _sheet.sumContent.SubmatrixSum(boxRight) + _sheet.sumColor.SubmatrixSum(boxRight) == 0)
                {
                    removedBoxes.Add(box);
                }
            }

            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void SplittedEmptyLinesFilter()
        {// find out continuous empty rows/cols that can split the box into two irrelevant regions
            var removedBoxes = new HashSet<Boundary>();
            foreach (var box in _boxes)
            {
                if (removedBoxes.Contains(box))
                {
                    continue;
                }
                if (!VerifyBoxSplit(box))
                {
                    removedBoxes.Add(box);
                }
            }
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        #region overlap header filter
        private void AdjoinHeaderFilter()
        {
            // two candidate _boxes, with their headers overlapping each others
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();

            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                for (int j = i + 1; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];

                    if (box1.Equals(box2)) continue;
                    //overlap
                    if (!Utils.isOverlap(box1, box2)) continue;
                    // overlap header
                    if (!((box1.top == box2.top && box2.bottom - box2.top > 4 && box1.bottom - box1.top > 4 && IsHeaderUp(new Boundary(box1.top, box1.top, Math.Min(box1.left, box2.left), Math.Max(box1.right, box2.right))))
                        || (box1.left == box2.left && box2.right - box2.left > 4 && box1.right - box1.left > 4 && IsHeaderLeft(new Boundary(Math.Min(box1.top, box2.top), Math.Max(box1.bottom, box2.bottom), box1.left, box1.left)))))
                        continue;
                    Boundary boxMerge = Utils.UnifyBox(box1, box2);
                    // there are no other boxe overlaps them
                    bool markOverlap = false;
                    foreach (var box3 in _boxes)
                    {
                        if (box1.Equals(box3) || box2.Equals(box3)) continue;
                        if (Utils.isOverlap(boxMerge, box3)) { markOverlap = true; break; }
                    }
                    // remove them and append the merged
                    if (!markOverlap)
                    {
                        if (!box1.Equals(boxMerge))
                        {
                            removedBoxes.Add(box1);
                        }
                        if (!box2.Equals(boxMerge))
                        {
                            removedBoxes.Add(box2);
                        }
                        appendBoxes.Add(boxMerge);
                    }
                }
            }

            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
        }

        private void OverlapUpHeaderFilter()
        {
            // overlap other _boxes' up header
            var removedBoxes = new HashSet<Boundary>();
            // find out upheaders of all _boxes
            List<Boundary> upHeaders = _sheet.FindoutUpheaders(this, _boxes);

            #region filter _boxes based on headers
            foreach (var box in _boxes)
            {
                foreach (var headerBox in upHeaders)
                {
                    Boundary upsideOfHeader = new Boundary(headerBox.top - 1, headerBox.bottom - 1, headerBox.left, headerBox.right);
                    // incorrectly with an upheader inside the data area, and they share the same left and right boundary lines
                    if (((upsideOfHeader.left == box.left && upsideOfHeader.right == box.right)
                        || (Math.Abs(upsideOfHeader.left - box.left) <= 1 && Math.Abs(upsideOfHeader.right - box.right) <= 1 && box.right - box.left > 5)
                        || (Math.Abs(upsideOfHeader.left - box.left) <= 2 && Math.Abs(upsideOfHeader.right - box.right) <= 2 && box.right - box.left > 10)
                        || (Math.Abs(box.bottom - upsideOfHeader.top - 1) < 2 && upsideOfHeader.right - upsideOfHeader.left > 3)
                       )
                        && Utils.isOverlap(box, upsideOfHeader) && Math.Abs(upsideOfHeader.top - box.top) > 1)// && box.left == forcedbox.left && box.right == forcedbox.right)
                    {
                        removedBoxes.Add(box);
                        break;
                    }
                    // cases that a box only overlaps the right part of the header
                    if (Math.Abs(upsideOfHeader.top + 1 - box.top) <= 1 && Utils.isOverlap(box, headerBox) && box.left >= upsideOfHeader.left + 1 && box.left <= upsideOfHeader.right - 1)
                    {
                        Boundary deviationWindow = new Boundary(headerBox.top, headerBox.bottom, box.left - 2, box.left);
                        //verify if the deviationWindow is compact
                        if (_sheet.sumContentExist.SubmatrixSum(deviationWindow) >= 6 && HeaderRate(deviationWindow) == 1)
                        {
                            removedBoxes.Add(box);
                        }
                    }
                    // cases that a box only overlaps left part of the header 
                    if (Math.Abs(upsideOfHeader.top + 1 - box.top) <= 1 && Utils.isOverlap(box, headerBox) && box.right >= upsideOfHeader.left + 1 && box.right <= upsideOfHeader.right - 1)
                    {
                        Boundary deviationWindow = new Boundary(headerBox.top, headerBox.bottom, box.right, box.right + 2);
                        //verify if the deviationWindow is compact
                        if (_sheet.sumContentExist.SubmatrixSum(deviationWindow) >= 6 && HeaderRate(deviationWindow) == 1)
                        {
                            removedBoxes.Add(box);
                        }
                    }
                }
            }
            #endregion
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void OverlapHeaderFilter()
        {
            // overlap other _boxes' up header in the top or bottom region of this box
            // overlap other _boxes' left header in the left or right region of this box
            var removedBoxes = new HashSet<Boundary>();

            // find out upheaders of all _boxes
            List<Boundary> upHeaders = _sheet.FindoutUpheaders(this, _boxes);
            List<Boundary> leftHeaders = _sheet.FindoutLeftheaders(this, _boxes);

            foreach (var box in _boxes)
            {
                foreach (var upHeader in upHeaders)
                {
                    // the bottom of the box overlap the whole header box with left and right edge
                    if (!Utils.ContainsBox(box, upHeader) && Utils.isOverlap(new Boundary(Math.Max(box.bottom - 4, box.top), box.bottom, box.left - 1, box.right - 1), upHeader))
                    {
                        bool markAlternativeBox = false;
                        //there exists alternative candidates dont overlap this header
                        foreach (var box2 in _boxes)
                        {
                            if (Utils.isOverlap(box, box2) && !Utils.isOverlap(box2, upHeader) && (Math.Abs(box.right - box2.right) < 2) && (Math.Abs(box.left - box2.left) < 2))
                            {
                                markAlternativeBox = true;
                            }
                        }
                        if (markAlternativeBox)
                        {
                            removedBoxes.Add(box);
                            break;
                        }
                    }
                }
                foreach (var leftHeader in leftHeaders)
                {
                    if (!Utils.ContainsBox(box, leftHeader) && Utils.isOverlap(new Boundary(box.top - 1, box.bottom - 1, Math.Max(box.right - 5, box.left), box.right), leftHeader))
                    {
                        // find our if there are alternatives that overlap this box but not overlap forcedbox
                        bool markExistAlternative = false;
                        foreach (var box2 in _boxes)
                        {
                            if (Utils.isOverlap(box, box2) && !Utils.isOverlap(box2, leftHeader))
                            {
                                markExistAlternative = true;
                            }
                        }
                        if (markExistAlternative)
                        {
                            removedBoxes.Add(box);
                            break;
                        }
                    }
                }
            }

            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }
        #endregion

        #region general filter related
        private bool GeneralFilter(Boundary box)
        {
            if (box.bottom - box.top < 1 || box.right - box.left < 1)
            {
                return false;
            }

            // border out edge sparse
            if (!VerifyBoxBorderValueInOutSimple(box))
            {
                return false;
            }
            else if (!VerifyBoxBorderValueOutSparse(box))
            {
                return false;
            }
            //// cause wu sha for null line
            else if (!VerifyBoxBorderValueNotNull(box))
            {
                return false;
            }

            // continuous none-content line split the box into two not related regions
            else if (!VerifyBoxSplit(box))
            {
                return false;
            }

            return true;
        }

        private bool VerifyBoxBorderValueNotNull(Boundary box)
        {
            var boxUp = new Boundary(box.top, box.top, box.left, box.right);
            var boxDown = new Boundary(box.bottom, box.bottom, box.left, box.right);
            var boxLeft = new Boundary(box.top, box.bottom, box.left, box.left);
            var boxRight = new Boundary(box.top, box.bottom, box.right, box.right);
            if (_sheet.sumContent.SubmatrixSum(boxUp) + _sheet.sumColor.SubmatrixSum(boxUp) == 0
                || _sheet.sumContent.SubmatrixSum(boxDown) + _sheet.sumColor.SubmatrixSum(boxDown) == 0
                || _sheet.sumContent.SubmatrixSum(boxLeft) + _sheet.sumColor.SubmatrixSum(boxLeft) == 0
                || _sheet.sumContent.SubmatrixSum(boxRight) + _sheet.sumColor.SubmatrixSum(boxRight) == 0)
            {
                return false;
            }
            return true;
        }

        private bool VerifyBoxBorderValueInOutSimple(Boundary box)
        {
            // border inside not none, outside sparse
            var boxUp = new Boundary(box.top - 1, box.top - 1, box.left, box.right);
            int sumUp = _sheet.sumContentExist.SubmatrixSum(boxUp);
            if (sumUp >= 6)
            {
                return false;
            }
            var boxDown = new Boundary(box.bottom + 1, box.bottom + 1, box.left, box.right);
            int sumDown = _sheet.sumContentExist.SubmatrixSum(boxDown);
            if (sumDown >= 6)
            {
                return false;
            }
            var boxLeft = new Boundary(box.top, box.bottom, box.left - 1, box.left - 1);
            int sumLeft = _sheet.sumContentExist.SubmatrixSum(boxLeft);
            if (sumLeft >= 6)
            {
                return false;
            }
            var boxRight = new Boundary(box.top, box.bottom, box.right + 1, box.right + 1);
            int sumRight = _sheet.sumContentExist.SubmatrixSum(boxRight);
            if (sumRight >= 6)
            {
                return false;
            }

            boxUp = new Boundary(box.top, box.top, box.left, box.right);
            boxDown = new Boundary(box.bottom, box.bottom, box.left, box.right);
            boxLeft = new Boundary(box.top, box.bottom, box.left, box.left);
            boxRight = new Boundary(box.top, box.bottom, box.right, box.right);
            if (_sheet.sumContent.SubmatrixSum(boxUp) + _sheet.sumColor.SubmatrixSum(boxUp) == 0
                || _sheet.sumContent.SubmatrixSum(boxDown) + _sheet.sumColor.SubmatrixSum(boxDown) == 0
                || _sheet.sumContent.SubmatrixSum(boxLeft) + _sheet.sumColor.SubmatrixSum(boxLeft) == 0
                || _sheet.sumContent.SubmatrixSum(boxRight) + _sheet.sumColor.SubmatrixSum(boxRight) == 0)
            {
                return false;
            }
            return true;
        }

        private bool VerifyBoxBorderValueOutSparse(Boundary box)
        {
            var boxUp = new Boundary(box.top - 1, box.top - 1, box.left, box.right);
            var boxDown = new Boundary(box.bottom + 1, box.bottom + 1, box.left, box.right);
            var boxLeft = new Boundary(box.top, box.bottom, box.left - 1, box.left - 1);
            var boxRight = new Boundary(box.top, box.bottom, box.right + 1, box.right + 1);
            int sumUp = _sheet.sumContentExist.SubmatrixSum(boxUp);
            int sumRight = _sheet.sumContentExist.SubmatrixSum(boxRight);
            int sumDown = _sheet.sumContentExist.SubmatrixSum(boxDown);
            int sumLeft = _sheet.sumContentExist.SubmatrixSum(boxLeft);
            if (sumUp >= 6 || sumDown >= 6 || sumLeft >= 6 || sumRight >= 6)
            {
                return false;
            }
            if (box.bottom - box.top <= 2)
            {
                if (sumLeft >= 2 || sumRight >= 2)
                {
                    return false;
                }

            }
            if (box.right - box.left <= 1)
            {
                if (sumUp >= 2 || sumDown >= 2)
                {
                    return false;
                }
            }
            if (box.bottom - box.top <= 4)
            {
                if (sumLeft >= 4 || sumRight >= 4)
                {
                    return false;
                }

            }
            if (box.right - box.left <= 4)
            {
                if (sumUp >= 4 || sumDown >= 4)
                {
                    return false;
                }
            }

            return true;
        }

        private bool VerifyBoxSplit(Boundary box)
        {
            // find out continuous empty rows/cols that can split the box into two irrelevant regions
            int up = box.top;
            int down = box.bottom;
            int left = box.left;
            int right = box.right;
            // avoid up header, so from up + 2
            int upOffset = 0;
            int leftOffset = 0;
            if (box.bottom - box.top > 12) upOffset = 2;
            if (box.right - box.left > 12) leftOffset = 2;
            for (int i = up + 3 + upOffset; i < down - 4; i++)
            {
                // one row without format and  three continuous rows without contents
                Boundary edgeBox3 = new Boundary(i, i + 2, left, right);
                Boundary edgeBox1 = new Boundary(i + 1, i + 1, left, right);

                if (_sheet.sumContent.SubmatrixSum(edgeBox1) < 3)
                {
                    if (_sheet.sumContent.SubmatrixSum(edgeBox1) + _sheet.sumColor.SubmatrixSum(edgeBox1) == 0 && _sheet.sumContentExist.SubmatrixSum(edgeBox3) == 0)
                    {
                        #region find out the empty rows which are not empty in the upside and downside
                        int k = i + 3;
                        Boundary edgeBoxDown = new Boundary(k, k, left, right);
                        while (k < down)
                        {
                            edgeBoxDown = new Boundary(k, k, left, right);
                            if (_sheet.sumContent.SubmatrixSum(edgeBoxDown) > 5) break;
                            k++;
                        }
                        k = i - 1;
                        Boundary edgeBoxUp = new Boundary(k, k, left, right);
                        while (k > up)
                        {
                            edgeBoxUp = new Boundary(k, k, left, right);
                            if (_sheet.sumContent.SubmatrixSum(edgeBoxUp) > 5) break;
                            k--;
                        }
                        #endregion
                        // verify the relation of the up and down rows
                        /////// may exist some problem, may remove the right box
                        if (_sheet.sumContentExist.SubmatrixSum(edgeBoxUp) > 5 && _sheet.sumContentExist.SubmatrixSum(edgeBoxDown) > 5)
                        {
                            return false;
                        }
                    }
                    else if (_sheet.sumColor.SubmatrixSum(edgeBox1) + _sheet.sumBorderCol.SubmatrixSum(edgeBox1) < 5 && !Utils.isOverlap(edgeBox1, _sheet.conhensionRegions, exceptForward: true))
                    {
                        #region homogeneous of four corner regions in the box
                        Boundary BoxUpLeft = new Boundary(up, i + 1, left, left + 2);
                        Boundary BoxUpRight = new Boundary(up, i + 1, right - 2, right);
                        Boundary BoxDownLeft = new Boundary(i + 1, down, left, left + 2);
                        Boundary BoxDownRight = new Boundary(i + 1, down, right - 2, right);

                        double densityUpLeft = (_sheet.sumContent.SubmatrixSum(BoxUpLeft) + _sheet.sumColor.SubmatrixSum(BoxUpLeft) + _sheet.sumBorderCol.SubmatrixSum(BoxUpLeft)) / Utils.AreaSize(BoxUpLeft);
                        double densityUpRight = (_sheet.sumContent.SubmatrixSum(BoxUpRight) + _sheet.sumColor.SubmatrixSum(BoxUpRight) + _sheet.sumBorderCol.SubmatrixSum(BoxUpRight)) / Utils.AreaSize(BoxUpRight);
                        double densityDownLeft = (_sheet.sumContent.SubmatrixSum(BoxDownLeft) + _sheet.sumColor.SubmatrixSum(BoxDownLeft) + _sheet.sumBorderCol.SubmatrixSum(BoxDownLeft)) / Utils.AreaSize(BoxDownLeft);
                        double densityDownRight = (_sheet.sumContent.SubmatrixSum(BoxDownRight) + _sheet.sumColor.SubmatrixSum(BoxDownRight) + _sheet.sumBorderCol.SubmatrixSum(BoxDownRight)) / Utils.AreaSize(BoxDownRight);

                        if (densityUpLeft == 0 && densityDownLeft > 2 * 0.2)
                        {
                            return false;
                        }
                        if (densityUpRight == 0 && densityDownRight > 2 * 0.2)
                        {
                            return false;
                        }
                        if (densityDownLeft == 0 && densityUpLeft > 2 * 0.2)
                        {
                            return false;
                        }
                        if (densityDownRight == 0 && densityUpRight > 2 * 0.2)
                        {
                            return false;
                        }
                        #endregion
                    }
                }
            }
            for (int i = left + 3 + leftOffset; i < right - 4; i++)
            {
                Boundary edgeBox3 = new Boundary(up, down, i, i + 2);
                Boundary edgeBox1 = new Boundary(up, down, i + 1, i + 1);
                if (_sheet.sumContent.SubmatrixSum(edgeBox1) < 3)
                {

                    if (_sheet.sumContent.SubmatrixSum(edgeBox1) + _sheet.sumColor.SubmatrixSum(edgeBox1) == 0 && _sheet.sumContentExist.SubmatrixSum(edgeBox3) == 0)
                    {
                        #region find out the empty columns which are not empty in the leftside and rightside
                        int k = i + 3;
                        Boundary edgeBoxRight = new Boundary(up, down, k, k);
                        while (k < down)
                        {
                            edgeBoxRight = new Boundary(up, down, k, k);
                            if (_sheet.sumContent.SubmatrixSum(edgeBoxRight) > 5) break;
                            k++;
                        }

                        k = i - 1;
                        Boundary edgeBoxLeft = new Boundary(up, down, k, k);
                        while (k > up)
                        {
                            edgeBoxLeft = new Boundary(up, down, k, k);
                            if (_sheet.sumContent.SubmatrixSum(edgeBoxLeft) > 5) break;
                            k--;
                        }
                        #endregion
                        if (edgeBoxRight.right - edgeBoxLeft.right >= 3)
                        {
                            return false;
                        }

                    }
                    #region homogeneous of four corner regions in the box
                    else if (_sheet.sumColor.SubmatrixSum(edgeBox1) + _sheet.sumBorderRow.SubmatrixSum(edgeBox1) < 5 && !Utils.isOverlap(edgeBox1, _sheet.conhensionRegions, exceptForward: true))
                    {
                        Boundary BoxUpLeft = new Boundary(up, up + 2, left, i + 1);
                        Boundary BoxUpRight = new Boundary(up, up + 2, i + 1, right);
                        Boundary BoxDownLeft = new Boundary(down - 2, down, left, i + 1);
                        Boundary BoxDownRight = new Boundary(down - 2, down, i + 1, right);

                        double densityUpLeft = (_sheet.sumContent.SubmatrixSum(BoxUpLeft) + _sheet.sumColor.SubmatrixSum(BoxUpLeft) + _sheet.sumBorderRow.SubmatrixSum(BoxUpLeft)) / Utils.AreaSize(BoxUpLeft);
                        double densityUpRight = (_sheet.sumContent.SubmatrixSum(BoxUpRight) + _sheet.sumColor.SubmatrixSum(BoxUpRight) + _sheet.sumBorderRow.SubmatrixSum(BoxUpRight)) / Utils.AreaSize(BoxUpRight);
                        double densityDownLeft = (_sheet.sumContent.SubmatrixSum(BoxDownLeft) + _sheet.sumColor.SubmatrixSum(BoxDownLeft) + _sheet.sumBorderRow.SubmatrixSum(BoxDownLeft)) / Utils.AreaSize(BoxDownLeft);
                        double densityDownRight = (_sheet.sumContent.SubmatrixSum(BoxDownRight) + _sheet.sumColor.SubmatrixSum(BoxDownRight) + _sheet.sumBorderRow.SubmatrixSum(BoxDownRight)) / Utils.AreaSize(BoxDownRight);

                        if (densityUpLeft == 0 && densityUpRight / Utils.AreaSize(BoxUpRight) > 2 * 0.2)
                        {
                            return false;
                        }
                        if (densityUpRight == 0 && densityUpLeft / Utils.AreaSize(BoxUpLeft) > 2 * 0.2)
                        {
                            return false;
                        }
                        if (densityDownLeft == 0 && densityDownRight / Utils.AreaSize(BoxDownRight) > 2 * 0.2)
                        {
                            return false;
                        }
                        if (densityDownRight == 0 && densityDownLeft / Utils.AreaSize(BoxDownLeft) > 2 * 0.2)
                        {
                            return false;
                        }
                    }
                    #endregion
                }
            }

            return true;
        }
        #endregion

        #region suppression filter related
        private int CompareSuppressionBoxesHeader(Boundary box1, Boundary box2)
        {
            // compare if the up / down / left / right boundary contains header
            if (box1.top != box2.top)
            {
                bool isHeaderUp1 = IsHeaderUp(Utils.UpRow(box1));
                bool isHeaderUp2_1 = IsHeaderUp(Utils.UpRow(box2));
                bool isHeaderUp2_2 = IsHeaderUp(Utils.UpRow(box2, start: 1));
                if (isHeaderUp1 && !isHeaderUp2_1)
                {
                    return 1;
                }
                if (!isHeaderUp1 && (isHeaderUp2_1 || isHeaderUp2_2))
                {
                    return 2;
                }
            }
            if (box1.left != box2.left)
            {
                bool isHeaderLeft1 = IsHeaderLeft(Utils.LeftCol(box1));
                bool isHeaderLeft2 = IsHeaderLeft(Utils.LeftCol(box2));
                if (isHeaderLeft1 && !isHeaderLeft2)
                {
                    return 1;
                }
                if (!isHeaderLeft1 && isHeaderLeft2)
                {
                    return 2;
                }

            }
            if (box1.bottom != box2.bottom)
            {
                if (IsHeaderUp(Utils.DownRow(box1)) && !IsHeaderUp(Utils.DownRow(box2))
                    && _sheet.ContentExistValueDensity(Utils.DownRow(box2, start: -1)) < 0.2 * 2)
                {
                    return 2;
                }

            }
            if (box1.right != box2.right)
            {
                if (IsHeaderLeft(Utils.RightCol(box1)) && !IsHeaderLeft(Utils.RightCol(box2))
                    && _sheet.ContentExistValueDensity(Utils.RightCol(box2, start: -1)) < 0.2 * 2)
                {
                    return 2;
                }

            }
            return 0;
        }

        private int CompareSuppressionBoxesMerge(Boundary box1, Boundary box2)
        {
            // compare if the down/right boundary overlaps merge cells
            if (_sheet.ExistsMerged(Utils.RightCol(box1, step: 2)) && !_sheet.ExistsMerged(Utils.RightCol(box2, step: 2)))
            {
                return 2;
            }
            if (_sheet.ExistsMerged(Utils.DownRow(box1, step: 2)) && !_sheet.ExistsMerged(Utils.DownRow(box2, step: 2)))
            {
                return 2;
            }

            return 0;
        }

        private int CompareSuppressionBoxesSparsity(Boundary box1, Boundary box2)
        {
            // check if the boundaries are sparse
            Boundary boxUp = Utils.UpRow(box1);
            Boundary boxDown = Utils.DownRow(box1);
            Boundary boxLeft = Utils.LeftCol(box1);
            Boundary boxRight = Utils.RightCol(box1);

            // verify the very up/down/left/right first
            if (box1.top != box2.top && CheckSparsityofUpRow(boxUp, 1) == 2)
            {
                return 2;
            }
            if (box1.bottom != box2.bottom && CheckSparsityofDownRow(boxDown, 1) == 2)
            {
                return 2;
            }
            if (box1.left != box2.left && CheckSparsityofCol(boxLeft, 1) == 2)
            {
                return 2;
            }
            if (box1.right != box2.right && CheckSparsityofCol(boxRight, 1) == 2)
            {
                return 2;
            }

            for (int i = 0; i < box2.top - box1.top; i++)
            {
                Boundary boxUp1 = Utils.UpRow(box1, start: i);
                int valid = CheckSparsityofUpRow(boxUp1, i - box1.top + 1);
                if (valid != 0)
                {
                    return valid;
                }
            }
            for (int i = 0; i > box2.bottom - box1.bottom; i--)
            {
                Boundary boxDown1 = Utils.DownRow(box1, start: i);
                int valid = CheckSparsityofDownRow(boxDown1, -i + 1);
                if (valid != 0)
                {
                    return valid;
                }
            }

            for (int i = 0; i < box2.left - box1.left; i++)
            {
                Boundary boxLeft1 = Utils.LeftCol(box1, i);
                int valid = CheckSparsityofCol(boxLeft1, i + 1);
                if (valid != 0)
                {
                    return valid;
                }
            }
            for (int i = 0; i > box2.right - box1.right; i--)
            {
                Boundary boxRight1 = Utils.RightCol(box1, i);
                int valid = CheckSparsityofCol(boxRight1, -i + 1);
                if (valid != 0)
                {
                    return valid;
                }
            }

            return 0;
        }

        private int CompareSuppressionBoxes(Boundary box1, Boundary box2)
        {
            if (box1.Equals(box2)) return 0;

            // compare if the up / down / left / right boundary contains header
            int compareHeader = CompareSuppressionBoxesHeader(box1, box2);
            if (compareHeader != 0)
            {
                return compareHeader;
            }
            // check if the boundaries are sparse
            int comparValid = CompareSuppressionBoxesSparsity(box1, box2);
            if (comparValid != 0)
            {
                return comparValid;
            }

            // compare if the down/right boundary overlaps merge cells
            int compareMerege = CompareSuppressionBoxesMerge(box1, box2);
            if (compareMerege != 0)
            {
                return compareMerege;
            }
            return 0;
        }

        private void SuppressionSoftFilter()
        {
            // resolve suppression conficts of candidats _boxes

            // resolve the four directions of suppression separetely, respect to the order up, down, left, right
            //suppressionSoftFilter(direction: 0);
            //suppressionSoftFilter(direction: 1);
            //suppressionSoftFilter(direction: 2);
            //suppressionSoftFilter(direction: 3);

            // // resolve the four directions of suppression together
            SuppressionSoftFilter(ref _boxes, direction: 4);
        }

        private void SuppressionSoftFilter(ref List<Boundary> _, int direction)
        {
            // resolve suppression conficts of candidats _boxes
            var removedBoxes = new HashSet<Boundary>();
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                if (removedBoxes.Contains(box1))
                {
                    continue;
                }

                for (int j = i + 1; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];
                    if (removedBoxes.Contains(box2))
                    {
                        continue;
                    }
                    if (!Utils.isSuppressionBox(box1, box2, directionNum: direction) || box1.Equals(box2) || !Utils.isOverlap(box1, box2))
                    {
                        continue;
                    }

                    if (Utils.Height(box2) < 0.6 * Utils.Height(box1) || Utils.Height(box1) < 0.6 * Utils.Height(box2)
                        || Utils.Width(box2) < 0.6 * Utils.Width(box1) || Utils.Width(box1) < 0.6 * Utils.Width(box2))
                    {
                        continue;
                    }
                    #region compare two _boxes

                    Boundary boxSuppresion1;
                    Boundary boxSuppresion2;
                    if (direction == 4)
                    {
                        boxSuppresion1 = Utils.OverlapBox(box1, box2);
                        boxSuppresion2 = Utils.OverlapBox(box1, box2);
                    }
                    else
                    {
                        boxSuppresion1 = box1;
                        boxSuppresion2 = box2;
                        boxSuppresion1[direction] = box1[direction];
                        boxSuppresion2[direction] = box2[direction];

                    }

                    // 0 means the default value, 1 means choose the first one, 2 means for the second one 
                    int compareReuslt1 = CompareSuppressionBoxes(box1, boxSuppresion1);
                    int compareReuslt2 = compareReuslt1 == 0 ? CompareSuppressionBoxes(box2, boxSuppresion2) : 0;

                    #endregion
                    if (compareReuslt1 == 1 || compareReuslt2 == 2)
                    {
                        removedBoxes.Add(box2);

                    }
                    else if (compareReuslt1 == 2 || compareReuslt2 == 1)
                    {
                        removedBoxes.Add(box1);
                        break;
                    }


                }
            }
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void SuppressionHardFilter()
        {
            var removedBoxes = new HashSet<Boundary>();
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                if (removedBoxes.Contains(box1)) continue;
                for (int j = i + 1; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];
                    if (removedBoxes.Contains(box2)) continue;
                    if (Utils.Height(box2) < 0.6 * Utils.Height(box1) || Utils.Height(box1) < 0.6 * Utils.Height(box2)
                         || Utils.Width(box2) < 0.6 * Utils.Width(box1) || Utils.Width(box1) < 0.6 * Utils.Width(box2))
                    {
                        continue;
                    }
                    if (box1.Equals(box2) || !Utils.isSuppressionBox(box1, box2)) continue;
                    if (_sheet.ComputeBorderDiffsRow(box1) >= _sheet.ComputeBorderDiffsRow(box2)
                        || _sheet.ComputeBorderDiffsCol(box1) >= _sheet.ComputeBorderDiffsCol(box2))
                    {
                        removedBoxes.Add(box2);
                    }
                    else
                    {
                        removedBoxes.Add(box1);
                    }
                }
            }
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private int CheckSparsityofCol(Boundary box, int depth)
        {
            double areaCol = Utils.AreaSize(box);
            if (_sheet.ContentExistValueDensity(box) >= 2 * 0.3
                && _sheet.TextDistinctCount(box) >= Math.Max(areaCol * 0.2, 3))
            {
                return 1;
            }
            if (depth == 1 && _sheet.sumContentExist.SubmatrixSum(box) <= 4 && areaCol >= 5)
            {
                return 2;
            }

            return 0;
        }

        private int CheckSparsityofUpRow(Boundary box, int depth)
        {
            double areaRow = Utils.AreaSize(box);
            if (IsHeaderUp(box)) return 1;
            if (_sheet.ContentExistValueDensity(box) >= 2 * 0.3
                && _sheet.TextDistinctCount(box) >= Math.Max(0.2 * areaRow, 3))
            {
                return 1;
            }
            if (depth == 1 && (_sheet.sumContentExist.SubmatrixSum(box) <= 4) && areaRow >= 6)
            {
                return 2;
            }
            if (depth == 1 && (_sheet.sumContentExist.SubmatrixSum(box) <= 6) && areaRow >= 10)
            {
                return 2;
            }

            return 0;
        }

        private int CheckSparsityofDownRow(Boundary box, int depth)
        {
            double areaRow = Utils.AreaSize(box);
            if (_sheet.ContentExistValueDensity(box) >= 2 * 0.3
                && _sheet.TextDistinctCount(box) >= Math.Max(areaRow * 0.2, 3))
            {
                return 1;
            }
            if (depth == 1 && (_sheet.sumContentExist.SubmatrixSum(box) <= 4 || _sheet.TextDistinctCount(box) < 2) && areaRow >= 6)
            {
                return 2;
            }
            if (depth == 1 && (_sheet.sumContentExist.SubmatrixSum(box) <= 6
                || (_sheet.ContentExistValueDensity(box) <= 0.3 * 2 && _sheet.TextDistinctCount(box) < 3))
                && areaRow >= 10)
            {
                return 2;
            }

            return 0;
        }
        #endregion

        #region various contains filters
        private void NestingCombinationFilter()
        {
            // in nesting combination cases, filter the intermediate candidates

            List<Boundary> removedBoxes = new List<Boundary>();

            // vertival
            foreach (int left in _sheet.colBoundaryLines)
            {
                foreach (int right in _sheet.colBoundaryLines)
                {
                    if (left >= right)
                    {
                        continue;
                    }
                    List<Boundary> UpDownBoxes = new List<Boundary>();
                    foreach (var box in _boxes)
                    {
                        if (box.left >= left - 1 && box.left <= left + 3 && box.right >= right - 1 && box.right <= right + 3)
                        {
                            UpDownBoxes.Add(box);
                        }
                    }
                    removedBoxes.AddRange(FindInterCandidates(UpDownBoxes));

                }
            }

            // horizontal
            foreach (int up in _sheet.rowBoundaryLines)
            {
                foreach (int down in _sheet.rowBoundaryLines)
                {
                    if (up >= down) continue;
                    List<Boundary> LeftRightBoxes = new List<Boundary>();
                    foreach (var box in _boxes)
                    {
                        if (box.top >= up - 1 && box.bottom >= down - 1 && box.top <= up + 3 && box.bottom <= down + 3)
                        {
                            LeftRightBoxes.Add(box);
                        }
                    }
                    removedBoxes.AddRange(FindInterCandidates(LeftRightBoxes));
                }
            }

            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private static List<Boundary> FindInterCandidates(List<Boundary> ranges)
        {
            List<Boundary> iterBoxes = new List<Boundary>();
            foreach (var box in ranges)
            {
                bool markIn = false;
                bool markOut = false;
                foreach (var box2 in ranges)
                {
                    if (!box.Equals(box2))
                    {
                        if (Utils.ContainsBox(box2, box, step: 2))
                        {
                            markIn = true;
                        }
                        if (Utils.ContainsBox(box, box2, step: 2))
                        {
                            markOut = true;
                        }
                    }
                }
                if (markIn && markOut)
                {
                    iterBoxes.Add(box);
                }
            }
            return iterBoxes;
        }

        private void HeaderPriorityFilter()
        {
            var removedBoxes = new HashSet<Boundary>();
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                for (int j = 0; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];
                    if (box1.Equals(box2) || !Utils.isOverlap(box1, box2) || Utils.ContainsBox(box2, box1, 2)) continue;
                    // with similar up and down boudaries

                    if (Math.Abs(box1.top - box2.top) > 2 || Math.Abs(box1.bottom - box2.bottom) > 2)
                    {
                        continue;
                    }
                    // header are closed to each other
                    if (Math.Abs(box1.left - box2.left) <= 1) { continue; }
                    Boundary box1Left = Utils.LeftCol(box1);
                    Boundary box2Left = Utils.LeftCol(box2);
                    if (Utils.ContainsBox(box1, box2) && IsHeaderUp(box2) && !IsHeaderUp(new Boundary(box1.top, box1.top, box2.left, box2.right)))
                    {
                        removedBoxes.Add(box1);
                    }
                    else if (Utils.ContainsBox(box1, box2) && box2.left - box1.left > 3 && IsHeaderUp(box2) && !IsHeaderUp(new Boundary(box1.top, box1.top, box1.left, box2.left - 1)))
                    {
                        removedBoxes.Add(box1);
                    }
                    else if (IsHeaderLeft(box1Left) && !IsHeaderLeft(box2Left))
                    {
                        removedBoxes.Add(box2);
                    }
                    else if (IsHeaderLeft(box2Left) && !IsHeaderLeft(box1Left))
                    {
                        removedBoxes.Add(box1);
                        break;
                    }
                    else
                    {
                        continue;
                    }
                }
            }
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                for (int j = 0; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];
                    if (box2.Equals(box1) || !Utils.isOverlap(box1, box2)) continue;
                    if (Math.Abs(box1.left - box2.left) > 2 || Math.Abs(box1.right - box2.right) > 2)
                    {
                        continue;
                    }
                    // header are closed to each other
                    if (Math.Abs(box1.top - box2.top) <= 1) continue;

                    Boundary box1Up = Utils.UpRow(box1);
                    Boundary box2Up = Utils.UpRow(box2);
                    if (IsHeaderUp(box1Up) && !IsHeaderUp(box2Up))
                    {
                        removedBoxes.Add(box2);
                    }
                    else if (IsHeaderUp(box2Up) && !IsHeaderUp(box1Up))
                    {
                        removedBoxes.Add(box1);
                        break;
                    }
                    else
                    {
                        continue;
                    }


                }
            }

            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void PairAlikeContainsFilter()
        {
            // solve the contradict containing  pairs with same similar left-right boundaries or similar up-down boundaries
            var removedBoxes = new HashSet<Boundary>();
            //vertical
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                for (int j = 0; j < _boxes.Count; j++)
                {
                    // find a box so as to box1 contains box2 and shares left and right header
                    Boundary box2 = _boxes[j];
                    if (!Utils.ContainsBox(box1, box2, 1) || box1.bottom <= box2.bottom) continue;
                    // similar left right border
                    if (Math.Abs(box1.left - box2.left) > 2 || Math.Abs(box1.right - box2.right) > 2
                        || box1.right - box1.left >= 2 * (box2.right - box2.left) || box1.right - box1.left <= 0.5 * (box2.right - box2.left))
                    {
                        continue;
                    }
                    // similar up heaer
                    if (Math.Abs(box1.top - box2.top) > 2)
                    {
                        continue;
                    }
                    int cntInside = 0;

                    // the remaining box that excluded box2 from box1 
                    Boundary remainBox = new Boundary(box2.bottom + 1, box1.bottom, box1.left, box1.right);
                    while (remainBox.top < remainBox.bottom && _sheet.sumContentExist.SubmatrixSum(Utils.UpRow(remainBox)) < 3)
                    {
                        remainBox.top = remainBox.top + 1;
                    }
                    // count the closed related _boxes
                    for (int k = 0; k < _boxes.Count; k++)
                    {
                        Boundary box3 = _boxes[k];
                        if (removedBoxes.Contains(box3)) { continue; }
                        if (Utils.isOverlap(box2, box3) || box1.Equals(box3) || box2.Equals(box3)) { continue; }
                        if (Utils.ContainsBox(box3, remainBox, 2) || (Utils.ContainsBox(remainBox, box3, 2) && IsHeaderUp(Utils.UpRow(remainBox))))
                        {
                            cntInside += 1;
                            break;
                        }

                        if ((Math.Abs(box3.left - box1.left) <= 2 || Math.Abs(box3.right - box1.right) <= 2) && Utils.ContainsBox(box1, box3, 1))
                        {
                            cntInside += 1;
                            break;
                        }
                    }

                    if (cntInside == 0)
                    {
                        Boundary box2Bottom = new Boundary(Math.Max(box2.top, box2.bottom - 12), box2.bottom, box2.left, box2.right);
                        if (!IsHeaderLeft(Utils.LeftCol(remainBox)) && _sheet.ContentExistValueDensity(box2Bottom) > 2 * _sheet.ContentExistValueDensity(remainBox)
                            && (_sheet.ContentExistValueDensity(remainBox) > 2 * 0.25 || _sheet.ContentExistValueDensity(box2Bottom) > 2 * 0.5))
                        {
                            removedBoxes.Add(box1);
                        }
                        else if (box2.bottom - box2.top >= 4 && _sheet.ContentExistValueDensity(remainBox) < 2 * 0.25 && _sheet.ContentExistValueDensity(box2Bottom) > 2 * 0.5)
                        {
                            removedBoxes.Add(box1);
                        }
                        else if (box2.bottom - box2.top >= 4 && _sheet.ContentExistValueDensity(remainBox) < 2 * 0.1 && _sheet.ContentExistValueDensity(box2Bottom) > 2 * 0.35)
                        {
                            removedBoxes.Add(box1);
                        }
                        else if (box1.bottom - box2.bottom <= 4 && box2.bottom - box2.top >= 5 && IsHeaderLeft(Utils.LeftCol(box2)) && !IsHeaderLeft(Utils.LeftCol(box1)))
                        {
                            removedBoxes.Add(box1);
                        }
                        else if (IsHeaderUp(Utils.DownRow(box1)) && _sheet.ContentExistValueDensity(Utils.DownRow(box1, start: 1)) < 0.2 * 2
                            && !IsHeaderUp(Utils.DownRow(box1, start: 3)) && !IsHeaderUp(Utils.DownRow(box1, start: 2)))
                        {
                            removedBoxes.Add(box1);
                        }
                        else if (box1.bottom - box2.bottom <= 4 && box2.bottom - box2.top >= 5 && remainBox.right - remainBox.left >= 4
                            && _sheet.sumContentExist.SubmatrixSum(remainBox) / Utils.Width(remainBox) <= 4
                            && _sheet.ContentExistValueDensity(remainBox) < 2 * 0.45 && _sheet.ContentExistValueDensity(box2Bottom) > 2 * 0.6)
                        {
                            removedBoxes.Add(box1);
                        }
                        else
                        {
                            removedBoxes.Add(box2);
                        }

                    }
                }

            }

            // horizontal
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                for (int j = 0; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];
                    if (!Utils.ContainsBox(box1, box2, 1)) continue;
                    if (Math.Abs(box1.top - box2.top) > 2 || Math.Abs(box1.bottom - box2.bottom) > 2
                        || box1.bottom - box1.top >= 2 * (box2.bottom - box2.top) || box1.bottom - box1.top <= 0.5 * (box2.bottom - box2.top)
                        || Math.Abs(box1.left - box2.left) > 2 || box1.right <= box2.right)
                    {
                        continue;
                    }
                    int cntInside = 0;
                    Boundary remainBox = new Boundary(box1.top, box1.bottom, box2.right + 1, box1.right);
                    while (remainBox.left < remainBox.right && _sheet.sumContentExist.SubmatrixSum(Utils.LeftCol(remainBox)) < 3)
                    {
                        remainBox.left = remainBox.left + 1;
                    }

                    for (int k = 0; k < _boxes.Count; k++)
                    {
                        Boundary box3 = _boxes[k];
                        if (removedBoxes.Contains(box3)) { continue; }
                        if (Utils.isOverlap(box2, box3) || box1.Equals(box3) || box2.Equals(box3)) { continue; }
                        if (Utils.ContainsBox(box3, remainBox, 2) || (Utils.ContainsBox(remainBox, box3, 2) && IsHeaderLeft(Utils.LeftCol(remainBox))))
                        {
                            cntInside = cntInside + 1;
                            break;
                        }
                        if ((Math.Abs(box3.top - box1.top) <= 2 || Math.Abs(box3.bottom - box1.bottom) <= 2) && Utils.ContainsBox(box1, box3, 1))
                        {
                            cntInside = cntInside + 1;
                            break;
                        }
                    }
                    if (cntInside == 0)
                    {

                        Boundary box2Right = new Boundary(box2.top, box2.bottom, Math.Max(box2.left, box2.right - 12), box2.right);
                        if (!IsHeaderUp(Utils.UpRow(remainBox)) && _sheet.ContentExistValueDensity(box2Right) > 2 * _sheet.ContentExistValueDensity(remainBox)
                            && (_sheet.ContentExistValueDensity(box2Right) > 0.5 || _sheet.ContentExistValueDensity(remainBox) > 0.25))
                        {
                            removedBoxes.Add(box1);
                        }
                        else if (box2.right - box2.left >= 4 && _sheet.ContentExistValueDensity(remainBox) < 2 * 0.25 && _sheet.ContentExistValueDensity(box2Right) > 2 * 0.5)
                        {
                            removedBoxes.Add(box1);
                        }
                        else if (box2.right - box2.left >= 4 && _sheet.ContentExistValueDensity(remainBox) < 2 * 0.1 && _sheet.ContentExistValueDensity(box2Right) > 2 * 0.35)
                        {
                            removedBoxes.Add(box1);
                        }
                        else if ((box1.right - box2.right) <= 4 && box2.right - box2.left >= 5 && IsHeaderUp(Utils.UpRow(box2)) && !IsHeaderUp(Utils.UpRow(box1)))
                        {
                            removedBoxes.Add(box1);
                        }
                        else if (IsHeaderLeft(Utils.RightCol(box1)) && _sheet.ContentExistValueDensity(Utils.RightCol(box1, start: 1)) < 0.2 * 2
                            && !IsHeaderLeft(Utils.RightCol(box1, start: 2)) && !IsHeaderLeft(Utils.RightCol(box1, start: 3)))
                        {
                            removedBoxes.Add(box1);
                        }
                        else
                        {
                            removedBoxes.Add(box2);
                        }

                    }
                }
            }
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void PairContainsFilter()
        {
            var removedBoxes = new HashSet<Boundary>();
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                int cntOverlap = 0;
                for (int j = 0; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];
                    if (Utils.isOverlap(box1, box2) && !Utils.ContainsBox(box1, box2) && !Utils.ContainsBox(box2, box1))
                    {
                        cntOverlap = cntOverlap + 1;
                        break;
                    }
                }
                if (cntOverlap != 0) continue;
                //if (removedBoxes.Contains(box1)) continue;
                for (int j = 0; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];
                    //if (removedBoxes.Contains(box2)) continue;
                    if (!Utils.ContainsBox(box1, box2) || box1.Equals(box2))
                    {
                        continue;
                    }
                    int cntInside = 0;

                    for (int k = 0; k < _boxes.Count; k++)
                    {
                        Boundary box3 = _boxes[k];
                        if (removedBoxes.Contains(box3)) continue;
                        if (box1.Equals(box3) || box2.Equals(box3)) continue;
                        if (Utils.ContainsBox(box1, box3))
                        {
                            cntInside++;
                            break;
                        }

                    }
                    if (cntInside == 0)
                    {
                        Boundary box1Up = new Boundary(box1.top, box1.top, box1.left, box1.right);
                        Boundary box2Up = new Boundary(box2.top, box2.top, box2.left, box2.right);
                        Boundary box1Left = new Boundary(box1.top, box1.bottom, box1.left, box1.left);
                        Boundary box2Left = new Boundary(box2.top, box2.bottom, box2.left, box2.left);

                        if (IsHeaderLeft(box1Left) && !IsHeaderLeft(box2Left))
                        {
                            removedBoxes.Add(box2);
                        }
                        if (IsHeaderUp(box1Up) && !IsHeaderUp(box2Up))
                        {
                            removedBoxes.Add(box2);
                        }

                        List<Boundary> remainBoxs = new List<Boundary>();
                        remainBoxs.Add(new Boundary(box2.bottom + 2, box1.bottom, box1.left, box1.right));
                        remainBoxs.Add(new Boundary(box1.top, box1.bottom, box2.right + 2, box1.right));
                        remainBoxs.Add(new Boundary(box1.top, box1.bottom, box1.left, box2.left - 2));
                        remainBoxs.Add(new Boundary(box1.top, box2.top - 2, box1.left, box1.right));

                        double maxRemainValueRate = 0;
                        foreach (var remainBox in remainBoxs)
                        {
                            if (remainBox.bottom < remainBox.top || remainBox.right < remainBox.left) continue;
                            if (Utils.AreaSize(remainBox) != 0) maxRemainValueRate = Math.Max(maxRemainValueRate, _sheet.ContentExistValueDensity(remainBox));
                        }
                        if (maxRemainValueRate < 2 * 0.3 && _sheet.ContentExistValueDensity(box2) > 2 * 0.7)
                        {
                            removedBoxes.Add(box1);
                        }
                        else if (maxRemainValueRate < 2 * 0.2 && _sheet.ContentExistValueDensity(box2) > 2 * 0.5)
                        {
                            removedBoxes.Add(box1);
                        }
                        else if (maxRemainValueRate < 2 * 0.4 && _sheet.ContentExistValueDensity(box2) > 2 * 0.9)
                        {
                            removedBoxes.Add(box1);
                        }
                        else
                        {
                            removedBoxes.Add(box2);
                        }

                    }
                }
            }
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void CombineContainsHeaderFilter()
        {
            var removedBoxes = new HashSet<Boundary>();

            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                int cntInside = 0;
                int cntNoneHead = 0;
                //if (removedBoxes.Contains(box1)) continue;
                List<Boundary> insideBoxes = new List<Boundary>();
                for (int j = 0; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];
                    //if (removedBoxes.Contains(box2)) continue;
                    if (box2.Equals(box1) || !Utils.isOverlap(box1, box2) || Utils.ContainsBox(box2, box1, 2))//if (box2.Equals(box1) || !Utils.isContainsBox(box1, box2))
                    {
                        continue;
                    }


                    Boundary box2Left = new Boundary(box2.top, box2.bottom, box2.left, box2.left);
                    Boundary box2Up = new Boundary(box2.top, box2.top, box2.left, box2.right);
                    if (!(Math.Abs(box1.top - box2.top) > 2 || Math.Abs(box1.bottom - box2.bottom) > 2) && !IsHeaderLeft(box2Left))
                    {
                        cntNoneHead++;
                        //break;
                    }
                    else if (!(Math.Abs(box1.left - box2.left) > 2 || Math.Abs(box1.right - box2.right) > 2) && !IsHeaderUp(box2Up))
                    {
                        cntNoneHead++;
                        //break;
                    }
                    else if ((Math.Abs(box1.left - box2.left) > 2 || Math.Abs(box1.right - box2.right) > 2) && (Math.Abs(box1.top - box2.top) > 2 || Math.Abs(box1.bottom - box2.bottom) > 2) && (!IsHeaderUp(box2Up) || !IsHeaderLeft(box2Left)))
                    {
                        cntNoneHead++;
                        //break;
                    }
                    else
                    {
                        cntInside++;
                        insideBoxes.Add(box2);
                    }

                }
                if (cntInside >= 2 && Utils.IsFillBox(box1, insideBoxes))
                {
                    removedBoxes.Add(box1);

                }

            }

            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void CombineContainsFillAreaFilterSoft()
        {
            var removedBoxes = new HashSet<Boundary>();

            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                int cntInside = 0;
                //if (removedBoxes.Contains(box1)) continue;
                List<Boundary> insideBoxes = new List<Boundary>();
                for (int j = 0; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];
                    if (box2.Equals(box1)) continue;
                    //if (removedBoxes.Contains(box2)) continue;
                    if (box2.Equals(box1) || Utils.ContainsBox(box2, box1, 2) || !Utils.isOverlap(box1, box2))//if (box2.Equals(box1) || !Utils.isContainsBox(box1, box2))
                    {
                        continue;
                    }
                    cntInside++;
                    insideBoxes.Add(box2);

                }
                double areaSizeCombine = 0;
                int valueSumInside = 0;

                foreach (var inBox in insideBoxes)
                {
                    Boundary inBoxOverlapRegion = Utils.OverlapBox(inBox, box1);
                    areaSizeCombine = areaSizeCombine + Utils.AreaSize(inBoxOverlapRegion);
                    valueSumInside += _sheet.sumContentExist.SubmatrixSum(inBoxOverlapRegion);
                }
                if (cntInside > 0 && areaSizeCombine < 0.4 * Utils.AreaSize(box1))
                {
                    if (valueSumInside < 0.7 * _sheet.sumContentExist.SubmatrixSum(box1))
                    {
                        foreach (var inBox in insideBoxes)
                        {
                            if (Utils.ContainsBox(box1, inBox, 2))
                            {
                                removedBoxes.Add(inBox);
                            }
                        }
                    }
                    if (valueSumInside > 0.85 * _sheet.sumContentExist.SubmatrixSum(box1))
                    {
                        removedBoxes.Add(box1);
                    }
                }

            }

            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void CombineContainsFillLineFilterSoft()
        {
            var removedBoxes = new HashSet<Boundary>();

            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                //if (removedBoxes.Contains(box1)) continue;
                List<Boundary> insideBoxes = new List<Boundary>();
                List<int> rows = new List<int>();
                List<int> cols = new List<int>();
                List<Boundary> rowBoxes = new List<Boundary>();
                List<Boundary> colBoxes = new List<Boundary>();
                for (int j = 0; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];
                    //if (removedBoxes.Contains(box2)) continue;
                    if (!box2.Equals(box1) && Utils.isOverlap(box1, box2))//if (box2.Equals(box1) || !Utils.isContainsBox(box1, box2))
                    {
                        Boundary box2Left = new Boundary(Math.Max(box2.top, box1.top), Math.Min(box2.bottom, box1.bottom), box2.left, box2.left);
                        Boundary box2Up = new Boundary(box2.top, box2.top, Math.Max(box1.left, box2.left), Math.Min(box2.right, box1.right));
                        if (box1.right >= box1.left + 2 && !(box2.right > box1.right + 1 && box2.left < box1.left - 1) && box2.top >= box1.top + 3 && box2.bottom >= box1.bottom - 2 && box2.bottom <= box1.bottom + 2 && IsHeaderUp(box2Up))
                        {
                            colBoxes.Add(box2);
                            for (int k = box2.left - 2; k <= box2.right + 2; k++)
                            {
                                cols.Add(k);
                            }
                        }
                        if (box1.bottom >= box1.top + 2 && !(box2.bottom > box1.bottom + 1 && box2.top < box1.top - 1) && box2.left >= box1.left + 3 && box2.right >= box1.right - 2 && box2.right <= box1.right + 2 && IsHeaderLeft(box2Left))
                        {
                            rowBoxes.Add(box2);
                            for (int k = box2.top - 2; k <= box2.bottom + 2; k++)
                            {
                                rows.Add(k);
                            }
                        }
                    }

                }
                if (Utils.IsFillBoxRowColLines(box1, rows, cols))
                {
                    removedBoxes.Add(box1);
                }
                foreach (var rowBoxin1 in rowBoxes)
                {
                    Boundary rowBox1 = new Boundary(box1.top, box1.bottom, rowBoxin1.left, rowBoxin1.right);
                    List<Boundary> OverlapBoxes = new List<Boundary>();
                    foreach (var rowBoxin2 in rowBoxes)
                    {
                        if (Utils.isOverlap(rowBoxin2, rowBox1))
                        {
                            OverlapBoxes.Add(Utils.OverlapBox(rowBoxin2, rowBox1));
                        }
                    }
                    if (_sheet.sumContentExist.SubmatrixSum(rowBox1) - SheetMap.ValueSumRange(OverlapBoxes, _sheet.sumContentExist) < 10)
                    {
                        removedBoxes.Add(box1);
                    }
                }
                foreach (var colBoxin1 in colBoxes)
                {
                    Boundary colBox1 = new Boundary(colBoxin1.top, colBoxin1.bottom, box1.left, box1.right);
                    List<Boundary> OverlapBoxes = new List<Boundary>();
                    foreach (var colBoxin2 in colBoxes)
                    {
                        if (Utils.isOverlap(colBoxin2, colBox1))
                        {
                            OverlapBoxes.Add(Utils.OverlapBox(colBoxin2, colBox1));
                        }
                    }
                    if (_sheet.sumContentExist.SubmatrixSum(colBox1) - SheetMap.ValueSumRange(OverlapBoxes, _sheet.sumContentExist) < 10)
                    {
                        removedBoxes.Add(box1);
                    }
                }
            }

            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void CombineContainsFilterHard()
        {
            var removedBoxes = new HashSet<Boundary>();

            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                //if (removedBoxes.Contains(box1)) continue;
                List<int> rows = new List<int>();
                List<int> cols = new List<int>();
                List<Boundary> boxesIn = new List<Boundary>();
                bool containsHeader = false;
                int rowsCount = 0;
                for (int j = 0; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];

                    //if (removedBoxes.Contains(box2)) continue;
                    if (!Utils.isOverlap(box1, box2) || Utils.ContainsBox(box2, box1, 2)) continue;
                    Boundary overlapRegion = Utils.OverlapBox(box1, box2);
                    if (box2.Equals(overlapRegion)) overlapRegion = box2;

                    if (IsHeaderUp(Utils.UpRow(overlapRegion)) && !IsHeaderUp(Utils.UpRow(box1)) &&
                        Math.Abs(Utils.UpRow(box1).top - Utils.UpRow(overlapRegion).top) > 3 && _sheet.sumContentExist.SubmatrixSum(Utils.UpRow(overlapRegion)) > 0.8 * _sheet.sumContentExist.SubmatrixSum(new Boundary(overlapRegion.top, overlapRegion.top, box1.left, box1.right)))
                    {
                        containsHeader = true;
                    }

                    if (!overlapRegion.Equals(box1) && Utils.ContainsBox(box1, overlapRegion, 2) && !Utils.isSuppressionBox(box1, overlapRegion))//if (box2.Equals(box1) || !Utils.isContainsBox(box1, box2))
                    {
                        boxesIn.Add(overlapRegion);
                        for (int k = overlapRegion.left - 2; k <= overlapRegion.right + 2; k++)
                        {
                            cols.Add(k);
                        }
                        for (int k = overlapRegion.top - 2; k <= overlapRegion.bottom + 2; k++)
                        {
                            rows.Add(k);
                        }
                        rowsCount += overlapRegion.bottom - overlapRegion.top + 1;
                    }
                }

                double sizeSum = Utils.AreaSize(boxesIn);

                int valueSum = SheetMap.ValueSumRange(boxesIn, _sheet.sumContentExist);

                bool columnOverlap = false;
                for (int k = 0; k < boxesIn.Count; k++)
                {
                    for (int t = k + 1; t < boxesIn.Count; t++)
                    {
                        if (!(boxesIn[k].left > boxesIn[t].right || boxesIn[k].right < boxesIn[t].left))
                        {
                            columnOverlap = true;
                        }
                    }
                }

                // sheet.valueSumRange(box1) - valueSum < 2 *Math.Max(box1.down - box1.up + 1, box1.right - box1.left + 1)
                if ((containsHeader || (float)(box1.bottom - box1.top + 1 - rowsCount) / (float)(boxesIn.Count - 1) > 1.5) && Utils.IsFillLines(box1.top, box1.bottom, rows) && Utils.IsFillLines(box1.left, box1.right, cols) && sizeSum > 0.6 * Utils.AreaSize(box1) && valueSum > 0.8 * _sheet.sumContentExist.SubmatrixSum(box1))
                {
                    removedBoxes.Add(box1);
                }
                // filter big box when sub-boxes has no overlaped columns
                else if (!columnOverlap && boxesIn.Count >= 2)
                {
                    removedBoxes.Add(box1);
                }
                else
                {
                    foreach (var box2 in boxesIn)
                    {
                        removedBoxes.Add(box2);
                    }
                }
            }
            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void ContainsLittleFilter()
        {
            // filter small  _boxes with row-col direction  sub _boxes containing header
            var removedBoxes = new HashSet<Boundary>();

            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                if (removedBoxes.Contains(box1)) continue;
                List<Boundary> insideBoxes = new List<Boundary>();
                for (int j = 0; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];
                    if (removedBoxes.Contains(box2)) continue;
                    if (!Utils.ContainsBox(box1, box2) || box2.Equals(box1))
                    {
                        continue;
                    }
                    insideBoxes.Add(box2);
                    // TODO: similar with surround
                    if (box2.bottom - box2.top < 6 && box2.right - box2.left < 6 && (box2.bottom - box2.top < 4 || box2.right - box2.left < 4))
                    {
                        var box2OutUp = new Boundary(box2.top - 1, box2.top - 1, box2.left, box2.right);
                        var box2OutDown = new Boundary(box2.bottom + 1, box2.bottom + 1, box2.left, box2.right);
                        var box2OutLeft = new Boundary(box2.top, box2.bottom, box2.left - 1, box2.left - 1);
                        var box2OutRight = new Boundary(box2.top, box2.bottom, box2.right + 1, box2.right + 1);
                        int cntNotNone = 0;
                        int cntNotSparse = 0;
                        if (_sheet.sumContentExist.SubmatrixSum(box2OutUp) > 0) cntNotNone++;
                        if (_sheet.sumContentExist.SubmatrixSum(box2OutDown) > 0) cntNotNone++;
                        if (_sheet.sumContentExist.SubmatrixSum(box2OutLeft) > 0) cntNotNone++;
                        if (_sheet.sumContentExist.SubmatrixSum(box2OutRight) > 0) cntNotNone++;

                        if (_sheet.sumContentExist.SubmatrixSum(box2OutDown) > 2) cntNotSparse++;
                        if (_sheet.sumContentExist.SubmatrixSum(box2OutRight) > 2) cntNotSparse++;

                        if ((cntNotNone >= 3 && cntNotSparse >= 1) || cntNotNone == 4)
                        {
                            removedBoxes.Add(box2);
                        }
                    }
                }
            }

            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }

        private void PairContainsFilterHard()
        {
            var removedBoxes = new HashSet<Boundary>();

            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box1 = _boxes[i];
                if (removedBoxes.Contains(box1)) continue;
                List<Boundary> insideBoxes = new List<Boundary>();
                List<Boundary> overlapBoxes = new List<Boundary>();
                for (int j = 0; j < _boxes.Count; j++)
                {
                    Boundary box2 = _boxes[j];
                    if (removedBoxes.Contains(box2)) continue;
                    if (!Utils.ContainsBox(box1, box2, 1) && !Utils.ContainsBox(box2, box1, 1) && !box2.Equals(box1) && Utils.isOverlap(box2, box1))
                    {
                        overlapBoxes.Add(box2);
                    }
                    if (!Utils.ContainsBox(box1, box2, 1) || box2.Equals(box1))
                    {
                        continue;
                    }

                    // TODO: similar with surround
                    Boundary upHeader1 = new Boundary(box1.top, box1.top, box1.left, box1.right);
                    Boundary upHeader2 = new Boundary(box2.top, box2.top, box2.left, box2.right);
                    Boundary leftHeader1 = new Boundary(box1.top, box1.bottom, box1.left, box1.left);
                    Boundary leftHeader2 = new Boundary(box2.top, box2.bottom, box2.left, box2.left);
                    if ((IsHeaderUp(upHeader1) && !IsHeaderUp(upHeader2) && Math.Abs(upHeader1.top - upHeader2.top) >= 2) || (IsHeaderLeft(leftHeader1) && !IsHeaderLeft(leftHeader2) && Math.Abs(upHeader1.left - upHeader2.left) >= 2))
                    {
                        removedBoxes.Add(box2);
                    }
                    else { insideBoxes.Add(box2); }

                }
                if (insideBoxes.Count == 1 && overlapBoxes.Count == 0)
                {
                    removedBoxes.Add(insideBoxes[0]);
                }

            }

            Utils.RemoveTheseCandidates(removedBoxes, _boxes);
        }
        #endregion

        #region formula related
        private void FormulaCorrelationFilter()
        {
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary boxFrom = _boxes[i];
                for (int j = 0; j < _boxes.Count; j++)
                {
                    Boundary boxTo = _boxes[j];
                    if (boxFrom.Equals(boxTo))
                    {
                        continue;
                    }
                    // two _boxes are far away for  vertical or horizontal directitons
                    if (boxTo.bottom < boxFrom.top - 10 || boxTo.right < boxFrom.left - 10 || boxFrom.bottom < boxTo.top - 10 || boxFrom.right < boxTo.left - 10)
                    {
                        continue;
                    }
                    // two _boxes are crossing each other both in vertical or horizontal directitons
                    // +2 is for skipping the header region
                    if ((boxFrom.left <= boxTo.left - 2 || boxFrom.right >= boxTo.right + 2)
                          && (boxFrom.top <= boxTo.top - 2 || boxFrom.bottom >= boxTo.bottom + 2))
                    {
                        continue;
                    }
                    //// two _boxes are not overlaped both in vertical or horizontal directitons
                    //if ((boxTo.bottom < boxFrom.top && boxTo.right < boxFrom.left) || (boxFrom.bottom < boxTo.top && boxFrom.right < boxTo.left))
                    //{
                    //    continue;
                    //}

                    // vertical relation cases
                    // this condition refer to that the boxFrom should "thiner" than boxTo, this regulation can avoid some noises in combination tables
                    if (boxFrom.left > boxTo.left - 2 && boxFrom.right < boxTo.right + 2)
                    {
                        int verticalCorrelation = IsFormulaCorrelation(boxFrom, boxTo, "vertical");
                        if (verticalCorrelation == 2 || verticalCorrelation == 1)
                        {
                            removedBoxes.Add(boxFrom);
                        }
                        Boundary boxLarge = Utils.UnifyBox(boxFrom, boxTo);
                        if (verticalCorrelation == 2
                            && !(boxFrom.top >= boxTo.top && boxFrom.bottom <= boxTo.bottom)
                            && _sheet.sumContentExist.SubmatrixSum(new Boundary(boxTo.bottom + 3, boxFrom.top - 3, boxTo.left, boxTo.right)) <= 10)
                        {
                            // because boxFrom maybe a combined box with left border inside
                            removedBoxes.Add(boxTo);
                            appendBoxes.Add(boxLarge);
                        }
                        if (verticalCorrelation == 2
                            && !(!(boxFrom.top >= boxTo.top && boxFrom.bottom <= boxTo.bottom)
                            && _sheet.sumContentExist.SubmatrixSum(new Boundary(boxTo.bottom + 3, boxFrom.top - 3, boxTo.left, boxTo.right)) <= 10))
                        {
                            appendBoxes.Add(boxLarge);
                        }
                    }

                    // horizontal relation cases
                    int horizontalCorrelation = IsFormulaCorrelation(boxFrom, boxTo, "horizontal");
                    if (boxFrom.top > boxTo.top - 2 && boxFrom.bottom < boxTo.bottom + 2 && horizontalCorrelation != 0)
                    {
                        // because reference ranges overlap boxFrom, so remove boxFrom directly
                        removedBoxes.Add(boxFrom);
                        Boundary boxLarge = Utils.UnifyBox(boxFrom, boxTo);
                        if (horizontalCorrelation == 2
                            && !(boxFrom.left >= boxTo.left && boxFrom.right <= boxTo.right)
                            && _sheet.sumContentExist.SubmatrixSum(new Boundary(boxTo.top, boxTo.bottom, boxTo.right + 3, boxFrom.left - 3)) <= 10)
                        {
                            removedBoxes.Add(boxTo);
                            appendBoxes.Add(boxLarge);
                        }
                        if (horizontalCorrelation == 2
                            && !((boxFrom.left < boxTo.left || boxFrom.right > boxTo.right)
                            && _sheet.sumContentExist.SubmatrixSum(new Boundary(boxTo.top, boxTo.bottom, boxTo.right + 3, boxFrom.left - 3)) <= 10))
                        {
                            appendBoxes.Add(boxLarge);
                        }
                    }
                }
            }

            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private int IsFormulaCorrelation(Boundary boxFrom, Boundary boxTo, string direction)
        {
            // go over all the cells in boxFrom, to  find our all the formulas
            for (int row = boxFrom.top; row <= boxFrom.bottom; row++)
            {
                for (int col = boxFrom.left; col <= boxFrom.right; col++)
                {
                    Boundary curCell = new Boundary(row, row, col, col);
                    // when verify vertical reference,make sure the current cell is is downside or upside the boxFrom
                    if (direction == "vertical" && boxTo.top <= curCell.top && curCell.top <= boxTo.bottom)
                    {
                        continue;
                    }
                    // when verify horizontal reference, make sure the current cell is leftside or rightside the boxFrom
                    if (direction == "horizontal" && boxTo.left <= curCell.left && curCell.left <= boxTo.right)
                    {
                        continue;
                    }

                    foreach (var referRange in _sheet.formulaRanges[row, col])
                    {
                        if (!Utils.isOverlap(referRange, boxTo))
                        {
                            continue;
                        }

                        // to judge if two _boxes should be merged to one due to formulas
                        int formulaRelation = direction == "vertical"
                            ? formulaRelation = IsFormulaRelatedUpDown(boxFrom, boxTo, curCell, referRange)
                            : IsFormulaRelatedLeftRight(boxFrom, boxTo, curCell, referRange);
                        if (formulaRelation != 0)
                            return formulaRelation;
                    }
                }
            }
            return 0;
        }

        private int IsFormulaRelatedUpDown(Boundary boxFrom, Boundary boxTo, Boundary cell, Boundary referRange)
        {
            Boundary overlapRange = Utils.OverlapBox(referRange, boxTo);
            // the reference range should not be contained by the source box
            if (Utils.ContainsBox(boxFrom, overlapRange, 1)) { return 0; }
            if (Utils.isOverlap(boxFrom, new Boundary(1, _sheet.Height, overlapRange.left, overlapRange.right)))
            {
                Boundary box1Up1 = Utils.UpRow(boxFrom);
                Boundary box1Up2 = Utils.UpRow(boxFrom, start: 1);
                if ((boxFrom.bottom > boxTo.bottom || boxFrom.top > boxTo.top) && overlapRange.top < boxFrom.top
                    && boxFrom.right > boxTo.left && boxTo.right > boxFrom.left
                    && !IsHeaderUp(box1Up1) && !IsHeaderUp(box1Up2))
                {
                    if (Utils.ContainsBox(boxTo, cell))
                        return 1;
                    else
                        return 2;
                }
            }
            return 0;
        }

        private int IsFormulaRelatedLeftRight(Boundary boxFrom, Boundary boxTo, Boundary cell, Boundary referRange)
        {
            Boundary overlapRange = Utils.OverlapBox(referRange, boxTo);
            if (Utils.ContainsBox(boxFrom, overlapRange, 1)) { return 0; }
            if (Utils.isOverlap(boxFrom, new Boundary(overlapRange.top, overlapRange.bottom, 1, _sheet.Width)))
            {
                Boundary box1Left1 = Utils.LeftCol(boxFrom);
                Boundary box1Left2 = Utils.LeftCol(boxFrom, start: 1);

                if ((boxFrom.right > boxTo.right || boxFrom.left > boxTo.left) && referRange.left < boxFrom.left
                    && boxFrom.bottom > boxTo.top && boxTo.bottom > boxFrom.top
                    && !IsHeaderLeft(box1Left1) && !IsHeaderLeft(box1Left2))
                {
                    if (Utils.ContainsBox(boxTo, cell))
                    {
                        return 1;
                    }
                    else
                    {
                        return 2;
                    }
                }

            }
            return 0;
        }
        #endregion
    }
}
```

## File: code/DetectorTrimmers.cs
```csharp
using System;
using System.Linq;
using System.Collections.Generic;

namespace SpreadsheetLLM.Heuristic
{
    internal partial class TableDetectionHybrid
    {
        #region retrive header
        private void RetrieveDistantUpHeader()
        {
            //found out the up header apart from the data region
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();

            foreach (var box1 in _boxes)
            {
                bool markContainsHeaderAlready = IsHeaderUpWithDataArea(Utils.UpRow(box1), box1);
                if (markContainsHeaderAlready) { continue; }

                // find if exist header out the box in the upside
                bool markContainsHeaderUp = false;

                // find first compact row upside
                Boundary compactBoxUpRow = Utils.UpRow(box1, start: -1);
                for (int headerStartPoint = 1; headerStartPoint < 5; headerStartPoint++)
                {
                    compactBoxUpRow = Utils.UpRow(box1, start: -headerStartPoint);
                    if (_sheet.sumContentExist.SubmatrixSum(compactBoxUpRow) >= 6
                        && _sheet.TextDistinctCount(compactBoxUpRow) > 1
                        && _sheet.ContentExistValueDensity(compactBoxUpRow) >= 2 * 0.5)
                    {
                        break;
                    }
                }
                // the first compact row should be  new header
                if (IsHeaderUp(compactBoxUpRow) && HeaderRate(compactBoxUpRow) > 0.8) { markContainsHeaderUp = true; }

                int cntHeaderHeight = 0;
                // skip the new header region
                while (cntHeaderHeight < 3 && markContainsHeaderUp && IsHeaderUpSimple(Utils.UpRow(compactBoxUpRow, start: -1)))
                {
                    cntHeaderHeight++;
                    compactBoxUpRow = Utils.UpRow(compactBoxUpRow, start: -1);
                }

                // verify there is empty rows upside this new header
                if (markContainsHeaderUp
                    && (_sheet.sumContentExist.SubmatrixSum(Utils.UpRow(compactBoxUpRow, start: -1)) < 3
                    || _sheet.sumContentExist.SubmatrixSum(Utils.UpRow(compactBoxUpRow, start: -2)) < 3
                    || _sheet.sumContentExist.SubmatrixSum(Utils.UpRow(compactBoxUpRow, start: -3)) < 3))
                {
                    removedBoxes.Add(box1);
                    Boundary newBox = new Boundary(compactBoxUpRow.top, box1.bottom, box1.left, box1.right);
                    appendBoxes.Add(newBox);
                }
            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
        }

        private void RetrieveUpHeader(int step)
        {
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();
            foreach (var box in _boxes)
            {
                bool markHeader = false;
                if (IsHeaderUp(new Boundary(box.top, box.top, box.left, box.right)))
                {
                    markHeader = true;
                }
                int up;
                for (up = box.top - step; up > 0; up--)
                {
                    Boundary boxUp = new Boundary(up, up, box.left, box.right);
                    if (markHeader && !IsHeaderUp(boxUp) && !_sheet.ExistsMerged(boxUp))
                    {
                        break;
                    }
                    if (Utils.DistinctStrs(_sheet.contentStrs, up, up, box.left, box.right) >= 2)
                    {
                        continue;
                    }
                    if (_sheet.ExistsMerged(boxUp) && Utils.DistinctStrs(_sheet.contentStrs, up, up, box.left, box.right) >= 2)
                    {
                        continue;
                    }
                    else if (_sheet.ContentExistValueDensity(boxUp) >= 2 * 0.4 && _sheet.sumContentExist.SubmatrixSum(boxUp) >= 4 && Utils.DistinctStrs(_sheet.contentStrs, up, up, box.left, box.right) >= 2)
                    {
                        continue;
                    }
                    else if (box.right - box.left >= 8 && (_sheet.RowContentExistValueDensitySplit(boxUp, 4) >= 0.7 || _sheet.RowContentExistValueDensitySplit(boxUp, 8) >= 0.7)
                        && !(_sheet.ContentExistValueDensity(boxUp) >= 2 * 0.4 && !IsHeaderUp(boxUp)))
                    {
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }
                if (up < box.top - step && up >= box.top - 6)
                {
                    removedBoxes.Add(box);
                    Boundary newBox = new Boundary(up + 1, box.bottom, box.left, box.right);
                    appendBoxes.Add(newBox);
                }

            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void RetrieveLeft(int step)
        {
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();
            foreach (var box in _boxes)
            {
                int left;
                for (left = box.left - step; left > 0; left--)
                {
                    Boundary boxLeft = new Boundary(box.top, box.bottom, left, left);
                    if (_sheet.ExistsMerged(boxLeft))
                    {
                        continue;
                    }
                    else if (_sheet.ContentExistValueDensity(boxLeft) >= 2 * 0.4 && _sheet.sumContentExist.SubmatrixSum(boxLeft) >= 4)
                    {
                        continue;
                    }
                    else if (box.bottom - box.top >= 8 && (_sheet.ColContentExistValueDensitySplit(boxLeft, 4) == 1 || _sheet.ColContentExistValueDensitySplit(boxLeft, 8) >= 0.8)
                        && !(_sheet.ContentExistValueDensity(boxLeft) >= 2 * 0.4 && !IsHeaderLeft(boxLeft)))
                    {
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }
                if (left < box.left - step && left >= box.left - 5 && !Utils.isOverlap(new Boundary(box.top, box.bottom, left + 1, box.left - 1), _boxes))
                {
                    removedBoxes.Add(box);
                    Boundary newBox = new Boundary(box.top, box.bottom, left + 1, box.right);
                    appendBoxes.Add(newBox);
                }

            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void RetrieveLeftHeader()
        {
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();
            foreach (var box in _boxes)
            {
                Boundary newBox = box;

                // Iteratively expand left border
                while (newBox.left > 1)
                {
                    Boundary boxLeft = Utils.LeftCol(newBox);
                    Boundary boxLeft1 = Utils.LeftCol(newBox, start: -1);
                    Boundary boxLeft2 = Utils.LeftCol(newBox, start: -2);
                    Boundary boxLeft3 = Utils.LeftCol(newBox, start: -3);
                    if (Utils.isOverlap(boxLeft2, _boxes)) break;

                    Boundary tmpBox = newBox;
                    if (!IsHeaderLeft(boxLeft)
                        && _sheet.sumContent.SubmatrixSum(boxLeft1) > 3
                        && _sheet.sumContent.SubmatrixSum(boxLeft2) == 0)
                    {
                        tmpBox = new Boundary(newBox.top, newBox.bottom, newBox.left - 1, newBox.right);
                    }
                    else if (!IsHeaderLeft(boxLeft)
                        && _sheet.sumContent.SubmatrixSum(boxLeft1) + _sheet.sumContent.SubmatrixSum(boxLeft2) > 3
                        && _sheet.sumContent.SubmatrixSum(boxLeft3) == 0)
                    {
                        tmpBox = new Boundary(newBox.top, newBox.bottom, newBox.left - 2, newBox.right);
                    }
                    else if (_sheet.sumContent.SubmatrixSum(boxLeft1) > 5 &&
                        _sheet.ColContentExistValueDensitySplit(boxLeft1, 5) > 0.2 &&
                        _sheet.sumBorderRow.SubmatrixSum(boxLeft2) == 0)
                    {
                        tmpBox = new Boundary(newBox.top, newBox.bottom, newBox.left - 1, newBox.right);
                    }
                    else if (_sheet.sumBorderRow.SubmatrixSum(boxLeft1) > 0)
                    {
                        tmpBox = new Boundary(newBox.top, newBox.bottom, newBox.left - 1, newBox.right);
                    }

                    if (tmpBox.Equals(newBox)) break;
                    newBox = tmpBox;
                }
                if (!newBox.Equals(box))
                {
                    removedBoxes.Add(box);
                    appendBoxes.Add(newBox);
                }
            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }
        #endregion

        private List<Boundary> ProProcessReduceToCompact(List<Boundary> ranges)
        {
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();
            foreach (var box in ranges)
            {
                var newBox = box;
                bool markChanged = true;
                while (markChanged && newBox.left <= newBox.right && newBox.top <= newBox.bottom)
                {
                    #region remove the cols and rows that are not compact
                    markChanged = false;
                    int startRowUp = newBox.top;
                    while (startRowUp < newBox.bottom && _sheet.ContentExistValueDensity(Utils.UpRow(newBox, start: startRowUp - newBox.top)) < 0.8)
                    {
                        startRowUp++;
                        markChanged = true;
                        newBox.top = startRowUp;
                    }

                    int startRowDown = newBox.bottom;
                    while (startRowDown > newBox.top && _sheet.ContentExistValueDensity(Utils.DownRow(newBox, start: newBox.bottom - startRowDown)) < 0.8)
                    {
                        startRowDown--;
                        markChanged = true;
                        newBox.bottom = startRowDown;
                    }

                    int startColLeft = newBox.left;
                    while (startColLeft < newBox.right && _sheet.ContentExistValueDensity(Utils.LeftCol(newBox, start: startColLeft - newBox.left)) < 0.8)
                    {
                        startColLeft++;
                        markChanged = true;
                        newBox.left = startColLeft;
                    }

                    int startColRight = newBox.right;
                    while (startColRight > newBox.left && _sheet.ContentExistValueDensity(Utils.RightCol(newBox, start: newBox.right - startColRight)) < 0.8)
                    {
                        startColRight--;
                        markChanged = true;
                        newBox.right = startColRight;
                    }
                    #endregion
                }

                if (!box.Equals(newBox))
                {
                    removedBoxes.Add(box);
                    if (newBox.left < newBox.right && newBox.top < newBox.bottom)
                    {
                        appendBoxes.Add(newBox);
                    }
                }
            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, ranges);
            return ranges;
        }

        private void SparseBoundariesTrim()
        {
            // for four directions, skip the sparse edges
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();

            foreach (var box in _boxes)
            {
                Boundary newBox = new Boundary(box.top, box.bottom, box.left, box.right);
                bool markChange = true;
                while (markChange && newBox.left < newBox.right && newBox.top < newBox.bottom)
                {
                    markChange = false;
                    #region four directions
                    // left
                    if (newBox.left > 0)
                    {
                        Boundary lineBox = new Boundary(newBox.top, newBox.bottom, newBox.left, newBox.left);
                        // find the sparse row until empty 
                        while (_sheet.sumContentExist.SubmatrixSum(lineBox) < 3)
                        {
                            lineBox = new Boundary(lineBox.top, lineBox.bottom, lineBox.left + 1, lineBox.left + 1);
                            if (_sheet.sumContentExist.SubmatrixSum(lineBox) == 0) { break; }
                        }
                        // change newBox when empty row found
                        while (lineBox.left < newBox.right && _sheet.sumContentExist.SubmatrixSum(lineBox) == 0)
                        {
                            lineBox = new Boundary(lineBox.top, lineBox.bottom, lineBox.left + 1, lineBox.left + 1);
                            markChange = true;
                            newBox.left = lineBox.left;
                        }
                    }
                    // right
                    if (newBox.right <= _sheet.Width)
                    {
                        Boundary lineBox = new Boundary(newBox.top, newBox.bottom, newBox.right, newBox.right);
                        while (_sheet.sumContentExist.SubmatrixSum(lineBox) < 3)
                        {
                            lineBox = new Boundary(lineBox.top, lineBox.bottom, lineBox.left - 1, lineBox.right - 1);
                            if (_sheet.sumContentExist.SubmatrixSum(lineBox) == 0) { break; }
                        }
                        while (lineBox.right > newBox.left && _sheet.sumContentExist.SubmatrixSum(lineBox) == 0)
                        {
                            lineBox = new Boundary(lineBox.top, lineBox.bottom, lineBox.left - 1, lineBox.right - 1);
                            markChange = true;
                            newBox.right = lineBox.right;
                        }
                    }
                    // up
                    if (newBox.top > 0)
                    {
                        Boundary lineBox = new Boundary(newBox.top, newBox.top, newBox.left, newBox.right);
                        while (!IsHeaderUp(lineBox) && (_sheet.sumContentExist.SubmatrixSum(lineBox) < 3 || _sheet.ContentExistValueDensity(lineBox) < 2 * 0.1
                            || (_sheet.sumContentExist.SubmatrixSum(lineBox) < 5 && newBox.right - newBox.left + 1 > 7)))
                        {
                            lineBox = new Boundary(lineBox.top + 1, lineBox.bottom + 1, lineBox.left, lineBox.right);
                            if (_sheet.sumContentExist.SubmatrixSum(lineBox) == 0) { break; }
                        }
                        while (lineBox.top < newBox.bottom && _sheet.sumContentExist.SubmatrixSum(lineBox) == 0)
                        {
                            lineBox = new Boundary(lineBox.top + 1, lineBox.bottom + 1, lineBox.left, lineBox.right);
                            markChange = true;
                            newBox.top = lineBox.top;
                        }
                    }

                    // down
                    if (newBox.bottom <= _sheet.Height)
                    {
                        Boundary lineBox = new Boundary(newBox.bottom, newBox.bottom, newBox.left, newBox.right);
                        while (_sheet.sumContentExist.SubmatrixSum(lineBox) < 3)
                        {
                            lineBox = new Boundary(lineBox.top - 1, lineBox.top - 1, newBox.left, newBox.right);
                            if (_sheet.sumContentExist.SubmatrixSum(lineBox) == 0) break;
                        }
                        while (lineBox.bottom > newBox.top && _sheet.sumContentExist.SubmatrixSum(lineBox) == 0)
                        {
                            lineBox = new Boundary(lineBox.top - 1, lineBox.top - 1, newBox.left, newBox.right);
                            markChange = true;
                            newBox.bottom = lineBox.bottom;
                        }
                    }
                    #endregion
                }

                if (!box.Equals(newBox))
                {
                    removedBoxes.Add(box);
                    if (newBox.left < newBox.right && newBox.top < newBox.bottom)
                    {
                        appendBoxes.Add(newBox);
                    }
                }
            }

            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void SurroundingBoudariesTrim()
        {
            int cntBoxes = -1;
            while (_boxes.Count != cntBoxes)
            {
                _boxes = Utils.DistinctBoxes(_boxes);
                cntBoxes = _boxes.Count;

                // find left not sparse column with simple rules
                FindLeftBoundaryNotSparse();

                // find first row with compact contents inside
                FindUpBoundaryNotSparse();

                // for four directions, skip the sparse edges
                SparseBoundariesTrim();

                // find the bottom boundary, not sparse, and dont overlap any upheaders of other _boxes
                BottomBoundaryTrim();

                // find the bottom boundary, not sparse, and dont overlap any upheaders of other _boxes
                UpBoundaryCompactTrim();

                // for four directions, remove the _boxes with the none edges
                NoneBorderFilter();
            }
        }

        private void UpHeaderTrim()
        {
            // refine the upper boundary of the _boxes to compact, especially when header exists

            // find the first not sparse row or header row as upper bound
            FindUpBoundaryNotSparse();
            // find the true header if it exists
            FindUpBoundaryIsHeader();
            // find the true header with a sparse upside row if it exists
            FindUpBoundaryIsClearHeader();
            // find first up header with compact contents
            FindUpBoundaryIsCompactHeader(0.6, 0.8);
            FindUpBoundaryIsCompactHeader(0.4, 0.7);
            FindUpBoundaryIsCompactHeader(0.2, 0.5);
        }

        private void FindUpBoundaryNotSparse()
        {
            // find the first not sparse row as up boundary
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box = _boxes[i];
                Boundary newBox = new Boundary(box.top, box.bottom, box.left, box.right);
                // find the first not sparse row as upper bound
                #region find the first not sparse row as upper bound
                Boundary upperBoundRowCandidate = new Boundary(box.top, box.top, box.left, box.right);
                Boundary upperBoundRowCandidateDown = new Boundary(box.top + 1, box.top + 1, box.left, box.right);
                while (upperBoundRowCandidate.top < newBox.bottom)
                {
                    if (_sheet.ContentExistValueDensity(upperBoundRowCandidate) < 2 * 0.2
                       || ((box.right - box.left + 1) >= 5 && (_sheet.TextDistinctCount(upperBoundRowCandidate) <= 1)))
                    {
                        upperBoundRowCandidate = new Boundary(upperBoundRowCandidate.top + 1, upperBoundRowCandidate.top + 1, newBox.left, newBox.right);
                    }
                    else if (!IsHeaderUp(upperBoundRowCandidate) && box.right - box.left > 7 && 2 * _sheet.sumContentExist.SubmatrixSum(upperBoundRowCandidate) <= _sheet.sumContentExist.SubmatrixSum(upperBoundRowCandidateDown)
                        && (_sheet.sumContentExist.SubmatrixSum(upperBoundRowCandidate) < 7 || _sheet.ContentExistValueDensity(upperBoundRowCandidate) < 0.3 * 2))
                    {
                        upperBoundRowCandidate = new Boundary(upperBoundRowCandidate.top + 1, upperBoundRowCandidate.top + 1, newBox.left, newBox.right);
                    }
                    else
                    {
                        break;
                    }
                }
                newBox.top = upperBoundRowCandidate.top;
                if (!box.Equals(newBox) && newBox.top < newBox.bottom)
                {
                    removedBoxes.Add(box);
                    appendBoxes.Add(newBox);
                }
                #endregion
            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void FindUpBoundaryIsHeader()
        {
            // find the true header if it exists
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box = _boxes[i];
                Boundary newBox = new Boundary(box.top, box.bottom, box.left, box.right);
                // find the first header if it exists
                #region find the first header
                Boundary upperBoundaryRow = new Boundary(box.top, box.top, box.left, box.right);
                while (!IsHeaderUp(upperBoundaryRow) && upperBoundaryRow.top <= box.top + 3 && upperBoundaryRow.top < box.bottom)
                {
                    upperBoundaryRow = new Boundary(upperBoundaryRow.top + 1, upperBoundaryRow.top + 1, newBox.left, newBox.right);
                }
                newBox.top = upperBoundaryRow.top;

                if (box.Equals(newBox)) continue;


                if (IsHeaderUpWithDataArea(upperBoundaryRow, box) && newBox.top < newBox.bottom)
                {

                    removedBoxes.Add(box);
                    appendBoxes.Add(newBox);
                }
                #endregion
            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void FindUpBoundaryIsCompactHeader(double threshDensityLow, double threshDensityHigh)
        {
            // find first header with compact rows
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();

            foreach (var box in _boxes)
            {
                if (box.right - box.left + 1 <= 4) continue;

                Boundary newBox = new Boundary(box.top, box.bottom, box.left, box.right);
                // find the first compact header 
                Boundary upperBoundaryRow = new Boundary(box.top, box.top, box.left, box.right);

                while (_sheet.ContentExistValueDensity(upperBoundaryRow) < 2 * threshDensityLow && upperBoundaryRow.top < box.top + 6 && upperBoundaryRow.top < box.bottom)
                {
                    upperBoundaryRow = new Boundary(upperBoundaryRow.top + 1, upperBoundaryRow.top + 1, newBox.left, newBox.right);
                }

                newBox.top = upperBoundaryRow.top;

                if (box.Equals(newBox) || _sheet.ContentExistValueDensity(upperBoundaryRow) < 2 * threshDensityLow)
                    continue;

                if (IsHeaderUpWithDataArea(upperBoundaryRow, box) && newBox.top < newBox.bottom)
                {
                    removedBoxes.Add(box);
                    appendBoxes.Add(newBox);
                }

            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void LeftHeaderTrim()
        {
            // find first header with compact rows
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();

            foreach (var box in _boxes)
            {
                Boundary newBox = new Boundary(box.top, box.bottom, box.left + 1, box.right);

                if (Utils.DistinctStrs(_sheet.contentStrs, box.top, box.bottom, box.left, box.left) <= 1)
                {
                    removedBoxes.Add(box);
                    appendBoxes.Add(newBox);
                }

            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void BottomTrim()
        {
            // find first header with compact rows
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();

            foreach (var box in _boxes)
            {
                int k = 0;
                while (k < box.bottom - box.top)
                {
                    Boundary bottomBox = new Boundary(box.bottom - k, box.bottom - k, box.left, box.right);

                    if (_sheet.ContentExistValueDensity(bottomBox) < 0.3 * 2 || (box.right - box.left + 1 > 3 && Utils.DistinctStrs(_sheet.contentStrs, box.bottom - k, box.bottom - k, box.left, box.right) <= 1))
                    {
                        k++;
                    }
                    else
                    {
                        break;
                    }
                }
                if (k > 0)
                {
                    Boundary newBox = new Boundary(box.top, box.bottom - k, box.left, box.right);
                    removedBoxes.Add(box);
                    appendBoxes.Add(newBox);
                }
            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void UpTrimSimple()
        {
            // find first header with compact rows
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();

            foreach (var box in _boxes)
            {
                int k = 0;
                while (k < box.bottom - box.top)
                {
                    if (Utils.DistinctStrs(_sheet.contentStrs, box.top + k, box.top + k, box.left, box.right) <= 1)
                    {
                        k++;
                    }
                    else
                    {
                        break;
                    }
                }
                if (k > 0)
                {
                    Boundary newBox = new Boundary(box.top + k, box.bottom, box.left, box.right);
                    removedBoxes.Add(box);
                    appendBoxes.Add(newBox);
                }
            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void UpBoundaryCompactTrim()
        {
            // find first header with compact rows
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();

            foreach (var box in _boxes)
            {
                Boundary newBox = new Boundary(box.top, box.bottom, box.left, box.right);
                // find the first compact header 
                Boundary upperBoundaryRow1 = new Boundary(box.top, box.top, box.left, box.right);
                Boundary upperBoundaryRow2 = new Boundary(box.top + 1, box.top + 1, box.left, box.right);

                while (!(IsHeaderUp(upperBoundaryRow1) && !IsHeaderUp(upperBoundaryRow2))
                    && 2 * _sheet.ContentExistValueDensity(upperBoundaryRow1) <= _sheet.ContentExistValueDensity(upperBoundaryRow2)
                    && upperBoundaryRow1.top < box.top + 6 && upperBoundaryRow2.top < box.bottom)
                {
                    upperBoundaryRow1 = new Boundary(upperBoundaryRow1.top + 1, upperBoundaryRow1.top + 1, newBox.left, newBox.right);
                    upperBoundaryRow2 = new Boundary(upperBoundaryRow2.top + 1, upperBoundaryRow2.top + 1, newBox.left, newBox.right);
                }

                newBox.top = upperBoundaryRow1.top;

                if (box.Equals(newBox) || _sheet.ContentExistValueDensity(upperBoundaryRow1) <= 0.6 * _sheet.ContentExistValueDensity(upperBoundaryRow2)) { continue; }


                if (newBox.top < newBox.bottom)
                {
                    removedBoxes.Add(box);
                    appendBoxes.Add(newBox);
                }

            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void FindUpBoundaryIsClearHeader()
        {
            // find the true header with a sparse upside row if it exists
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box = _boxes[i];
                Boundary newBox = new Boundary(box.top, box.bottom, box.left, box.right);
                // find the first header with a sparse upside row if it exists
                #region find the first header with a sparse upside row if it exists
                Boundary upperBoundaryRow = new Boundary(box.top, box.top, box.left, box.right);

                // find the first none line
                while (_sheet.sumContentExist.SubmatrixSum(upperBoundaryRow) > 3 && upperBoundaryRow.top < box.top + 6 && upperBoundaryRow.top < box.bottom)
                {
                    upperBoundaryRow = new Boundary(upperBoundaryRow.top + 1, upperBoundaryRow.top + 1, newBox.left, newBox.right);
                }

                // verify the boundary is sparse
                if (_sheet.sumContentExist.SubmatrixSum(upperBoundaryRow) > 3) continue;

                // continue tot find the first not sparse line below
                while (_sheet.sumContentExist.SubmatrixSum(upperBoundaryRow) < 3 && upperBoundaryRow.top < box.top + 2 && upperBoundaryRow.top < box.bottom)
                {
                    upperBoundaryRow = new Boundary(upperBoundaryRow.top + 1, upperBoundaryRow.top + 1, newBox.left, newBox.right);
                }
                newBox.top = upperBoundaryRow.top;

                if (box.Equals(newBox) || _sheet.sumContentExist.SubmatrixSum(upperBoundaryRow) < 3) continue;


                if (IsHeaderUpWithDataArea(upperBoundaryRow, box) && newBox.top < newBox.bottom)
                {
                    removedBoxes.Add(box);
                    appendBoxes.Add(newBox);
                }
                #endregion
            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void BottomBoundaryTrim()
        {
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();
            foreach (var box in _boxes)
            {
                // find out the boundary row line which is not sparse and without merge ranges
                // for merge ranges usually exists in the top of the box, but bot bottom
                Boundary newBox = new Boundary(box.top, box.bottom, box.left, box.right);
                int bottomLine = box.bottom;
                while (bottomLine > box.top)
                {
                    if (box.right - box.left + 1 > 6 &&
                            //exist merged in the bottoom(exclude the very left bide to avoid the left header)
                            (_sheet.ExistsMerged(new Boundary(bottomLine, bottomLine, Math.Min(box.left + 5, box.right - 1), box.right))
                            // exist merged region separately in the bottoom
                            || (_sheet.ExistsMerged(new Boundary(bottomLine, bottomLine, box.left, box.right))
                            && !_sheet.ExistsMerged(new Boundary(Math.Max(box.top, bottomLine - 4), bottomLine - 2, box.left, box.right)))
                            // sparsity
                            || (_sheet.sumContentExist.SubmatrixSum(new Boundary(bottomLine, bottomLine, box.left, box.left)) == 0
                            && _sheet.ContentExistValueDensity(new Boundary(bottomLine, bottomLine, box.left, box.right)) < 0.15 * 2)
                            || (_sheet.sumContentExist.SubmatrixSum(new Boundary(bottomLine, bottomLine, box.left, box.left + 2)) == 0
                            && _sheet.ContentExistValueDensity(new Boundary(bottomLine, bottomLine, box.left, box.right)) < 0.3 * 2)))
                    {
                        bottomLine -= 1;
                        continue;
                    }
                    else if (box.right - box.left + 1 >= 2 && _sheet.sumContentExist.SubmatrixSum(new Boundary(bottomLine, bottomLine, box.left, box.right)) < 3)
                    {
                        bottomLine -= 1;
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }
                newBox.bottom = bottomLine;

                // if the bottom of the box overlaps other _boxes' header, then need to skip them
                int bottomLineSkipHeader = bottomLine;
                int cnt = 0;
                while (cnt < 5 && bottomLine > box.top &&
                    _sheet.sumContentExist.SubmatrixSum(new Boundary(bottomLineSkipHeader, bottomLineSkipHeader, box.left, box.right)) >= 2
                    && HeaderRate(new Boundary(bottomLineSkipHeader, bottomLineSkipHeader, box.left, box.right), step: 0) > 0.6)
                {
                    cnt += 1;
                    bottomLineSkipHeader -= 1;
                }
                // usually header have less than three lines
                if (cnt < 3 && _sheet.sumContentExist.SubmatrixSum(new Boundary(bottomLineSkipHeader, bottomLineSkipHeader, box.left, box.right)) == 0)
                {
                    while (bottomLineSkipHeader > box.top && _sheet.sumContentExist.SubmatrixSum(new Boundary(bottomLineSkipHeader, bottomLineSkipHeader, box.left, box.right)) == 0)
                    {
                        bottomLineSkipHeader -= 1;
                    }
                    bottomLine = bottomLineSkipHeader;
                    newBox.bottom = bottomLine;
                }

                if (box.Equals(newBox)) continue;
                removedBoxes.Add(box);
                if (newBox.top < newBox.bottom)
                {
                    appendBoxes.Add(newBox);
                }
            }

            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void FindLeftBoundaryNotSparse()
        {
            // find left not sparse column with simple rules
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();
            foreach (var box in _boxes)
            {
                Boundary newBox = new Boundary(box.top, box.bottom, box.left, box.right);
                bool mark = true;
                Boundary leftLine = new Boundary(box.top, box.bottom, box.left, box.left);
                while (mark && newBox.left < newBox.right)
                {
                    mark = false;
                    if (newBox.left > 0)
                    {
                        leftLine = new Boundary(newBox.top, newBox.bottom, newBox.left, newBox.left);

                        if ((box.bottom - box.top + 1) >= 5 && !(_sheet.ContentExistValueDensity(leftLine) >= 0.7) && _sheet.TextDistinctCount(leftLine) <= 1)
                        {
                            mark = true;
                            newBox.left = newBox.left + 1;
                        }
                        //else if (((box.down - box.up + 1) >= 10 && sheet.TextDistinctCount(lineBox) <= 2))
                        //{
                        //    mark = true;
                        //    newBox.left = newBox.left + 1;
                        //}
                        else if (((box.bottom - box.top) > 3 && _sheet.sumContentExist.SubmatrixSum(leftLine) < 5)
                            || ((box.bottom - box.top + 1) > 10 && (_sheet.sumContentExist.SubmatrixSum(leftLine) < 7 || _sheet.ContentExistValueDensity(leftLine) < 2 * 0.15)))
                        {
                            mark = true;
                            newBox.left = newBox.left + 1;
                        }

                    }
                }
                if (!box.Equals(newBox))
                {
                    removedBoxes.Add(box);
                    if (newBox.left < newBox.right && newBox.top < newBox.bottom)
                    {
                        appendBoxes.Add(newBox);
                    }
                }
            }

            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }
    }
}
```

## File: code/HeaderReco.cs
```csharp
using System;
using System.Linq;
using System.Collections.Generic;

namespace SpreadsheetLLM.Heuristic
{
    internal partial class TableDetectionHybrid
    {
        private Dictionary<Boundary, bool> _headerUpMap = new Dictionary<Boundary, bool>();
        private Dictionary<Boundary, bool> _headerLeftMap = new Dictionary<Boundary, bool>();

        public bool IsHeaderUp(Boundary header)
        {
            if (_headerUpMap.ContainsKey(header))
            {
                return _headerUpMap[header];
            }
            if (IsHeaderUpSimple(header))
            {
                Boundary HeaderDownSide = Utils.DownRow(header, start: -1);

                if (_sheet.ComputeSimilarRow(header, HeaderDownSide) < 0.15 && IsHeaderUpSimple(HeaderDownSide))
                {
                    _headerUpMap[header] = false;
                    return false;
                }
                else if (_sheet.sumContentExist.SubmatrixSum(header) == 2 && _sheet.sumContentExist.SubmatrixSum(HeaderDownSide) == 2
                    && HeaderRate(header, step: 0) == HeaderRate(HeaderDownSide, step: 0))
                {
                    return false;
                }
                else
                {
                    _headerUpMap[header] = true;
                    return true;
                }
            }
            _headerUpMap[header] = false;
            return false;
        }

        public bool IsHeaderLeft(Boundary header)
        {
            if (_headerLeftMap.ContainsKey(header))
            {
                return _headerLeftMap[header];
            }
            if (IsHeaderLeftSimple(header))
            {
                _headerLeftMap[header] = true;
                return true;
            }

            Boundary headerRight = Utils.RightCol(header, start: -1);
            if (_sheet.ContentExistValueDensity(headerRight) >= 1.5 * _sheet.ContentExistValueDensity(header)
                && _sheet.ContentExistValueDensity(headerRight) > 2 * 0.6
                && IsHeaderLeftSimple(headerRight))
            {
                _headerLeftMap[header] = true;
                return true;

            }
            _headerLeftMap[header] = false;
            return false;
        }

        private bool IsHeaderUpSimple(Boundary header)
        {
            // only consider this simple row, dont consider the relation between the next rows
            if (header.right == header.left)
            {
                return false;
            }
            if (_sheet.sumContentExist.SubmatrixSum(header) <= 4 && HeaderRate(header, step: 0) <= 0.5)
            {
                return false;
            }
            if ((Utils.AreaSize(header) > 4 && _sheet.TextDistinctCount(header) <= 2)
                 || (Utils.AreaSize(header) > 3 && _sheet.TextDistinctCount(header) < 2))
            {
                return false;
            }
            Boundary rightRegionOfHeader = new Boundary(header.top, header.bottom, Math.Min(header.left, header.right - 5) + 3, header.right);
            if (_sheet.ContentExistValueDensity(header) > 2 * 0.3
                && HeaderRate(header) > 0.4
                && HeaderRate(rightRegionOfHeader) > 0.3)
            {
                return true;
            }
            return false;
        }

        private bool IsHeaderLeftSimple(Boundary header)
        {
            // only consider this simple col, dont consider the relation between the next cols

            if (header.bottom - header.top == 0)
            {
                return false;
            }
            if (header.bottom - header.top == 1 && HeaderRate(header) >= 0.5)
            {
                return true;
            }
            if ((Utils.AreaSize(header) > 4 && _sheet.TextDistinctCount(header) <= 2)
                || (Utils.AreaSize(header) > 3 && _sheet.TextDistinctCount(header) < 2))
            {
                return false;
            }
            if (_sheet.sumContentExist.SubmatrixSum(header) <= 4 && HeaderRate(header) <= 0.5)
            {
                return false;
            }
            Boundary upRegionOfHeader = new Boundary(Math.Min(header.top, header.bottom - 5) + 3, header.bottom, header.left, header.right);
            if (_sheet.ContentExistValueDensity(header) > 2 * 0.3 &&
                HeaderRate(header) > 0.4 && HeaderRate(upRegionOfHeader) > 0.3)
            {
                return true;
            }
            return false;
        }

        private bool IsHeaderUpWithDataArea(Boundary upperBoundaryRow, Boundary box)
        {
            int up = upperBoundaryRow.top;
            if (!IsHeaderUp(upperBoundaryRow)) { return false; }
            // find the bottom of up header
            while (IsHeaderUpSimple(upperBoundaryRow) && upperBoundaryRow.top <= up + 2 && upperBoundaryRow.top < box.bottom)
            {
                upperBoundaryRow = new Boundary(upperBoundaryRow.top + 1, upperBoundaryRow.top + 1, upperBoundaryRow.left, upperBoundaryRow.right);
            }

            bool validHeaderMark = true;
            // verify the next rows under the header are not like headers
            for (int k = 1; k <= 3; k++)
            {
                Boundary nextRow = new Boundary(upperBoundaryRow.top + k, upperBoundaryRow.top + k, upperBoundaryRow.left, upperBoundaryRow.right);
                if (nextRow.top <= box.bottom && IsHeaderUp(nextRow))
                {
                    validHeaderMark = false;
                    break;
                }
            }
            return validHeaderMark;
        }

        private double HeaderRate(Boundary box, int step = 6)
        {
            // calculate the rate in the cells in the header proposal that not numerical
            int cntExistContent = 0;
            int cntAllCells = 0;
            int cntHeaderLikeCell = 0;

            for (int i = box.top; i <= box.bottom; i++)
            {
                for (int j = box.left; j <= box.right; j++)
                {
                    cntAllCells++;
                    Boundary headerControledSuroundingRegion = new Boundary(i, i, j, j);
                    if (box.top == box.bottom)
                    {
                        headerControledSuroundingRegion = new Boundary(i, i + step, j, j);
                    }
                    else if (box.top == box.bottom)
                    {
                        headerControledSuroundingRegion = new Boundary(i, i, j, j + step);
                    }
                    if (_sheet.sumContentExist.SubmatrixSum(headerControledSuroundingRegion) != 0)
                    {
                        cntExistContent++;
                        if (_sheet.featureMap[i - 1, j - 1].MarkText)
                        {
                            if ((_sheet.featureMap[i - 1, j - 1].AlphabetRatio >= _sheet.featureMap[i - 1, j - 1].NumberRatio && _sheet.featureMap[i - 1, j - 1].AlphabetRatio != 0)
                                || _sheet.featureMap[i - 1, j - 1].AlphabetRatio * _sheet.featureMap[i - 1, j - 1].TextLength > 2.5 || _sheet.featureMap[i - 1, j - 1].SpCharRatio > 0)
                            {
                                cntHeaderLikeCell++;
                            }
                        }
                    }
                }
            }

            if (cntAllCells == 0) return 0;
            return (double)cntHeaderLikeCell / Math.Max(cntExistContent, (double)cntAllCells / 3);
        }
    }
}
```

## File: code/HeuristicTableDetector.cs
```csharp
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using SheetCore;

namespace SpreadsheetLLM.Heuristic
{
    public static class HeuristicTableDetector
    {
        private const int WindowHeight = 5;
        private const int WindowWidth = 5;
        /// <summary>
        /// The heuristic TableSense performance is relative to input grid size (Height*Width), but not total non-blank cells in the grid.
        /// In extreme cases, a chunk with 10000 cells may shape an 10000*10000 window that causes significant overhead.
        /// Therefore, we introduce <see cref="MaximumInputGridSize"/> that defines an upperbound of input grid size.
        /// </summary>
        private const int MaximumInputGridSize = 250000;
        private static readonly List<Boundary> EmptyIntArrayList = new List<Boundary>();

        public static IList<IRange> Detect(ISheet inputSheet, bool eliminateOverlaps, IList<string> logs)
        {
            var result = DetectBoundary(inputSheet, eliminateOverlaps, logs);
            return result.Select(box => (IRange)new Range(box.top, box.bottom, box.left, box.right)).ToList();
        }

        public static IList<IRange> HybridCombine(ISheet inputSheet, IList<IRange> heuResults, IList<IRange> mlResults)
        {
            int height = inputSheet.Height;
            int width = inputSheet.Width;
            if (height == 0 && width == 0)
            {
                return System.Array.Empty<IRange>();
            }
            if (height * width > MaximumInputGridSize)
            {
                return System.Array.Empty<IRange>();
            }

            // Construct a Sheet object from inputSheet
            var sheet = new CoreSheet(inputSheet);

            CellFeatures.ExtractFeatures(sheet, out var features, out var cells, out var formula);
            var sheetMap = new SheetMap(WindowHeight + 2, WindowWidth + 2, features, cells, formula, sheet.MergedAreas, EmptyIntArrayList);

            var logs = new List<string>();
            var detector = new TableDetectionHybrid(new List<string>());

            var heuBoundaries = heuResults.Select(boundary => new Boundary(
                up: boundary.FirstRow - inputSheet.FirstRow + WindowHeight + 3,
                down: boundary.LastRow - inputSheet.FirstRow + WindowHeight + 3,
                left: boundary.FirstColumn - inputSheet.FirstColumn + WindowWidth + 3,
                right: boundary.LastColumn - inputSheet.FirstColumn + WindowWidth + 3
            )).ToList();

            var mlBoundaries = mlResults.Select(boundary => new Boundary(
                up: boundary.FirstRow - inputSheet.FirstRow + WindowHeight + 3,
                down: boundary.LastRow - inputSheet.FirstRow + WindowHeight + 3,
                left: boundary.FirstColumn - inputSheet.FirstColumn + WindowWidth + 3,
                right: boundary.LastColumn - inputSheet.FirstColumn + WindowWidth + 3
            )).ToList();

            var boxes = detector.HybridCombine(sheetMap, heuBoundaries, mlBoundaries);

            return boxes.Select(box => (IRange)new Range(
                box.top + inputSheet.FirstRow - WindowHeight - 3, // start_row, 1 indexed
                box.bottom + inputSheet.FirstRow - WindowHeight - 3, // end_row
                box.left + inputSheet.FirstColumn - WindowWidth - 3, // start_col
                box.right + inputSheet.FirstColumn - WindowWidth - 3 // end_col
            )).ToArray();
        }

        internal static List<Boundary> DetectBoundary(SheetCore.ISheet inputSheet, bool eliminateOverlaps, IList<string> logs)
        {
            int height = inputSheet.Height;
            int width = inputSheet.Width;
            if (height == 0 && width == 0)
            {
                logs.Add("Zero sized input sheet");
                return EmptyIntArrayList;
            }
            if (height * width > MaximumInputGridSize)
            {
                logs.Add("Skipped large sized input sheet");
                return EmptyIntArrayList;
            }

            // Construct a Sheet object from inputSheet
            var sheet = new CoreSheet(inputSheet);

            Stopwatch stopwatch = Stopwatch.StartNew();
            CellFeatures.ExtractFeatures(sheet, out var features, out var cells, out var formula);
            logs.Add($"ExtractFeatures ElapsedTime: {stopwatch.ElapsedMilliseconds}");
            stopwatch.Restart();
            var sheetMap = new SheetMap(WindowHeight + 2, WindowWidth + 2, features, cells, formula, sheet.MergedAreas, EmptyIntArrayList);
            logs.Add($"SheetMap: {stopwatch.ElapsedMilliseconds}");
            stopwatch.Restart();
            var detector = new TableDetectionHybrid(logs);
            var boxes = detector.Detect(sheetMap, eliminateOverlaps);  // 1-indexed, based on left-top cell
            logs.Add($"TableDetectionHybrid.Detect. ElapsedTime: {stopwatch.ElapsedMilliseconds}");
            for (int i = 0; i < boxes.Count; i++)
            {
                var box = boxes[i];
                box.top += inputSheet.FirstRow - WindowHeight - 3;     // start_row, 1 indexed
                box.bottom += inputSheet.FirstRow - WindowHeight - 3;  // end_row
                box.left += inputSheet.FirstColumn - WindowWidth - 3;  // start_col
                box.right += inputSheet.FirstColumn - WindowWidth - 3; // end_col
                boxes[i] = box;
            }

            return boxes;
        }
    }
}
```

## File: code/RegionGrowthDetector.cs
```csharp
using System;
using System.Collections.Generic;

namespace SpreadsheetLLM.Heuristic
{
    internal class RegionGrowthDetector
    {
        private static int[] dx = { +1, -1, 0, 0, +1, -1, +1, -1 };
        private static int[] dy = { 0, 0, +1, -1, -1, -1, +1, +1 };
        private static int step = 4;
        private const int stRow = 1;
        private const int stCol = 1;

        /// <summary>
        /// Split tables from an Excel sheet
        /// </summary>
        public static List<Boundary> FindConnectedRanges(string[,] content, int[,] valueMapBorderOrNull, int thresholdHor, int thresholdVer, int direct = 1)
        {
            var ret = new List<(int, int, int, int)>();
            int rowNum = content.GetLength(0);
            int colNum = content.GetLength(1);

            int rangeUpRow = stRow - 1;
            int rangeLeftCol = stCol - 1;
            int rangeDownRow = stRow + rowNum - 2;
            int rangeRightCol = stCol + colNum - 2;
            bool[,] vis = new bool[rowNum, colNum];
            int[,] counterHor = new int[rowNum, colNum];
            int[,] counterVer = new int[rowNum, colNum];
            for (int i = 0; i < rowNum; ++i)
            {
                for (int j = 0; j < colNum; ++j)
                {
                    counterHor[i, j] = thresholdHor;
                    counterVer[i, j] = thresholdVer;
                }
            }

            var rangeRow = new int[rangeDownRow - rangeUpRow + 1];
            var rangeCol = new int[rangeRightCol - rangeLeftCol + 1];
            if (direct == 0)
            {
                rangeRow[0] = rangeUpRow;
                for (int row_i = 1; row_i <= rangeDownRow - rangeUpRow; ++row_i)
                {
                    rangeRow[row_i] = rangeRow[row_i - 1] + 1;
                }

                rangeCol[0] = rangeLeftCol;
                for (int col_i = 1; col_i <= rangeRightCol - rangeLeftCol; ++col_i)
                {
                    rangeCol[col_i] = rangeCol[col_i - 1] + 1;
                }
            }
            else
            {
                rangeRow[0] = rangeDownRow;
                for (int row_i = 1; row_i <= rangeDownRow - rangeUpRow; ++row_i)
                {
                    rangeRow[row_i] = rangeRow[row_i - 1] - 1;
                }

                rangeCol[0] = rangeLeftCol;
                for (int col_i = 1; col_i <= rangeRightCol - rangeLeftCol; ++col_i)
                {
                    rangeCol[col_i] = rangeCol[col_i - 1] + 1;
                }
            }

            for (int row_i = 0; row_i <= rangeDownRow - rangeUpRow; ++row_i)
            {
                for (int col_i = 0; col_i <= rangeRightCol - rangeLeftCol; ++col_i)
                {
                    int rowIdx = rangeRow[row_i];
                    int colIdx = rangeCol[col_i];
                    //if the cell has been visited during connected region search, just ignore
                    if (vis[rowIdx - stRow + 1, colIdx - stCol + 1])
                        continue;

                    //ignore empty or null cells, because we cannot start search from these cells
                    if (content[rowIdx, colIdx] == "")
                        continue;

                    //the following code will try to find a connected region containing the current cell with a breadth-first algorithm
                    var q = new Queue<(int row, int col)>();
                    q.Enqueue((rowIdx, colIdx));
                    vis[rowIdx - stRow + 1, colIdx - stCol + 1] = true;

                    int minRow = int.MaxValue;
                    int minCol = int.MaxValue;
                    int maxRow = int.MinValue;
                    int maxCol = int.MinValue;

                    while (q.Count > 0)
                    {
                        var cellCordinate = q.Dequeue();
                        int row = cellCordinate.row;
                        int col = cellCordinate.col;

                        //If the current cell does not have right/down neighbors 
                        if (counterHor[row - stRow + 1, col - stCol + 1] == 0 || counterVer[row - stRow + 1, col - stCol + 1] == 0)
                        {
                            continue;
                        }

                        minRow = Math.Min(row, minRow);
                        minCol = Math.Min(col, minCol);
                        maxRow = Math.Max(row, maxRow);
                        maxCol = Math.Max(col, maxCol);

                        for (int i = 0; i < step; ++i)
                        {
                            //Search the neighboring cell
                            int nextRow = row + dx[i];
                            int nextCol = col + dy[i];
                            if (nextRow >= rangeUpRow && nextRow <= rangeDownRow
                                && nextCol >= rangeLeftCol && nextCol <= rangeRightCol
                                && !vis[nextRow - stRow + 1, nextCol - stCol + 1])
                            {
                                vis[nextRow - stRow + 1, nextCol - stCol + 1] = true;
                                try
                                {
                                    if (content[nextRow, nextCol] == "")
                                    {
                                        if (dy[i] != 0)
                                        {
                                            counterHor[nextRow - stRow + 1, nextCol - stCol + 1] = counterHor[row - stRow + 1, col - stCol + 1] - 1;
                                        }
                                        if (dx[i] != 0)
                                        {
                                            counterVer[nextRow - stRow + 1, nextCol - stCol + 1] = counterVer[row - stRow + 1, col - stCol + 1] - 1;
                                        }
                                    }
                                    // If there exists vertical border, we can mark it as part of the current table.
                                    if (valueMapBorderOrNull != null && valueMapBorderOrNull[nextRow, nextCol] != 0)
                                    {
                                        if (dy[i] != 0)
                                            counterHor[nextRow - stRow + 1, nextCol - stCol + 1] = thresholdHor;
                                        if (dx[i] != 0)
                                            counterVer[nextRow - stRow + 1, nextCol - stCol + 1] = thresholdVer;
                                    }
                                }
                                catch
                                {
                                    if (dy[i] != 0)
                                    {
                                        counterHor[nextRow - stRow + 1, nextCol - stCol + 1] = counterHor[row - stRow + 1, col - stCol + 1] - 1;
                                    }
                                    if (dx[i] != 0)
                                    {
                                        counterVer[nextRow - stRow + 1, nextCol - stCol + 1] = counterVer[row - stRow + 1, col - stCol + 1] - 1;
                                    }

                                }
                                q.Enqueue((nextRow, nextCol));
                            }
                        }
                    }

                    if (minRow == int.MaxValue || minCol == int.MaxValue || maxRow == int.MinValue || maxCol == int.MinValue)
                        continue;

                    if (maxRow - minRow > 1)
                    {
                        ret.Add((minRow, minCol, maxRow, maxCol));
                        for (int ri = minRow; ri <= maxRow; ++ri)
                        {
                            for (int rj = minCol; rj <= maxCol; ++rj)
                            {
                                vis[ri - stRow + 1, rj - stCol + 1] = true;
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < 3; i++)
            {
                ret = Trim(content, ret);
            }

            var boxes = ret;
            var boxesList = new List<Boundary>();
            foreach (var box in boxes)
            {
                if (box.Item1 >= box.Item3 || box.Item2 >= box.Item4)
                    continue;

                boxesList.Add(new Boundary(box.Item1 + 1, box.Item3 + 1, box.Item2 + 1, box.Item4 + 1));
            }

            return boxesList;
        }

        /// <summary>
        /// Remove leading or tailing empty rows or columns for each sub-tables
        /// </summary>
        private static List<(int, int, int, int)> Trim(string[,] content, List<(int, int, int, int)> list)
        {
            var ret = new List<(int, int, int, int)>();
            foreach (var cellRange in list)
            {
                int upRow = cellRange.Item1;
                int leftCol = cellRange.Item2;
                int downRow = cellRange.Item3;
                int rightCol = cellRange.Item4;

                //Remove the leading empty rows
                for (int i = upRow; i <= downRow; ++i)
                {
                    bool isEmpty = IsRowEmpty(content, leftCol, rightCol, i);
                    if (isEmpty)
                    {
                        ++upRow;
                    }
                    else
                    {
                        break;
                    }
                }

                //Remove the tailing empty rows
                for (int i = downRow; i >= upRow; --i)
                {
                    bool isEmpty = IsRowEmpty(content, leftCol, rightCol, i);
                    if (isEmpty)
                    {
                        --downRow;
                    }
                    else
                    {
                        break;
                    }
                }

                //Remove the leading empty columns
                for (int j = leftCol; j <= rightCol; ++j)
                {
                    bool isEmpty = IsColEmpty(content, upRow, downRow, j);
                    if (isEmpty)
                    {
                        ++leftCol;
                    }
                    else
                    {
                        break;
                    }
                }

                //Remove the tailing empty columns
                for (int j = rightCol; j >= leftCol; --j)
                {
                    bool isEmpty = IsColEmpty(content, upRow, downRow, j);
                    if (isEmpty)
                    {
                        --rightCol;
                    }
                    else
                    {
                        break;
                    }
                }

                ret.Add((upRow, leftCol, downRow, rightCol));
            }
            return ret;
        }

        /// <summary>
        /// Check whether a row is an empty row
        /// </summary>
        private static bool IsColEmpty(string[,] content, int upRow, int downRow, int j)
        {
            for (int i = upRow; i <= downRow; ++i)
            {
                if (content[i, j] != "")
                    return false;
            }

            return true;
        }

        /// <summary>
        /// Check whether a row is an empty row
        /// </summary>
        private static bool IsRowEmpty(string[,] content, int leftCol, int rightCol, int i)
        {
            for (int j = leftCol; j <= rightCol; ++j)
            {
                if (content[i, j] != "")
                    return false;
            }

            return true;
        }
    }
}
```

## File: code/SheetMap.cs
```csharp
using System;
using System.Linq;
using System.Text.RegularExpressions;
using System.Collections.Generic;

namespace SpreadsheetLLM.Heuristic
{
    internal class SheetMap
    {
        public int Height;
        public int Width;

        int rowOffset;
        int colOffset;

        public List<Boundary> pivotBoxes;
        public List<Boundary> mergeBoxes;

        public string[,] contentStrs;

        public string[,] formuStrs;
        public List<Boundary>[,] formulaRanges;

        public CellFeatures[,] featureMap;

        public int[,] valueMapContent;
        public int[,] valueMapBorder;
        public int[,] valueMapColor;
        public int[,] valueMapAll;

        public int[,] sumContent;
        public int[,] sumContentExist;
        public int[,] sumBorder;
        public int[,] sumBorderCol;
        public int[,] sumBorderRow;
        public int[,] sumColor;
        public int[,] sumAll;

        public List<int> rowBoundaryLines;
        public List<int> colBoundaryLines;

        List<List<bool>> rowDiffsBool;
        List<List<bool>> colDiffsBool;
        List<List<double>> rowDiffs;
        List<List<double>> colDiffs;


        public List<Boundary> conhensionRegions;
        public List<Boundary> mergeforcedConhensionRegions;
        public List<Boundary> colforcedConhensionRegions;
        public List<Boundary> rowforcedConhensionRegions;
        public List<Boundary> colorforcedConhensionRegions;
        public List<Boundary> edgeforcedConhensionRegions;
        public List<Boundary> cohensionBorderRegions;
        public List<Boundary> smallCohensionBorderRegions;

        public SheetMap(int rowOffsetNum, int colOffsetNum, CellFeatures[,] features, string[,] contents, string[,] formus,
            List<Boundary> mergedRanges, List<Boundary> pivotTables)
        {
            // the row and column distance to shift the sheet 
            rowOffset = rowOffsetNum;
            colOffset = colOffsetNum;

            // list of pivottables
            pivotBoxes = ExtendPivot(pivotTables);

            // merged Regions in the sheet
            mergeBoxes = ConvertMergedRanges(mergedRanges);

            // shift the strings by rowOffset and colOffset in the sheet
            featureMap = ExtendFeatureMap(features, rowOffset, colOffset, out Height, out Width);
            contentStrs = ExtendMatrix(contents, rowOffset, colOffset);
            formuStrs = ExtendMatrix(formus, rowOffset, colOffset);

            // find the formula reference regions by regular expressions match
            formulaRanges = preProcessFormulas(formuStrs);

            // update the feature map for the sheet by the formulas 
            UpdateFeatAndContentsByFormula();

            // calculate the many types of value maps
            CalculateBasicValueMaps();
        }

        private void CalculateBasicValueMaps()
        {
            // maps
            valueMapContent = ComputeValueMap(feature => (feature.HasFormula ? 2 : 0) + (feature.MarkText ? 2 : 0));
            int[,] valueMapContentExist = ComputeValueMap(feature => feature.HasFormula || feature.MarkText ? 2 : 0);
            valueMapBorder = ComputeValueMap(feature =>
            {
                int cntBorder = Convert.ToInt32(feature.HasBottomBorder) + Convert.ToInt32(feature.HasTopBorder) + Convert.ToInt32(feature.HasLeftBorder) + Convert.ToInt32(feature.HasRightBorder);
                return (cntBorder >= 3 ? cntBorder - 1 : cntBorder) * 2;
            });
            int[,] valueMapBorderCol = ComputeValueMap(feature => feature.HasLeftBorder || feature.HasRightBorder ? 2 : 0);
            int[,] valueMapBorderRow = ComputeValueMap(feature => feature.HasBottomBorder || feature.HasTopBorder ? 2 : 0);
            valueMapColor = ComputeValueMap(feature => feature.HasFillColor ? 2 : 0);
            valueMapAll = ComputeValueMap((i, j) => Math.Min(valueMapContent[i, j] + valueMapBorder[i, j] + valueMapColor[i, j], 16));

            // sum maps
            sumContent = valueMapContent.CalcSumMatrix();
            sumContentExist = valueMapContentExist.CalcSumMatrix();
            sumBorder = valueMapBorder.CalcSumMatrix();
            sumBorderCol = valueMapBorderCol.CalcSumMatrix();
            sumBorderRow = valueMapBorderRow.CalcSumMatrix();
            sumColor = valueMapColor.CalcSumMatrix();
            sumAll = valueMapAll.CalcSumMatrix();
        }

        public void CohensionDetection()
        {
            // detect different types of cohension regions , often can not be partly overlaped by a correct box
            colforcedConhensionRegions = new List<Boundary>();
            rowforcedConhensionRegions = new List<Boundary>();
            colorforcedConhensionRegions = new List<Boundary>();
            mergeforcedConhensionRegions = new List<Boundary>();
            edgeforcedConhensionRegions = new List<Boundary>();

            // several kinds of forced compact regions that can not be cut off by a box
            conhensionRegions = new List<Boundary>();

            //  compact regions that formed by cell borders
            cohensionBorderRegions = new List<Boundary>();
            //  small compact regions that formed by cell borders
            smallCohensionBorderRegions = new List<Boundary>();


            //// generate several kinds of cohesion regions
            mergeforcedConhensionRegions = GenerateMergedCohensions(ref rowBoundaryLines, ref colBoundaryLines);
            colorforcedConhensionRegions = generateColorCohensions();
            //colforcedConhensionRegions = generateColContentCohensions(patienceThresh: 2, stepThresh: 3);
            //rowforcedConhensionRegions = generateRowContentCohensions(patienceThresh: 1, stepThresh: 1);

            // generate forcedBorderRegions and forcedBorderRegionsSmall
            dealWithBorderCohensionRegions();

            conhensionRegions.AddRange(colforcedConhensionRegions);
            conhensionRegions.AddRange(rowforcedConhensionRegions);

            conhensionRegions.AddRange(mergeforcedConhensionRegions);
        }

        #region Propose Boundary Lines
        public void ProposeBoundaryLines()
        {
            // initialise candidate boundary lines
            rowBoundaryLines = new List<int>();
            colBoundaryLines = new List<int>();

            //// compute valueMap differences
            computeValueDiff();

            //// propose box lines candidates in the row and column directions
            rowBoundaryLines = ProposeLinesByDiff(rowDiffsBool, lineDiffThresh: 1.0);
            colBoundaryLines = ProposeLinesByDiff(colDiffsBool, lineDiffThresh: 1.0);

            //// remove duplicates
            rowBoundaryLines = rowBoundaryLines.Distinct().ToList();
            colBoundaryLines = colBoundaryLines.Distinct().ToList();

            //// sort lines by the ununiform degree
            rowBoundaryLines.Sort((x, y) => -rowDiffsBool[x].Count(diff => diff).CompareTo(rowDiffsBool[y].Count(diff => diff)));
            colBoundaryLines.Sort((x, y) => -colDiffsBool[x].Count(diff => diff).CompareTo(colDiffsBool[y].Count(diff => diff)));
        }

        private static List<int> ProposeLinesByDiff(List<List<bool>> diffs, double lineDiffThresh)
        {
            return Enumerable.Range(0, diffs.Count)
                .Where(index => diffs[index].Count(diff => diff) >= lineDiffThresh)
                .ToList();
        }

        public double ComputeBorderDiffsRow(Boundary box)
        {
            int up = box.top - 2;
            int down = box.bottom - 1;
            int left = box.left - 2;
            int right = box.right - 1;

            double cnt1;
            double cnt2;

            cnt1 = 0;
            for (int j = left; j <= right; j++)
            {
                if (rowDiffsBool[up][j] & rowDiffs[up][j] > 0)
                    cnt1 = cnt1 + Math.Abs(rowDiffs[up][j]);
            }

            cnt2 = 0;
            for (int j = left; j <= right; j++)
            {
                if (rowDiffsBool[down][j] && rowDiffs[down][j] < 0)
                    cnt2 = cnt2 + Math.Abs(rowDiffs[down][j]);
            }

            double result = (cnt1 + cnt2) / Utils.Width(box);
            return result;
        }
        public double ComputeBorderDiffsCol(Boundary box)
        {
            int up = box.top - 2;
            int down = box.bottom - 1;
            int left = box.left - 2;
            int right = box.right - 1;

            double cnt3;
            double cnt4;

            cnt3 = 0;
            for (int i = up; i <= down; i++)
            {
                if (colDiffsBool[left][i] && colDiffs[left][i] > 0)
                    cnt3 = cnt3 + Math.Abs(colDiffs[left][i]);
            }

            cnt4 = 0;
            for (int i = up; i <= down; i++)
            {
                if (colDiffsBool[right][i] && colDiffs[right][i] < 0)
                    cnt4 = cnt4 + Math.Abs(colDiffs[right][i]);
            }

            double result = (cnt3 + cnt4) / Utils.Height(box);
            return result;
        }
        #endregion

        #region extend
        public List<Boundary> ExtendPivot(List<Boundary> pivotTables)
        {
            return pivotTables.Select(pivotBox => new Boundary(
                pivotBox.top + rowOffset,
                pivotBox.bottom + rowOffset,
                pivotBox.left + colOffset,
                pivotBox.right + colOffset)).ToList();
        }

        private static CellFeatures[,] ExtendFeatureMap(CellFeatures[,] features, int rowOffset, int colOffset, out int height, out int width)
        {
            height = features.GetLength(0) + 2 * rowOffset;
            width = features.GetLength(1) + 2 * colOffset;
            var results = new CellFeatures[height, width];

            for (int i = 0; i < height; i++)
            {
                for (int j = 0; j < width; j++)
                {
                    if (rowOffset <= i && i < height - rowOffset && colOffset <= j && j < width - colOffset)
                    {
                        results[i, j] = features[i - rowOffset, j - colOffset];
                    }
                    else
                    {
                        results[i, j] = CellFeatures.EmptyFeatureVec;
                    }
                }
            }

            return results;
        }

        private static string[,] ExtendMatrix(string[,] matrixIn, int offsetRow, int offsetCol)
        {
            string[,] matrixOut = new string[matrixIn.GetLength(0) + 2 * offsetRow, matrixIn.GetLength(1) + 2 * offsetCol];
            for (int i = 0; i < matrixOut.GetLength(0); i++)
            {
                for (int j = 0; j < matrixOut.GetLength(1); j++)
                {
                    if (offsetRow <= i && i < matrixOut.GetLength(0) - offsetRow && offsetCol <= j && j < matrixOut.GetLength(1) - offsetCol)
                    {
                        matrixOut[i, j] = matrixIn[i - offsetRow, j - offsetCol];
                    }
                    else
                    {
                        matrixOut[i, j] = "";
                    }
                }
            }
            return matrixOut;
        }
        #endregion

        #region block regions generation
        public List<Boundary> GenerateBlockRegions()
        {
            Boundary initialRegion = new Boundary(1, Height, 1, Width);
            List<Boundary> blockRegions = new List<Boundary>();
            List<Boundary> prevRegions = new List<Boundary> { initialRegion };
            List<Boundary> newRegions = new List<Boundary> { initialRegion };

            int cntSplit = 1;
            //iterate until no split generated
            while (cntSplit > 0)
            {
                prevRegions = newRegions;
                newRegions = new List<Boundary>();
                cntSplit = 0;
                for (int i = 0; i < prevRegions.Count; i++)
                {
                    if (prevRegions[i].bottom - prevRegions[i].top <= 0 || prevRegions[i].right - prevRegions[i].left <= 0)
                    {
                        continue;
                    }
                    // remove the  redundant borders
                    Boundary newbox = TrimEmptyEdges(prevRegions[i]);
                    if (!prevRegions[i].Equals(newbox))
                    {
                        newRegions.Add(newbox);
                        cntSplit++;
                        continue;
                    }
                    //split the region
                    List<Boundary> splitBoxes = SplitBlockRegion(prevRegions[i]);
                    if (splitBoxes.Count == 2)
                    {
                        newRegions.Add(splitBoxes[0]);
                        newRegions.Add(splitBoxes[1]);
                        cntSplit++;
                        continue;
                    }
                    blockRegions.Add(prevRegions[i]);
                }
            }
            return blockRegions;
        }

        /// <summary>
        /// Remove none edges from the up, down, left, right directions
        /// </summary>
        private Boundary TrimEmptyEdges(Boundary box)
        {
            int up = box.top;
            int down = box.bottom;
            int left = box.left;
            int right = box.right;

            for (; up < down; ++up)
            {
                Boundary edgeBox = new Boundary(up, up, left, right);
                if (sumContent.SubmatrixSum(edgeBox) + sumColor.SubmatrixSum(edgeBox) + sumBorder.SubmatrixSum(edgeBox) != 0)
                    break;
            }
            for (; down >= up; --down)
            {
                Boundary edgeBox = new Boundary(down, down, left, right);
                if (sumContent.SubmatrixSum(edgeBox) + sumColor.SubmatrixSum(edgeBox) + sumBorder.SubmatrixSum(edgeBox) != 0)
                    break;
            }
            for (; left < right; ++left)
            {
                Boundary edgeBox = new Boundary(up, down, left, left);
                if (sumContent.SubmatrixSum(edgeBox) + sumColor.SubmatrixSum(edgeBox) + sumBorder.SubmatrixSum(edgeBox) != 0)
                    break;
            }
            for (; right >= left; --right)
            {
                Boundary edgeBox = new Boundary(up, down, right, right);
                if (sumContent.SubmatrixSum(edgeBox) + sumColor.SubmatrixSum(edgeBox) + sumBorder.SubmatrixSum(edgeBox) != 0)
                    break;
            }

            return new Boundary(up, down, left, right);
        }

        private List<Boundary> SplitBlockRegion(Boundary box)
        {
            int up = box.top;
            int down = box.bottom;
            int left = box.left;
            int right = box.right;

            // horizontal split
            for (int i = up + 4; i <= down - 4; i++)
            {
                // one row without format and  three continuous rows without contents
                Boundary edgeBox5 = new Boundary(i - 2, i + 2, left, right);
                Boundary edgeBox3 = new Boundary(i - 1, i + 1, left, right);
                Boundary edgeBox1 = new Boundary(i, i, left, right);

                if (sumContent.SubmatrixSum(edgeBox1) + sumColor.SubmatrixSum(edgeBox1) == 0
                    && (sumContentExist.SubmatrixSum(edgeBox3) == 0
                    || (sumContentExist.SubmatrixSum(edgeBox3) < 6 && sumContentExist.SubmatrixSum(edgeBox5) < 10))
                    || (ContentExistValueDensity(edgeBox5) < 0.1 && sumContentExist.SubmatrixSum(edgeBox5) < 10))
                {
                    //find out the first unnone line below
                    int k = i + 2;
                    Boundary edgeBoxDown = new Boundary(k, k, left, right);
                    while (k < down)
                    {
                        edgeBoxDown = new Boundary(k, k, left, right);
                        if (sumContentExist.SubmatrixSum(edgeBoxDown) > 2) break;
                        k++;
                    }
                    //find out the first unnone line above
                    k = i - 2;
                    Boundary edgeBoxUp = new Boundary(k, k, left, right);
                    while (k > up)
                    {
                        edgeBoxUp = new Boundary(k, k, left, right);
                        if (sumContentExist.SubmatrixSum(edgeBoxUp) > 2) break;
                        k--;
                    }
                    // if not strictly related, then split
                    if (down - edgeBoxDown.top > 1 && edgeBoxUp.top - up > 1)
                    {
                        return new List<Boundary> { new Boundary(up, i - 1, left, right), new Boundary(i + 1, down, left, right) };
                    }
                }
            }
            // vertical split
            for (int i = left + 4; i <= right - 4; i++)
            {
                Boundary edgeBox1 = new Boundary(up, down, i, i);
                Boundary edgeBox3 = new Boundary(up, down, i - 1, i + 1);
                Boundary edgeBox5 = new Boundary(up, down, i - 2, i + 2);

                if (sumContent.SubmatrixSum(edgeBox1) + sumColor.SubmatrixSum(edgeBox1) == 0
                    && (sumContentExist.SubmatrixSum(edgeBox3) == 0
                    || (sumContentExist.SubmatrixSum(edgeBox3) < 6 && sumContentExist.SubmatrixSum(edgeBox5) < 10))
                    || (ContentExistValueDensity(edgeBox5) < 0.1 && sumContentExist.SubmatrixSum(edgeBox5) < 10))
                {
                    int k = i + 2;
                    Boundary edgeBoxRight = new Boundary(up, down, k, k);
                    while (k <= right)
                    {
                        edgeBoxRight = new Boundary(up, down, k, k);
                        if (sumContentExist.SubmatrixSum(edgeBoxRight) > 2) break;
                        k++;
                    }

                    k = i - 2;
                    Boundary edgeBoxLeft = new Boundary(up, down, k, k);
                    while (k >= left)
                    {
                        edgeBoxLeft = new Boundary(up, down, k, k);
                        if (sumContentExist.SubmatrixSum(edgeBoxLeft) > 2) break;
                        k--;
                    }

                    if (right - edgeBoxRight.left > 1 && edgeBoxLeft.left - left > 1 && !verifyMutualMatchCol(edgeBoxRight, edgeBoxLeft))
                    {
                        return new List<Boundary> { new Boundary(up, down, left, i - 1), new Boundary(up, down, i + 1, right) };
                    }

                }
            }
            return new List<Boundary>();
        }
        #endregion

        #region  border cohension regions
        private void locateBorderCohensions(bool[,,] borderMap)
        {
            //search the regions filled with borders
            // to record which cells in the map has been already searched
            bool[,] borderMapMark = new bool[Height, Width];
            for (int i = 0; i < Height; i++)
            {
                for (int j = 0; j < Width; j++)
                {
                    borderMapMark[i, j] = true;
                }
            }

            for (int i = 0; i < Height; i++)
            {
                for (int j = 0; j < Width; j++)
                {
                    if (!borderMapMark[i, j]) continue;
                    int stepRow = 0;
                    int stepCol = 0;
                    //greedy search
                    while (i + stepRow < Height && j + stepCol < Width)
                    {
                        if (!borderMap[i + stepRow, j + stepCol, 0]) break;
                        if (!borderMap[i + stepRow, j + stepCol, 1]) break;
                        if (!borderMap[i + stepRow, j + stepCol, 2]) break;
                        if (!borderMap[i + stepRow, j + stepCol, 3]) break;
                        stepRow++;
                        stepCol++;
                    }
                    if (stepRow > 0) stepRow--;
                    if (stepCol > 0) stepCol--;
                    while (i + stepRow < Height)
                    {
                        if (!borderMap[i + stepRow, j + stepCol, 0]) break;
                        if (!borderMap[i + stepRow, j + stepCol, 1]) break;
                        if (!borderMap[i + stepRow, j + stepCol, 2]) break;
                        if (!borderMap[i + stepRow, j + stepCol, 3]) break;
                        stepRow++;
                    }
                    if (stepRow > 0) stepRow--;
                    while (j + stepCol < Width)
                    {
                        if (!borderMap[i + stepRow, j + stepCol, 0]) break;
                        if (!borderMap[i + stepRow, j + stepCol, 1]) break;
                        if (!borderMap[i + stepRow, j + stepCol, 2]) break;
                        if (!borderMap[i + stepRow, j + stepCol, 3]) break;
                        stepCol++;
                    }
                    if (stepCol > 0) stepCol--;

                    if (stepRow == 0 && stepCol == 0) continue;

                    // get the region
                    Boundary forcedBorderRegion = new Boundary(i + 1, i + 1 + stepRow, j + 1, j + 1 + stepCol);

                    // record this region in the borderMapMark
                    for (int p = 0; p <= stepRow; p++)
                    {
                        for (int q = 0; q <= stepCol; q++)
                        {
                            borderMapMark[i + p, j + q] = false;
                        }
                    }

                    if (CntNoneBorder(forcedBorderRegion) >= 2)
                    {
                        continue;
                    }

                    // verify the region, and add it to forcedBorderRegions
                    if (VerifyBorderRegion(forcedBorderRegion))
                    {
                        if (stepRow >= 3 && stepCol >= 2)
                        {
                            cohensionBorderRegions.Add(forcedBorderRegion);
                        }
                        else if (stepRow >= 2 || stepCol >= 2)
                        {
                            smallCohensionBorderRegions.Add(forcedBorderRegion);
                        }
                    }
                }
            }
        }

        private bool VerifyBorderRegion(Boundary borderRegion)
        {
            int up = borderRegion.top - 1;
            int down = borderRegion.bottom - 1;
            int left = borderRegion.left - 1;
            int right = borderRegion.right - 1;

            // +2 avoid header
            for (int col = left + 2; col <= right; col++)
            {////featuremap bottom in, same bottom border form left to right
                if (col > left && featureMap[down, col].HasBottomBorder != featureMap[down, col - 1].HasBottomBorder)
                {
                    return false;
                }
            }

            for (int row = up + 2; row <= down; row++)
            {////featuremap right in, need have same right border form up to down
                if (row > up && featureMap[row, right].HasRightBorder != featureMap[row - 1, right].HasRightBorder)
                {
                    return false;
                }
            }

            for (int col = left; col <= right; col++)
            {////featuremap bottom out, dont have left border
                if (col > left && featureMap[down + 1, col].HasLeftBorder)
                {
                    return false;
                }
            }

            for (int col = left; col <= right; col++)
            {////featuremap up out, dont have left border
                if (col > left && featureMap[up - 1, col].HasLeftBorder)
                {
                    return false;
                }
            }

            for (int row = up; row <= down; row++)
            {////featuremap right out, dont have up border
                if (row > up && featureMap[row, right + 1].HasTopBorder)
                {
                    return false;
                }
            }

            for (int row = up; row <= down; row++)
            {////featuremap left out, dont have up border
                if (row > up && featureMap[row, left - 1].HasTopBorder)
                {
                    return false;
                }
            }

            return true;
        }
        #endregion

        #region deal with border,merge,color,semantic row/col
        private void dealWithBorderCohensionRegions()
        {
            // preprocess the border maps
            // the subscript indexes, 0: bottom border, 1: top border, 2: left border, 3: right border
            bool[,,] borderMap = new bool[Height, Width, 4];

            // initialize and preprocess the border map
            for (int i = 0; i < Height; i++)
            {
                for (int j = 0; j < Width; j++)
                {
                    borderMap[i, j, 0] = featureMap[i, j].HasBottomBorder;
                    borderMap[i, j, 1] = featureMap[i, j].HasTopBorder;
                    borderMap[i, j, 2] = featureMap[i, j].HasLeftBorder;
                    borderMap[i, j, 3] = featureMap[i, j].HasRightBorder;

                    // the left border for the right cell is same as the right border for the left cell
                    if (borderMap[i, j, 0] && i - 1 >= 0)
                    {
                        borderMap[i - 1, j, 1] = true;
                    }
                    if (borderMap[i, j, 1] && i + 1 < Height)
                    {
                        borderMap[i + 1, j, 0] = true;
                    }
                    if (borderMap[i, j, 2] && j - 1 >= 0)
                    {
                        borderMap[i, j - 1, 3] = true;
                    }
                    if (borderMap[i, j, 3] && j + 1 < Width)
                    {
                        borderMap[i, j + 1, 2] = true;
                    }
                }
            }

            // in order to search for border regions conveniently, first fill the border map in some degree
            //for example, if a cell have up and right borders, then we regard it as a bordered cell, and add the bottom and left borders for it.

            // extend borders from upleft to rightdown
            #region four directions
            for (int i = 0; i < Height; i++)
            {
                for (int j = 0; j < Width; j++)
                {
                    if (borderMap[i, j, 1] && borderMap[i, j, 2])
                    {
                        borderMap[i, j, 0] = true;
                        borderMap[i, j, 3] = true;
                        if (j < Width) borderMap[i, j + 1, 2] = true;
                        if (i < Height) borderMap[i + 1, j, 1] = true;
                    }
                }
            }

            // extend borders from upright to downleft
            for (int i = 0; i < Height; i++)
            {
                for (int j = Width - 1; j >= 0; j--)
                {
                    if (borderMap[i, j, 1] && borderMap[i, j, 3])
                    {
                        borderMap[i, j, 0] = true;
                        borderMap[i, j, 2] = true;
                        if (j - 1 >= 0) borderMap[i, j - 1, 3] = true;
                        if (i < Height) borderMap[i + 1, j, 1] = true;
                    }
                }
            }

            // extend borders from downleft to upright
            for (int i = Height - 1; i >= 0; i--)
            {
                for (int j = 0; j < Width; j++)
                {
                    if (borderMap[i, j, 0] == true && borderMap[i, j, 2] == true)
                    {
                        borderMap[i, j, 1] = true;
                        borderMap[i, j, 3] = true;
                        if (j < Width) borderMap[i, j + 1, 2] = true;
                        if (i - 1 >= 0) borderMap[i - 1, j, 0] = false;
                    }
                }
            }
            // extend borders from downright to upleft
            for (int i = Height - 1; i >= 0; i--)
            {
                for (int j = Width - 1; j >= 0; j--)
                {
                    if (borderMap[i, j, 0] && borderMap[i, j, 3])
                    {
                        borderMap[i, j, 1] = true;
                        borderMap[i, j, 2] = true;
                        if (j - 1 >= 0) borderMap[i, j - 1, 3] = true;
                        if (i - 1 >= 0) borderMap[i - 1, j, 0] = true;
                    }
                }
            }
            #endregion
            locateBorderCohensions(borderMap);
        }

        private List<Boundary> generateColorCohensions(double colorHeightThresh = 5, double colorWidthThresh = 3, double nullFeatureAreaThresh = 2)
        {
            // search for the colored squared regions in the sheet
            List<Boundary> colorRegions = new List<Boundary>();
            bool[,] colorMapMark = new bool[Height, Width];

            for (int i = 0; i < Height; i++)
            {
                for (int j = 0; j < Width; j++)
                {
                    // index of featuremap
                    colorMapMark[i, j] = featureMap[i, j].HasFillColor;
                }
            }

            for (int i = 0; i < Height; i++)
            {
                for (int j = 0; j < Width; j++)
                {
                    if (!colorMapMark[i, j]) continue;
                    // find the neighboring colored region (right and down dirctions)
                    int stepRow = 0;
                    int stepCol = 0;
                    // search for the retion in the direction of diagonal first
                    while (i + stepRow < Height && j + stepCol < Width)
                    {
                        if (!colorMapMark[i + stepRow, j + stepCol]) break;
                        stepRow++;
                        stepCol++;
                    }
                    if (stepRow > 0) stepRow--;
                    if (stepCol > 0) stepCol--;
                    while (i + stepRow < Height)
                    {
                        if (!colorMapMark[i + stepRow, j + stepCol]) break;
                        stepRow++;
                    }
                    if (stepRow > 0) stepRow--;
                    while (j + stepCol < Width)
                    {
                        if (!colorMapMark[i + stepRow, j + stepCol]) break;
                        stepCol++;
                    }
                    if (stepCol > 0) stepCol--;
                    //uncolored this colored region
                    for (int p = 0; p <= stepRow; p++)
                    {
                        for (int q = 0; q <= stepCol; q++)
                        {
                            colorMapMark[i + p, j + q] = false;
                        }
                    }

                    if (stepRow >= colorHeightThresh || stepCol >= colorWidthThresh)
                    {
                        Boundary forcedColorRegion = new Boundary(i + 1, i + 1 + stepRow, j + 1, j + 1 + stepCol);
                        colorRegions.Add(forcedColorRegion);
                    }
                }

            }
            return colorRegions;
        }

        private List<Boundary> ConvertMergedRanges(List<Boundary> mergedAreas)
        {
            return mergedAreas.Select(mergedArea => new Boundary(
                mergedArea.top + rowOffset + 1,
                mergedArea.bottom + rowOffset + 1,
                mergedArea.left + colOffset + 1,
                mergedArea.right + colOffset + 1)).ToList();
        }

        private List<Boundary> GenerateMergedCohensions(ref List<int> rowLines, ref List<int> colLines)
        {
            List<Boundary> mergeBoxesAll = new List<Boundary>();
            mergeBoxesAll.AddRange(mergeBoxes);

            // unify the overlapping and neighboring boxes
            ////List<Boundary> unifiedRanges = Utils.GetUnifiedRanges(mergeBoxes);
            ////mergeBoxesAll.AddRange(unifiedRanges);

            return mergeBoxesAll;
        }
        #endregion


        #region compute value maps, border, content, color, diff
        private void computeValueDiff()
        {
            //the differece for the two adjacent cells in the row and column directions
            rowDiffs = new List<List<double>>();
            colDiffs = new List<List<double>>();

            // whether is different for the two adjacent cells in the row and column directions
            rowDiffsBool = new List<List<bool>>();
            colDiffsBool = new List<List<bool>>();

            //initialize rowDiffBool and rowDiffs
            for (int i = 0; i < Height - 1; i++)
            {
                List<bool> rowDiffBool = new List<bool>();
                List<double> rowDiff = new List<double>();
                for (int k = 0; k < Width; k++)
                {
                    rowDiffBool.Add(false);
                    rowDiff.Add(0);
                }
                rowDiffsBool.Add(rowDiffBool);
                rowDiffs.Add(rowDiff);
            }

            //initialize colDiffBool and colDiffs
            for (int i = 0; i < Width - 1; i++)
            {
                List<bool> colDiffBool = new List<bool>();
                List<double> colDiff = new List<double>();
                for (int k = 0; k < Height; k++)
                {
                    colDiffBool.Add(false);
                    colDiff.Add(0);
                }
                colDiffsBool.Add(colDiffBool);
                colDiffs.Add(colDiff);
            }

            //  calculate rowDiffBool and rowDiffs, colDiffBool and colDiffs
            for (int i = 0; i < Height - 1; i++)
            {
                for (int j = 0; j < Width - 1; j++)
                {

                    // calculate rowDiffBool and rowDiffs
                    if (i > 0)
                    {
                        // up down windows
                        Boundary boxDown = new Boundary(i + 1, i + 1, 1, Width - 1);
                        Boundary boxUp = new Boundary(i, i, 1, Width - 1);
                        // sum up values in the up and down windows

                        int sumDownValue = sumAll.SubmatrixSum(boxDown);
                        int sumUpValue = sumAll.SubmatrixSum(boxUp);

                        int sumDownContentExsit = sumContentExist.SubmatrixSum(boxDown);
                        int sumUpContentExsit = sumContentExist.SubmatrixSum(boxUp);

                        if (sumUpValue == sumDownValue)
                        {
                            rowDiffsBool[i - 1][j] = false;
                        }
                        //  Whether or not exist content
                        else if (Utils.VerifyValuesOpposite(valueMapContent[i, j], valueMapContent[i - 1, j]) && Utils.VerifyValuesOpposite(sumDownContentExsit, sumUpContentExsit))
                        {
                            rowDiffsBool[i - 1][j] = true;
                        }
                        // content ununiform 
                        else if (Utils.VerifyValuesDiff(sumDownContentExsit, sumUpContentExsit, 5))
                        {
                            rowDiffsBool[i - 1][j] = true;
                        }
                        //color difference
                        else if (Utils.VerifyValuesOpposite(valueMapColor[i, j], valueMapColor[i - 1, j]))
                        {
                            rowDiffsBool[i - 1][j] = true;
                        }
                        //border difference
                        else if (featureMap[i, j].HasTopBorder && (!featureMap[i + 1, j].HasTopBorder || !featureMap[i - 1, j].HasTopBorder))
                        {
                            rowDiffsBool[i - 1][j] = true;
                        }
                        else
                        {
                            rowDiffsBool[i - 1][j] = false;
                        }
                        // calculate rowDiffs directly use sumDownValue and sumUpValue
                        rowDiffs[i - 1][j] = sumDownValue / 5 - sumUpValue / 5;

                    }
                    // calculate colDiffBool and colDiffs
                    if (j > 0)
                    {
                        Boundary boxRight = new Boundary(1, Height - 1, j + 1, j + 1);
                        Boundary boxLeft = new Boundary(1, Height - 1, j, j);
                        int sumRightValue = sumAll.SubmatrixSum(boxRight);
                        int sumLeftValue = sumAll.SubmatrixSum(boxLeft);
                        int sumRightContentExsit = sumContentExist.SubmatrixSum(boxRight);
                        int sumLeftContentExsit = sumContentExist.SubmatrixSum(boxLeft);
                        if (sumRightValue == sumLeftValue)
                        {
                            colDiffsBool[j - 1][i] = false;
                        }
                        else if (Utils.VerifyValuesOpposite(valueMapContent[i, j], valueMapContent[i, j - 1]) && Utils.VerifyValuesOpposite(sumLeftContentExsit, sumRightContentExsit))
                        {
                            colDiffsBool[j - 1][i] = true;
                        }
                        else if (Utils.VerifyValuesDiff(sumLeftContentExsit, sumRightContentExsit, 5))
                        {
                            colDiffsBool[j - 1][i] = true;
                        }
                        else if (Utils.VerifyValuesOpposite(valueMapColor[i, j], valueMapColor[i, j - 1]))
                        {
                            colDiffsBool[j - 1][i] = true;
                        }
                        else if (featureMap[i, j].HasLeftBorder && (!featureMap[i, j + 1].HasLeftBorder || !featureMap[i, j - 1].HasLeftBorder))
                        {
                            rowDiffsBool[i - 1][j] = true;
                        }
                        else
                        {
                            colDiffsBool[j - 1][i] = false;
                        }
                        colDiffs[j - 1][i] = sumRightValue / 5 - sumLeftValue / 5;
                    }

                }

            }
        }

        private int[,] ComputeValueMap(Func<CellFeatures, int> calcFunc)
            => ComputeValueMap((i, j) => calcFunc(featureMap[i, j]));

        private int[,] ComputeValueMap(Func<int, int, int> calcFunc)
        {
            valueMapAll = new int[Height, Width];
            for (int i = 0; i < Height; i++)
                for (int j = 0; j < Width; j++)
                    valueMapAll[i, j] = calcFunc(i, j);

            return valueMapAll;
        }
        #endregion

        #region value range sums

        public static int ValueSumRange(List<Boundary> boxes, int[,] valueMapSum)
        {
            // the overall area of boxes with overlaps
            int result = 0;
            List<Boundary> boxesCalculated = new List<Boundary>();
            foreach (var box in boxes)
            {
                // if one box is contained by another, then just skip this one
                if (Utils.ContainsBox(boxes, box)) continue;

                result += valueMapSum.SubmatrixSum(box);
                // subtract the overlaps by more than two boxes
                foreach (var boxIn in boxesCalculated)
                {
                    if (Utils.isOverlap(boxIn, box) && !boxIn.Equals(box))
                    {
                        Boundary boxOverlaped = Utils.OverlapBox(boxIn, box);
                        result -= valueMapSum.SubmatrixSum(boxOverlaped);
                    }
                }
                // subtract the overlaps by more than three boxes
                foreach (var boxIn1 in boxesCalculated)
                {
                    foreach (var boxIn2 in boxesCalculated)
                    {
                        if (Utils.isOverlap(boxIn1, box) && Utils.isOverlap(boxIn2, box) && Utils.isOverlap(boxIn2, boxIn1) && !boxIn2.Equals(boxIn1) && !boxIn1.Equals(box) && !boxIn2.Equals(box))
                        {
                            Boundary boxOverlaped = Utils.OverlapBox(boxIn1, boxIn2, box);
                            result += valueMapSum.SubmatrixSum(boxOverlaped);
                        }
                    }
                }
                // Omitted the more complicated cases for simiplicity, for example, subtract the overlaps by more than four boxes

                boxesCalculated.Add(box);
            }
            return result;
        }

        public double ContentExistValueDensity(Boundary box)
        {
            if (box.bottom < box.top || box.right < box.left) return 0;
            return sumContentExist.SubmatrixSum(box) / Utils.AreaSize(box);
        }

        public double RowContentExistValueDensitySplit(Boundary box, double split = 4)
        {
            if (box.bottom < box.top || box.right < box.left) return 0;
            double stride = (box.right - box.left + 1) / split;
            double cntExists = 0;
            for (int i = 0; i < split; i++)
            {
                if (sumContentExist.SubmatrixSum(new Boundary(box.top, box.bottom, Convert.ToInt32(box.left + i * stride), Convert.ToInt32(box.left + stride * (i + 1)) - 1)) > 0)
                {
                    cntExists++;
                }
            }
            return cntExists / split;
        }

        public double ColContentExistValueDensitySplit(Boundary box, double split = 4)
        {
            if (box.bottom < box.top || box.right < box.left) return 0;
            double stride = (box.bottom - box.top + 1) / split;
            double cntExists = 0;
            for (int i = 0; i < split; i++)
            {
                if (sumContentExist.SubmatrixSum(new Boundary(Convert.ToInt32(box.top + i * stride), Convert.ToInt32(box.top + stride * (i + 1)) - 1, box.left, box.right)) > 0)
                {
                    cntExists++;
                }
            }
            return cntExists / split;
        }
        #endregion

        #region row/col relation computation
        public bool verifyMutualMatchCol(Boundary box1, Boundary box2)
        {
            int up = box1.top;
            int down = box1.bottom;
            for (int i = up; i <= down; i++)
            {
                if (Math.Min(2, valueMapContent[i - 1, box1.left - 1]) != Math.Min(2, valueMapContent[i - 1, box2.left - 1]))
                {
                    return false;
                }
            }
            return true;
        }

        public double ComputeSimilarRow(Boundary box1, Boundary box2)
        {
            List<double> similars = new List<double>();

            for (int i = box1.top; i <= box1.bottom; i++)
            {
                for (int j = box2.top; j <= box2.bottom; j++)
                {
                    int left = box1.left;
                    int right = box1.right;
                    Boundary window1 = new Boundary(i, i, left, left);
                    Boundary window2 = new Boundary(j, j, left, left);
                    while (sumContentExist.SubmatrixSum(window1) == 0 && sumContentExist.SubmatrixSum(window2) == 0 && left <= right)
                    {
                        left++;
                        window1 = new Boundary(i, i, left, left);
                        window2 = new Boundary(j, j, left, left);
                    }
                    window1 = new Boundary(i, i, right, right);
                    window2 = new Boundary(j, j, right, right);
                    while (sumContentExist.SubmatrixSum(window1) == 0 && sumContentExist.SubmatrixSum(window2) == 0 && left <= right)
                    {
                        right--;
                        window1 = new Boundary(i, i, right, right);
                        window2 = new Boundary(j, j, right, right);
                    }
                    window1 = new Boundary(i, i, left, right);
                    window2 = new Boundary(j, j, left, right);

                    if (window1.Equals(window2))
                    {
                        continue;
                    }
                    List<double> list1_1 = convertFeatureToList(window1, feature => feature.AlphabetRatio);
                    List<double> list2_1 = convertFeatureToList(window2, feature => feature.AlphabetRatio);
                    List<double> list1_2 = convertFeatureToList(window1, feature => feature.NumberRatio);
                    List<double> list2_2 = convertFeatureToList(window2, feature => feature.NumberRatio);

                    if (sumContentExist.SubmatrixSum(window1) <= 2 || sumContentExist.SubmatrixSum(window1) / list1_1.Count <= 0.3
                        || sumContentExist.SubmatrixSum(window2) <= 2 || sumContentExist.SubmatrixSum(window2) / list1_2.Count <= 0.3)
                    {
                        continue;
                    }

                    similars.Add(Utils.r1(list1_1, list2_1));
                    similars.Add(Utils.r1(list1_2, list2_2));
                }
            }
            if (similars.Count == 0)
                return 1;
            return Utils.average(similars);
        }

        private List<double> convertFeatureToList(Boundary box, Func<CellFeatures, double> featureFunc)
        {
            List<double> ListedArr = new List<double>();
            for (int i = box.top - 1; i <= box.bottom - 1; i++)
            {
                for (int j = box.left - 1; j <= box.right - 1; j++)
                {
                    ListedArr.Add(featureFunc(featureMap[i, j]));
                }
            }
            return ListedArr;
        }

        #endregion

        #region formula analysis
        private List<Boundary>[,] preProcessFormulas(string[,] formus)
        {
            int height = formus.GetLength(0);
            int width = formus.GetLength(1);

            var formulaReferenceRanges = new List<Boundary>[height, width];

            // Regular expressions for date and lookup
            Regex RegLookup = new Regex("LOOKUP", RegexOptions.Compiled);
            Regex RegDate = new Regex("DATE", RegexOptions.Compiled);

            // Traverse all the formulas in the sheet
            for (int row = 0; row < height; row++)
            {
                // rowReferRanges,  one reference ranges for each cell in a row
                List<List<Boundary>> rowReferRanges = new List<List<Boundary>>();

                for (int col = 0; col < width; col++)
                {
                    List<Boundary> ranges = new List<Boundary>();
                    // the first kind of formula, like "SUM(A10:B13)", but not "R1C10"
                    if (formus[row, col] != null && formus[row, col] != "")
                    {
                        string formula = formus[row, col];
                        // if formula is long, just cut it for simplility
                        if (formula.Length > 50) formula = formula.Substring(0, 50);
                        // when  formula is long, then it seems hard to analysis correctly, "=SUMIF(mSRPV!$A:$A,$C12,mSRPV!J:J)"
                        // when  formula contains dates, then it may be important and should not be missed, "=DATE(YEAR(L10),MONTH(L10)+1,1)"
                        // when formula contains lookup, then need be careful, because the result region often refer to the wrong places.
                        MatchCollection mcsDate = RegDate.Matches(formula);
                        MatchCollection mcsLookup = RegLookup.Matches(formula);
                        if (mcsDate.Count != 0 || (mcsLookup.Count == 0 && formula.Length <= 18))
                        {
                            try
                            {
                                ranges = formulaAnalysis(formula);
                            }
                            catch
                            {
                            }
                        }
                        else
                        {
                            // refer to a empty cell as default 
                            ranges = new List<Boundary>();
                            ranges.Add(new Boundary(1, 1, 1, 1));
                        }

                    }

                    formulaReferenceRanges[row, col] = ranges;
                }
            }

            return formulaReferenceRanges;
        }

        private Boundary formulaRegionAnalysis(string regionStr)
        {   // Regular expressions
            Regex RegRow = new Regex("\\d+");
            Regex RegCol = new Regex("[A-Z]+");
            Boundary box = new Boundary(0, 0, 0, 0);

            MatchCollection mcRow = RegRow.Matches(regionStr);
            MatchCollection mcCol = RegCol.Matches(regionStr);

            if (mcRow.Count == 2 && mcCol.Count == 2)
            {
                string rowStr = mcRow[0].ToString();
                string colStr = mcCol[0].ToString();
                box.top = Convert.ToInt32(rowStr);
                box.left = colStr.Select(t => t - 'A' + 1).Aggregate(0, (current, temp) => temp + current * 26);

                rowStr = mcRow[1].ToString();
                colStr = mcCol[1].ToString();
                box.bottom = Convert.ToInt32(rowStr);
                box.right = colStr.Select(t => t - 'A' + 1).Aggregate(0, (current, temp) => temp + current * 26);

            }

            else if (mcCol.Count == 2 && mcRow.Count == 0)
            {
                box = new Boundary(1, Height, 0, 0);
                string colStr = mcCol[0].ToString();
                box.left = colStr.Select(t => t - 'A' + 1).Aggregate(0, (current, temp) => temp + current * 26);

                colStr = mcCol[1].ToString();
                box.right = colStr.Select(t => t - 'A' + 1).Aggregate(0, (current, temp) => temp + current * 26);

            }
            else if (mcRow.Count == 2 && mcCol.Count == 0)
            {
                box = new Boundary(0, 0, 1, Width);

                string rowStr = mcRow[0].ToString();
                box.top = Convert.ToInt32(rowStr);
                rowStr = mcRow[1].ToString();
                box.bottom = Convert.ToInt32(rowStr);
            }
            else if (mcRow.Count == 1 && mcCol.Count == 1)
            {
                box = new Boundary(0, 0, 0, 0);
                string rowStr = mcRow[0].ToString();
                string colStr = mcCol[0].ToString();
                box.top = Convert.ToInt32(rowStr);
                box.left = colStr.Select(t => t - 'A' + 1).Aggregate(0, (current, temp) => temp + current * 26);
                box.bottom = box.top;
                box.right = box.left;
            }
            else
            {
                return box;
            }

            box.top = Math.Max(box.top + rowOffset, 1 + rowOffset);
            box.bottom = Math.Min(box.bottom + rowOffset, Height - rowOffset);
            box.left = Math.Max(box.left + colOffset, 1 + colOffset);
            box.right = Math.Min(box.right + colOffset, Width - colOffset);

            return box;
        }
        private List<Boundary> formulaAnalysis(string formula)
        {
            // Regular expressions
            Regex RegRegion = new Regex("\\$?[A-Z]+\\$?\\d+:\\$?[A-Z]+\\$?\\d+");
            Regex RegCols = new Regex("\\$?[A-Z]+:\\$?[A-Z]+");
            Regex RegRows = new Regex("\\$?\\d+:\\$?\\d+");
            Regex RegCell = new Regex("\\$?[A-Z]+\\$?\\d+");

            List<Boundary> ranges = new List<Boundary>();

            Dictionary<string, bool> records = new Dictionary<string, bool>();

            // first analyze region,  then col/row, cell last

            //region level reference
            foreach (var mc in RegRegion.Matches(formula))
            {
                string regionStr = mc.ToString();
                if (records.ContainsKey(regionStr)) continue;
                else records[regionStr] = true;

                Boundary box = formulaRegionAnalysis(regionStr);

                if (box.top <= box.bottom && box.left <= box.right && box.top * box.bottom * box.left * box.right != 0)
                {
                    ranges.Add(box);
                }
            }
            if (ranges.Count > 0)
            {
                return ranges;
            }

            // col  level reference
            foreach (var mc in RegCols.Matches(formula))
            {
                string regionStr = mc.ToString();
                if (records.ContainsKey(regionStr)) continue;
                else records[regionStr] = true;

                Boundary box = formulaRegionAnalysis(regionStr);

                if (box.top <= box.bottom && box.left <= box.right && box.top * box.bottom * box.left * box.right != 0)
                {
                    ranges.Add(box);
                }
            }

            // row  level reference
            foreach (var mc in RegRows.Matches(formula))
            {
                string regionStr = mc.ToString();
                if (records.ContainsKey(regionStr)) continue;
                else records[regionStr] = true;

                Boundary box = formulaRegionAnalysis(regionStr);

                if (box.top <= box.bottom && box.left <= box.right && box.top * box.bottom * box.left * box.right != 0)
                {
                    ranges.Add(box);
                }
            }
            if (ranges.Count > 0)
            {
                return ranges;
            }

            // cell level reference
            foreach (var mc in RegCell.Matches(formula))
            {
                string regionStr = mc.ToString();
                if (records.ContainsKey(regionStr)) continue;
                else records[regionStr] = true;

                Boundary box = formulaRegionAnalysis(regionStr);

                if (box.top <= box.bottom && box.left <= box.right && box.top * box.bottom * box.left * box.right != 0)
                {
                    ranges.Add(box);
                }
            }

            return ranges;
        }

        private void DealFormulaRanges(List<Boundary> referRanges, int cellHeight, int cellWidth, int recurseDepth, int recurseMaxDepth = 10)
        {
            // update feature and content regarding to formulas, with recursive method
            int i = cellHeight - 1;
            int j = cellWidth - 1;
            if (contentStrs[i, j] != "" && contentStrs[i, j] != CellFeatures.DefaultContentForFomula)
            {
                return;
            }
            contentStrs[i, j] = contentStrs[i, j] + "_index_" + i.ToString() + "_" + j.ToString();
            bool markFindout = false;
            // tranverse all the reference ranges
            foreach (var range in referRanges)
            {
                // if already find out the target feature and content, then break
                if (markFindout) break;
                // tranverse all the cells in this range
                for (int row = range.top; row <= range.bottom; row++)
                {
                    if (markFindout) break;
                    for (int col = range.left; col <= range.right; col++)
                    {
                        #region update the feature and content for cells in the recursive path
                        if (row < 1 || row > Height || col < 1 || col > Width) continue;
                        // if text for the reference cell is not none
                        if (!featureMap[row - 1, col - 1].MarkText) continue;
                        if (recurseDepth < recurseMaxDepth && formulaRanges[row - 1, col - 1].Count > 0
                            && (contentStrs[row - 1, col - 1] == CellFeatures.DefaultContentForFomula || @Regex.Split(contentStrs[row - 1, col - 1], "_index_")[0] == CellFeatures.DefaultContentForFomula))
                        {
                            DealFormulaRanges(formulaRanges[row - 1, col - 1], row, col, recurseDepth + 1);
                        }

                        featureMap[i, j].TextLength = featureMap[row - 1, col - 1].TextLength;
                        featureMap[i, j].AlphabetRatio = featureMap[row - 1, col - 1].AlphabetRatio;
                        featureMap[i, j].NumberRatio = featureMap[row - 1, col - 1].NumberRatio;
                        featureMap[i, j].SpCharRatio = featureMap[row - 1, col - 1].SpCharRatio;

                        //add suffix for content text, it can avoid dead loop, and it can make sure variaty for cell values.
                        contentStrs[i, j] = contentStrs[i, j] + "_retrieved";

                        //if find a valid text, then end the tranverse
                        if (@Regex.Split(contentStrs[row - 1, col - 1], "_index_")[0] != ""
                            && @Regex.Split(contentStrs[row - 1, col - 1], "_index_")[0] != CellFeatures.DefaultContentForFomula)
                        {
                            markFindout = true;
                            break;
                        }
                        #endregion
                    }
                }
            }
        }

        private void UpdateFeatAndContentsByFormula()
        {
            // update the feature map for the sheet by the formulas 

            Random rand = new Random(DateTime.Now.Millisecond);

            for (int row = rowOffset; row < Height - rowOffset; row++)
            {
                for (int col = colOffset; col < Width - colOffset; col++)
                {
                    List<Boundary> ranges = formulaRanges[row, col];
                    if (ranges.Count > 0)
                    {
                        DealFormulaRanges(ranges, row + 1, col + 1, 0);
                    }
                }
            }

            for (int row = rowOffset; row < Height - rowOffset; row++)
            {
                for (int col = colOffset; col < Width - colOffset; col++)
                {
                    if (contentStrs[row, col] == CellFeatures.DefaultContentForFomula)
                    {
                        contentStrs[row, col] = contentStrs[row, col] + "_index_" + row.ToString() + "_" + col.ToString();
                    }
                }
            }
        }
        #endregion

        #region findout headers 
        public List<Boundary> FindoutUpheaders(TableDetectionHybrid detector, List<Boundary> regions)
        {
            List<Boundary> upHeaders = new List<Boundary>();
            for (int i = 0; i < regions.Count; i++)
            {
                Boundary box1 = regions[i];
                for (int k = 0; k < 5; k++)
                {
                    Boundary upHeaderCandidate = Utils.UpRow(box1, start: k);
                    if ((sumContentExist.SubmatrixSum(upHeaderCandidate) >= 6
                        && TextDistinctCount(upHeaderCandidate) > 1
                        && ContentExistValueDensity(upHeaderCandidate) >= 2 * 0.5)
                        || (sumContentExist.SubmatrixSum(upHeaderCandidate) >= 4
                        && TextDistinctCount(upHeaderCandidate) > 1
                        && ContentExistValueDensity(upHeaderCandidate) >= 2 * 1))
                    {
                        if (detector.IsHeaderUp(upHeaderCandidate) && ContentExistValueDensity(upHeaderCandidate) > 2 * 0.7)
                        {
                            upHeaders.Add(upHeaderCandidate);
                        }
                        break;
                    }
                }

            }
            return Utils.DistinctBoxes(upHeaders);
        }

        public List<Boundary> FindoutLeftheaders(TableDetectionHybrid detector, List<Boundary> regions)
        {
            List<Boundary> leftHeaders = new List<Boundary>();
            for (int i = 0; i < regions.Count; i++)
            {
                Boundary box1 = regions[i];
                for (int k = 0; k < 5; k++)
                {
                    Boundary leftHeaderCandidate = Utils.LeftCol(box1, start: k);
                    if (sumContentExist.SubmatrixSum(leftHeaderCandidate) >= 6 && TextDistinctCount(leftHeaderCandidate) > 1
                        && ContentExistValueDensity(leftHeaderCandidate) >= 2 * 0.5)
                    {
                        if (detector.IsHeaderLeft(leftHeaderCandidate))
                        {
                            leftHeaders.Add(leftHeaderCandidate);
                        }
                        break;
                    }
                }

            }
            return Utils.DistinctBoxes(leftHeaders);
        }
        #endregion
        public bool ExistsMerged(Boundary box)
        {
            return mergeBoxes.Any(range => Utils.isOverlap(range, box));
        }

        public int TextDistinctCount(Boundary box)
        {
            var hashSet = new HashSet<string>();
            for (int i = box.top; i <= box.bottom; i++)
            {
                for (int j = box.left; j <= box.right; j++)
                {
                    if (featureMap[i - 1, j - 1].MarkText)
                    {
                        hashSet.Add(contentStrs[i - 1, j - 1]);
                    }
                }
            }

            return hashSet.Count;
        }
        private int CntNoneBorder(Boundary box)
        {
            Boundary boxUp = new Boundary(box.top, box.top, box.left, box.right);
            Boundary boxDown = new Boundary(box.bottom, box.bottom, box.left, box.right);
            Boundary boxLeft = new Boundary(box.top, box.bottom, box.left, box.left);
            Boundary boxRight = new Boundary(box.top, box.bottom, box.right, box.right);
            return new[] { boxUp, boxDown, boxLeft, boxRight }
                .Count(box1 => sumContent.SubmatrixSum(box1) == 0 && sumColor.SubmatrixSum(box1) == 0);
        }
    }
}
```

## File: code/TableDetectionHybrid.cs
```csharp
using System.Linq;
using System.Collections.Generic;
using System.Diagnostics;

namespace SpreadsheetLLM.Heuristic
{
    internal partial class TableDetectionHybrid
    {
        private SheetMap _sheet;
        private List<Boundary> _regionGrowthBoxes;

        /// <summary>
        /// Candidate boxes field that is shared across all implementations of <see cref="TableDetectionHybrid"/> class.
        /// </summary>
        private List<Boundary> _boxes = new List<Boundary>();

        private IList<string> _logs;

        public TableDetectionHybrid(IList<string> logs)
        {
            _logs = logs;
        }

        public List<Boundary> Detect(SheetMap inputSheet, bool eliminateOverlaps)
        {
            var tableSenseDetectStopWatch = Stopwatch.StartNew();
            _sheet = inputSheet;
            //if file too big
            if (_sheet.Height > 1000 || _sheet.Height * _sheet.Width > 30000)
            {
                _logs.Add("Input too large - fall back to RegionGrowthDetect()");
                //simple region growth method
                RegionGrowthDetect();
                _logs.Add($"RegionGrowthDetect() ElapsedTime: {tableSenseDetectStopWatch.ElapsedMilliseconds}");
                tableSenseDetectStopWatch.Restart();
            }
            else
            {
                _logs.Add("Run TableSenseDetect()");
                TableSenseDetect();
                _logs.Add($"TableSenseDetect() ElapsedTime: {tableSenseDetectStopWatch.ElapsedMilliseconds}");
                tableSenseDetectStopWatch.Restart();

            }

            if (eliminateOverlaps)
            {
                _logs.Add($"Eliminating overlaps in {_boxes.Count} boxes");
                _boxes = Utils.RankBoxesBySize(_boxes);
                EliminateOverlaps();
                _logs.Add($"EliminateOverlaps. ElapsedTime: {tableSenseDetectStopWatch.ElapsedMilliseconds}");
                tableSenseDetectStopWatch.Restart();
            }

            _logs.Add($"Ranking {_boxes.Count} boxes");
            _boxes = Utils.RankBoxesByLocation(_boxes);

            _logs.Add($"Returning {_boxes.Count} boxes");
            tableSenseDetectStopWatch.Stop();
            return _boxes;
        }

        private void RegionGrowthDetect(int threshHor = 1, int threshVer = 1)
        {
            // main function for region growth method
            _boxes = RegionGrowthDetector.FindConnectedRanges(_sheet.contentStrs, _sheet.valueMapBorder, threshHor, threshVer);

            _logs.Add($"Found {_boxes.Count} connected ranges");

            // filter the little and sparse boxes
            LittleBoxesFilter();

            _logs.Add($"Filtered to {_boxes.Count} boxes");

            // filter the boxes overlaped the pivot tables
            // OverlapPivotFilter();

            // refine the upper boundary of the boxes to compact, especially when header exists
            UpHeaderTrim();

            // refine the boundaries of the boxes
            SurroundingBoudariesTrim();

            // extend the up header 
            RetrieveUpHeader(1);
            RetrieveUpHeader(2);

            // extend the left header 
            RetrieveLeftHeader();
            RetrieveLeft(1);
            RetrieveLeft(2);
        }

        private void TableSenseDetect()
        {
            // calculate the rowDiffsBool and colDiffsBool map and generate rowLine and colLine
            ////// extract the rowLine generation to a independent mothod
            _sheet.ProposeBoundaryLines();

            _logs.Add($"Proposed {_sheet.colBoundaryLines.Count} boundary lines");

            //  different types of cohension regions , often can not be partly overlaped by a correct box
            _sheet.CohensionDetection();

            // split the sheet to several discotinuous parts
            ////// TODO add a new parameter, the threshhold for the discotinuty
            List<Boundary> blockRegions = _sheet.GenerateBlockRegions();

            _logs.Add($"Generated {blockRegions.Count} block regions");

            List<Boundary> sheetBoxes = new List<Boundary>();
            List<Boundary> regiongrowthBoxes = new List<Boundary>();

            // generate and filter candidates for each blockRegion separately
            // int proposalsThresh = 4000;
            // int areaThresh = 10000;
            bool largeThresh = false;
            if (_sheet.Height * _sheet.Width > 10000)
            {
                largeThresh = true;
            }

            // use regions proposed by the region growth method
            int threshHorLimit = 7;
            for (int threshHor = 1; threshHor < threshHorLimit; threshHor++)
            {
                int threshVerLimit;
                if (threshHor < 3) threshVerLimit = 3;
                else threshVerLimit = 2;
                for (int threshVer = 1; threshVer < threshVerLimit; threshVer++)
                {
                    _boxes = RegionGrowthDetector.FindConnectedRanges(_sheet.contentStrs, _sheet.valueMapBorder, threshHor, threshVer, 0);

                    foreach (var box in _boxes)
                    {
                        if (GeneralFilter(box))
                        {
                            regiongrowthBoxes.Add(box);
                        }
                    }
                    if (!largeThresh)
                    {
                        _boxes = RegionGrowthDetector.FindConnectedRanges(_sheet.contentStrs, null, threshHor, threshVer, 0);
                        foreach (var box in _boxes)
                        {
                            if (GeneralFilter(box))
                            {
                                regiongrowthBoxes.Add(box);
                            }
                        }
                    }

                }
            }

            _logs.Add($"Found {regiongrowthBoxes.Count} region growth boxes");

            if (!largeThresh)
            {
                foreach (var blockRegion in blockRegions)
                {
                    _boxes = GenerateRawCandidateBoxes(blockRegion);
                    foreach (var box in regiongrowthBoxes)
                    {
                        if (Utils.isOverlap(blockRegion, box))
                        {
                            _boxes.Add(box);
                        }
                    }
                    // filter candidate boxes in blockregion
                    _boxes = Utils.DistinctBoxes(_boxes);
                    BlockCandidatesRefineAndFilter();

                    sheetBoxes.AddRange(_boxes);
                }
            }

            _logs.Add($"Found {sheetBoxes.Count} sheet boxes");

            _boxes = sheetBoxes;
            _boxes = Utils.DistinctBoxes(_boxes);
            _boxes = Utils.RankBoxesByLocation(_boxes);
            // filter candidates over all boxes in the sheet
            CandidatesRefineAndFilter();

            _logs.Add($"Filtered to {_boxes.Count} boxes");
        }

        private List<Boundary> GenerateRawCandidateBoxes(Boundary blockReigion)
        {
            List<Boundary> blockReigionboxes = new List<Boundary>();

            #region generate boundary lines in blockregion
            // find out the rowlines and collines in blockregion
            List<int> rowBoundaryLinesBlock = new List<int>();
            List<int> colBoundaryLinesBlock = new List<int>();

            foreach (int row in _sheet.rowBoundaryLines)
            {
                if (row < blockReigion.top - 2 || row > blockReigion.bottom - 1)
                {
                    continue;
                }
                for (int index = blockReigion.left - 3; index < blockReigion.right; index++)
                {
                    Boundary boxUp = new Boundary(row + 1, row + 1, index, index + 3);
                    Boundary boxDown = new Boundary(row + 2, row + 2, index, index + 3);

                    // exist inhomogeneity between the upside and downside
                    if (((_sheet.sumContentExist.SubmatrixSum(boxUp) > 0) && (_sheet.sumContentExist.SubmatrixSum(boxDown) == 0))
                        || ((_sheet.sumContentExist.SubmatrixSum(boxDown) > 0) && (_sheet.sumContentExist.SubmatrixSum(boxUp) == 0)))
                    {
                        rowBoundaryLinesBlock.Add(row);
                        break;
                    }
                }
            }

            rowBoundaryLinesBlock = rowBoundaryLinesBlock.Distinct().ToList();

            foreach (int col in _sheet.colBoundaryLines)
            {
                if (col < blockReigion.left - 2 || col > blockReigion.right - 1)
                {
                    continue;
                }

                for (int index = blockReigion.top - 3; index < blockReigion.bottom; index++)
                {
                    Boundary boxLeft = new Boundary(index, index + 3, col + 1, col + 1);
                    Boundary boxRight = new Boundary(index, index + 3, col + 2, col + 2);
                    // ununiform between the leftside and rightside
                    if (((_sheet.sumContentExist.SubmatrixSum(boxLeft) > 0) && (_sheet.sumContentExist.SubmatrixSum(boxRight) == 0))
                        || ((_sheet.sumContentExist.SubmatrixSum(boxRight) > 0) && (_sheet.sumContentExist.SubmatrixSum(boxLeft) == 0)))
                    {
                        colBoundaryLinesBlock.Add(col);
                        break;
                    }
                }
            }
            colBoundaryLinesBlock = colBoundaryLinesBlock.Distinct().ToList();
            #endregion

            // define complex blockregion regarding to the numbers of rowline and colline
            //TODO Threshhold index
            bool markComplex = false;
            if (rowBoundaryLinesBlock.Count > 300)
            {
                rowBoundaryLinesBlock.RemoveRange(300, rowBoundaryLinesBlock.Count - 300);
                markComplex = true;
            }
            if (colBoundaryLinesBlock.Count > 150)
            {
                colBoundaryLinesBlock.RemoveRange(150, colBoundaryLinesBlock.Count - 150);
                markComplex = true;
            }

            #region  generate candidates 
            // combinations of boundary lines as candidate regions
            foreach (int left in colBoundaryLinesBlock)
            {
                if (left < blockReigion.left - 2) continue;
                foreach (int right in colBoundaryLinesBlock)
                {
                    if (left >= right) continue;
                    if (right > blockReigion.right - 1) continue;
                    Dictionary<int, bool> notValidWorDown = new Dictionary<int, bool>();
                    foreach (int up in rowBoundaryLinesBlock)
                    {
                        if (up < blockReigion.top - 2) continue;
                        Boundary boxUpOut = new Boundary(up + 1, up + 1, left + 2, right + 1);
                        if (_sheet.sumContentExist.SubmatrixSum(boxUpOut) >= 6) continue;
                        Boundary boxLeftOut = new Boundary(up + 2, up + 4, left + 1, left + 1);
                        if (_sheet.sumContentExist.SubmatrixSum(boxLeftOut) >= 6) continue;
                        Boundary boxRightOut = new Boundary(up + 2, up + 4, right + 2, right + 2);
                        if (_sheet.sumContentExist.SubmatrixSum(boxRightOut) >= 6) continue;

                        Boundary boxUpIn = new Boundary(up + 2, up + 2, left + 2, right + 1);
                        if (_sheet.sumContentExist.SubmatrixSum(boxUpIn) == 0) continue;
                        foreach (int down in rowBoundaryLinesBlock)
                        {
                            if (up >= down) continue;
                            if (down > blockReigion.bottom - 1) continue;
                            if (notValidWorDown.ContainsKey(down)) continue;

                            Boundary boxDownOut = new Boundary(down + 2, down + 2, left + 2, right + 1);
                            Boundary boxDownIn = new Boundary(down + 1, down + 1, left + 2, right + 1);
                            if (_sheet.sumContentExist.SubmatrixSum(boxDownOut) >= 6) { notValidWorDown[down] = true; continue; }
                            if (_sheet.sumContentExist.SubmatrixSum(boxDownIn) == 0) { notValidWorDown[down] = true; continue; }
                            // generate box 
                            Boundary box = new Boundary(up + 2, down + 1, left + 2, right + 1);
                            // ensure the density
                            if (markComplex && _sheet.sumAll.SubmatrixSum(box) / Utils.AreaSize(box) < 2 * 0.3)
                            {
                                continue;
                            }

                            if (GeneralFilter(box))
                            {
                                blockReigionboxes.Add(box);
                            }
                        }
                    }
                }
            }
            #endregion
            return blockReigionboxes;
        }

        private void BlockCandidatesRefineAndFilter()
        {
            _logs.Add($"BlockCandidatesRefineAndFilter running on {_boxes.Count} boxes");

            // refine the upper boundary of the boxes to compact, especially when header exists
            UpHeaderTrim();

            // filter the boxes overlaped the sheet.forcedConhensionRegions
            OverlapCohensionFilter();
            // filter the boxes overlaped the sheet.forcedBorderRegions
            OverlapBorderCohensionFilter();
            // filter the boxes overlaped the pivot tables
            // overlapPivotFilter(ref boxes);

            _logs.Add($"{_boxes.Count} boxes after overlap filters");

            // filter little and sparse boxes
            LittleBoxesFilter();

            _logs.Add($"{_boxes.Count} boxes after filtering little boxes");

            // filter boxes that overlap the header incorrectly
            OverlapUpHeaderFilter();

            // refine the clarity of Boudary lines 
            SurroundingBoudariesTrim();

            // filter boxes that overlap the header incorrectly
            OverlapUpHeaderFilter();

            _logs.Add($"{_boxes.Count} boxes after header and boundary filters");

            // find out continuous empty rows/cols that can split the box into two irrelevant regions
            SplittedEmptyLinesFilter();

            // resolve suppression conficts of candidats boxes in soft manner
            ////SuppressionSoftFilter();
            // resolve suppression conficts of candidats boxes in hard manner
            ////SuppressionHardFilter();

            _logs.Add($"{_boxes.Count} boxes after empty lines and supression filters");

            //proProcessEnlarge();
        }

        private void CandidatesRefineAndFilter()
        {
            _logs.Add($"CandidatesRefineAndFilter running on {_boxes.Count} boxes");

            // add candidate boxes formed by borders
            BorderCohensionsAddition();

            _logs.Add($"{_boxes.Count} boxes after adding border cohensions");

            // filter little and sparse boxes
            LittleBoxesFilter();

            _logs.Add($"{_boxes.Count} boxes after filtering small boxes");

            // found out the up header apart from the data region
            RetrieveDistantUpHeader();

            // connect boxes with similar neighboring rows
            VerticalRelationalMerge();

            _logs.Add($"{_boxes.Count} boxes after relational merge");

            // resolve suppression conficts of candidats boxes
            SuppressionSoftFilter();

            _logs.Add($"{_boxes.Count} boxes after resolving suppression conflicts");

            // filter boxes missed header
            HeaderPriorityFilter();

            _logs.Add($"{_boxes.Count} boxes after filtering missed header");

            // combine boxes with formula reference
            // change
            ////FormulaCorrelationFilter();

            _logs.Add($"{_boxes.Count} boxes after formula reference combine");

            // solve the contradict containing  pairs with same similar left-right boundaries or similar up-down boundaries
            PairAlikeContainsFilter();
            // solve the contradict containing  pairs
            PairContainsFilter();

            _logs.Add($"{_boxes.Count} boxes after pair filters");

            // filter big boxes with compact  sub boxes containing header
            ////CombineContainsHeaderFilter();

            _logs.Add($"{_boxes.Count} boxes after contains header filter");

            // filter big  boxes with  sparse  sub boxes 
            CombineContainsFillAreaFilterSoft();
            // filter big  boxes with row-col direction  sub boxes containing header
            CombineContainsFillLineFilterSoft();
            // filter small  boxes with row-col direction  sub boxes containing header
            ContainsLittleFilter();
            // solve the contradict containing  pairs with same left-right or up-down

            _logs.Add($"{_boxes.Count} boxes after combine/contains filters");

            PairAlikeContainsFilter();
            // solve the contradict containing pairs
            PairContainsFilter();

            // in nesting combination cases, filter the intermediate candidates
            NestingCombinationFilter();

            OverlapHeaderFilter();

            _logs.Add($"{_boxes.Count} boxes after pair, nesting, and overlap filters");

            _boxes = Utils.DistinctBoxes(_boxes);

            _logs.Add($"{_boxes.Count} boxes after DistinctBoxes()");

            // preserve the boxes same as border regions and remove the other boxes overlap with them
            ForcedBorderFilter();

            // broad suppression filter 

            // two candidate boxes, with their headers overlapping each others
            AdjoinHeaderFilter();

            _logs.Add($"{_boxes.Count} boxes after border and adjoining filters");

            // filter little and sparse boxes
            LittleBoxesFilter();

            _logs.Add($"{_boxes.Count} boxes after small boxes filter");

            // hard filter 
            PairContainsFilterHard();
            CombineContainsFilterHard();

            _logs.Add($"{_boxes.Count} boxes after contains filters");

            // complete som region growth tables if missed
            AddRegionGrowth();
            AddCompactRegionGrowth();

            _logs.Add($"{_boxes.Count} boxes after region growth");

            MergeFilter();

            _logs.Add($"{_boxes.Count} boxes after merge");

            // if early retrieve header, then isheaderup will faled in containsFilter10
            // extend the up header 
            // extend the left  header 
            RetrieveLeftHeader();
            LeftHeaderTrim();
            BottomTrim();
            RetrieveUpHeader(1);
            RetrieveUpHeader(2);
            //retrieveUpHeader(ref boxes, 2);
            UpTrimSimple();
            //retrieveLeft(ref boxes, 1);
            //retrieveLeft(ref boxes, 2);

            //refineBoundaries(ref boxes);

            LittleBoxesFilter();

            _logs.Add($"{_boxes.Count} boxes concluding CandidatesRefineAndFilter");
        }

        #region merge Boxes
        private void VerticalRelationalMerge()
        {
            // connect boxes with similar neighboring rows
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();

            List<Boundary> edgeforcedConhensionRegions = new List<Boundary>();
            // find out regions that could connect two boxes
            for (int i = 0; i < _boxes.Count; i++)
            {
                Boundary box = _boxes[i];

                Boundary boxDownRow = Utils.DownRow(box);

                Boundary boxDownRow2 = Utils.DownRow(boxDownRow, -1);

                // brittle part
                for (int k = 1; k < 6; k++)
                {
                    boxDownRow2 = Utils.DownRow(boxDownRow, -k);
                    // left header or none sparse contents
                    if ((_sheet.sumContentExist.SubmatrixSum(Utils.LeftCol(boxDownRow2)) != 0 && k > 1) || _sheet.sumContentExist.SubmatrixSum(boxDownRow2) >= 4) { break; }
                }
                if (_sheet.ContentExistValueDensity(boxDownRow2) >= 2 * 0.2 && _sheet.sumContentExist.SubmatrixSum(boxDownRow2) >= 4
                    && (_sheet.ComputeSimilarRow(boxDownRow, boxDownRow2) < 0.1)
                    && !IsHeaderUp(boxDownRow2) && !_sheet.ExistsMerged(boxDownRow2)
                    )
                {
                    Boundary forcedboxDown = new Boundary(boxDownRow.top, boxDownRow2.top, box.left, box.right);
                    edgeforcedConhensionRegions.Add(forcedboxDown);
                }

                Boundary boxUpRow = Utils.UpRow(box);

                Boundary boxUpRow2 = Utils.UpRow(boxUpRow, -1);

                // brittle part
                for (int k = 1; k < 6; k++)
                {
                    boxUpRow2 = Utils.UpRow(boxUpRow, -k);
                    // left header or none sparse contents
                    if ((_sheet.sumContentExist.SubmatrixSum(Utils.LeftCol(boxUpRow2)) != 0 && k > 1) || _sheet.sumContentExist.SubmatrixSum(boxUpRow2) >= 4) { break; }
                }
                if (_sheet.ContentExistValueDensity(boxUpRow2) >= 2 * 0.2 && _sheet.sumContentExist.SubmatrixSum(boxUpRow2) >= 4
                    && (_sheet.ComputeSimilarRow(boxUpRow, boxUpRow2) < 0.1)
                    && !IsHeaderUp(boxUpRow) && !_sheet.ExistsMerged(boxUpRow)
                    )
                {
                    Boundary forcedboxUp = new Boundary(boxUpRow2.top, boxUpRow.top, box.left, box.right);
                    edgeforcedConhensionRegions.Add(forcedboxUp);
                }
            }

            edgeforcedConhensionRegions = Utils.DistinctBoxes(edgeforcedConhensionRegions);

            foreach (var forcedbox in edgeforcedConhensionRegions)
            {
                List<Boundary> removedBoxesList = new List<Boundary>();
                // remove boxes overlap edgeforcedConhensionRegions
                foreach (var box in _boxes)
                {
                    if (forcedbox.left == box.left && forcedbox.right == box.right && Utils.isOverlap(box, forcedbox) && !Utils.ContainsBox(box, forcedbox))
                    {
                        removedBoxes.Add(box);
                        if (box.top < forcedbox.top || box.bottom > forcedbox.bottom)
                        {
                            removedBoxesList.Add(box);
                        }
                    }
                }
                // add nex combined boxes from the removed ones
                //foreach (var box1 in removedBoxesList)
                //{
                //    foreach (var box2 in removedBoxesList)
                //    {
                //        Boundary newBox = new Boundary { Math.Min(box1.up, Math.Min(forcedbox.up, box2.up)), Math.Max(box1.down, Math.Max(forcedbox.down, box2.down)), box1.left, box1.right };
                //        appendBoxes.Add(newBox);
                //    }
                //}
            }

            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
        }
        #endregion

        #region forced connected cohension or border cohension
        private List<Boundary> BorderCohensionFilterSuppression(List<Boundary> borderCohensions)
        {
            var removedBoxes = new HashSet<Boundary>();
            foreach (var box1 in _boxes)
            {
                foreach (var borderCohension in borderCohensions)
                {
                    if (Utils.isSuppressionBox(box1, borderCohension, step: 2) && !box1.Equals(borderCohension))
                    {
                        removedBoxes.Add(borderCohension);
                    }
                }
            }
            //foreach (var box in removedBoxes.Keys)
            //    boxes.Remove(box);
            foreach (var box in removedBoxes)
                borderCohensions.Remove(box);
            return borderCohensions;
        }

        private void BorderCohensionsAddition()
        {
            // add candidate boxes formed by borders
            List<Boundary> borderRegions = new List<Boundary>();
            foreach (var box in _sheet.cohensionBorderRegions)
            {
                borderRegions.Add(box);
            }
            foreach (var box in _sheet.smallCohensionBorderRegions)
            {
                if (box.bottom - box.top > 1 && box.right - box.left > 1)
                {
                    borderRegions.Add(box);
                }

            }

            List<Boundary> clearBorderRegions = new List<Boundary>();
            foreach (var box in borderRegions)
            {
                Boundary boxUp = Utils.UpRow(box, -1);
                Boundary boxDown = Utils.DownRow(box, -1);
                Boundary boxLeft = Utils.LeftCol(box, -1);
                Boundary boxRight = Utils.RightCol(box, -1);
                if (box.right - box.left > 3 && _sheet.ContentExistValueDensity(boxUp) >= 2 * 0.5)
                {
                    continue;
                }
                if (box.right - box.left > 3 && _sheet.ContentExistValueDensity(boxDown) >= 2 * 0.5)
                {
                    continue;
                }
                if (box.bottom - box.top > 3 && _sheet.ContentExistValueDensity(boxLeft) >= 2 * 0.5)
                {
                    continue;
                }
                if (box.bottom - box.top > 3 && _sheet.ContentExistValueDensity(boxRight) >= 2 * 0.5)
                {
                    continue;
                }

                clearBorderRegions.Add(box);
            }
            // filter some border boxes 
            List<Boundary> candidateRanges = BorderCohensionFilterSuppression(clearBorderRegions);

            _boxes.AddRange(candidateRanges);
            _boxes = Utils.DistinctBoxes(_boxes);
        }
        #endregion
        
        #region region growth
        private void AddRegionGrowth(int threshHor = 1, int threshVer = 1)
        {
            // generate candidate boxes predicted by the region growth method
            _regionGrowthBoxes = RegionGrowthDetector.FindConnectedRanges(_sheet.contentStrs, null, threshHor, threshVer);

            // append the candidates that proposed by addRegionGrowth method but not overlap with anyone in the current boxes
            var appendBoxes = new HashSet<Boundary>();
            var removedBoxes = new HashSet<Boundary>();

            foreach (var box in _regionGrowthBoxes)
            {
                if (_sheet.sumContentExist.SubmatrixSum(box) < 24) continue;
                List<Boundary> boxesIn = new List<Boundary>();
                List<Boundary> boxesOverlap = new List<Boundary>();
                foreach (var box2 in _boxes)
                {
                    if (Utils.isOverlap(box2, box))
                    {
                        boxesOverlap.Add(box2);
                    }
                    if (Utils.ContainsBox(box, box2, 1) && !Utils.isSuppressionBox(box, box2))
                    {
                        boxesIn.Add(box2);
                    }
                }
                // if there are no overlaps, then append this box
                if (boxesOverlap.Count == 0)
                {
                    appendBoxes.Add(box);
                }
                // if  if there is only one overlap, then decide which one to preserve by comparing the relative densities
                else if (boxesOverlap.Count == 1 && boxesIn.Count == 1 && Utils.AreaSize(box) - Utils.AreaSize(boxesIn[0]) > 20
                    && (_sheet.sumContentExist.SubmatrixSum(box) - _sheet.sumContentExist.SubmatrixSum(boxesIn[0])) / (Utils.AreaSize(box) - Utils.AreaSize(boxesIn[0])) > 0.5 * 2)
                {
                    appendBoxes.Add(box);
                    removedBoxes.Add(box);
                }
            }
            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void AddCompactRegionGrowth()
        {
            // generate compact candidate boxes predicted by the region growth method
            var regionGrowthCompactBoxes = ProProcessReduceToCompact(_regionGrowthBoxes);
            regionGrowthCompactBoxes = ProProcessReduceToCompact(regionGrowthCompactBoxes);

            // append the candidates proposed that not overlap with anyone in the current boxes
            var appendBoxes = new HashSet<Boundary>();
            foreach (var box in regionGrowthCompactBoxes)
            {
                if (_sheet.sumContentExist.SubmatrixSum(box) < 7) continue;
                if (_sheet.sumContentExist.SubmatrixSum(new Boundary(box.top - 1, box.bottom + 1, box.left - 1, box.right + 1)) - _sheet.sumContentExist.SubmatrixSum(box) > 10) continue;

                if (!Utils.isOverlap(box, _boxes))
                {
                    appendBoxes.Add(box);
                }
            }
            Utils.AppendTheseCandidates(appendBoxes, _boxes);
        }
        #endregion
    }
}
```

## File: code/TableDetectionMLHeuHybrid.cs
```csharp
using System.Collections.Generic;
using System.Linq;

namespace SpreadsheetLLM.Heuristic
{
    internal partial class TableDetectionHybrid
    {
        public List<Boundary> HybridCombine(SheetMap inputSheet, List<Boundary> heuResults, List<Boundary> mlResults)
        {
            _sheet = inputSheet;

            _logs.Add($"HybridCombine running on {_boxes.Count} boxes");

            var appendBoxes = heuResults.Where(box =>
                _sheet.sumContentExist.SubmatrixSum(box) >= 8 &&
                !mlResults.Any(box2 => Utils.isOverlap(box2, box)));
            Utils.AppendTheseCandidates(appendBoxes, mlResults);
            _boxes = mlResults;

            // refine the clarity of Boudary lines 
            SparseBoundariesTrim2();

            CutHeaderFormatFilter();

            ////AddCompactRegionGrowth2(); // Xiao: Remove this logic for better accuracy

            return _boxes;
        }

        private void SparseBoundariesTrim2()
        {
            // for four directions, skip the sparse edges
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();

            foreach (var box in _boxes)
            {
                var (top, bottom, left, right) = (box.top, box.bottom, box.left, box.right);
                bool markChange = true;
                while (markChange && left < right && top < bottom)
                {
                    markChange = false;

                    // change newBox when empty row found
                    // left
                    while (left > 0 && left < right && _sheet.sumContentExist.SubmatrixSum(new Boundary(top, bottom, left, left)) == 0)
                    {
                        left++;
                        markChange = true;
                    }

                    // right
                    while (right <= _sheet.Width && right > left && _sheet.sumContentExist.SubmatrixSum(new Boundary(top, bottom, right, right)) == 0)
                    {
                        right--;
                        markChange = true;
                    }

                    // up
                    while (top > 0 && top < bottom && _sheet.sumContentExist.SubmatrixSum(new Boundary(top, top, left, right)) == 0)
                    {
                        top++;
                        markChange = true;
                    }

                    // down
                    while (bottom <= _sheet.Height && bottom > top && _sheet.sumContentExist.SubmatrixSum(new Boundary(bottom, bottom, left, right)) == 0)
                    {
                        bottom--;
                        markChange = true;
                    }
                }

                var newBox = new Boundary(top, bottom, left, right);
                if (!box.Equals(newBox))
                {
                    removedBoxes.Add(box);
                    if (left < right && top < bottom)
                    {
                        appendBoxes.Add(newBox);
                    }
                }
            }

            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
            _boxes = Utils.DistinctBoxes(_boxes);
        }

        private void CutHeaderFormatFilter()
        {
            // two candidate _boxes, with their headers overlapping each others
            var removedBoxes = new HashSet<Boundary>();
            var appendBoxes = new HashSet<Boundary>();

            foreach (var box in _boxes)
            {
                // As evaluated, removing this check can keep the accuracy while simplifying the logic
                ////if (!IsHeaderUp(Utils.UpRow(box)))
                ////    continue;

                var (left, right) = (box.left, box.right);
                for (; right < _sheet.Width; right++)
                {
                    var rightCell = new Boundary(box.top, box.top, right, right);
                    var rightColor = _sheet.sumColor.SubmatrixSum(rightCell);
                    var rightBorder = _sheet.sumBorderRow.SubmatrixSum(rightCell);
                    var nextCell = new Boundary(box.top, box.top, right + 1, right + 1);
                    var nextColor = _sheet.sumColor.SubmatrixSum(nextCell);
                    var nextBorder = _sheet.sumBorderRow.SubmatrixSum(nextCell);
                    var nextContent = _sheet.sumContent.SubmatrixSum(nextCell);
                    if (nextContent == 0 || nextColor + nextBorder == 0 || nextColor != rightColor || nextBorder != rightBorder)
                        break;
                }

                for (; left > 1; left--)
                {
                    var leftCell = new Boundary(box.top, box.top, left, left);
                    var leftColor = _sheet.sumColor.SubmatrixSum(leftCell);
                    var leftBorder = _sheet.sumBorderRow.SubmatrixSum(leftCell);
                    var prevCell = new Boundary(box.top, box.top, left - 1, left - 1);
                    var prevColor = _sheet.sumColor.SubmatrixSum(prevCell);
                    var prevBorder = _sheet.sumBorderRow.SubmatrixSum(prevCell);
                    var prevContent = _sheet.sumContent.SubmatrixSum(prevCell);
                    if (prevContent == 0 || prevColor + prevBorder == 0 || prevColor != leftColor || prevBorder != leftBorder)
                        break;
                }

                if (right != box.right || left != box.left)
                {
                    removedBoxes.Add(box);
                    appendBoxes.Add(new Boundary(box.top, box.bottom, left, right));
                }
            }

            Utils.RemoveAndAppendCandidates(removedBoxes, appendBoxes, _boxes);
        }

        private void AddCompactRegionGrowth2()
        {
            // append the candidates proposed that not overlap with anyone in the current boxes
            var appendBoxes = _regionGrowthBoxes.Where(box =>
            {
                int contentSum = _sheet.sumContentExist.SubmatrixSum(box);
                int textDistinctCount = _sheet.TextDistinctCount(box);
                return !IsOverlapOrEqual(box, _boxes)
                    && contentSum >= System.Math.Min(8, 2 * ((box.bottom - box.top + 1) * (box.right - box.left + 1) - 3))
                    && _sheet.sumContentExist.SubmatrixSum(new Boundary(box.top - 1, box.bottom + 1, box.left - 1, box.right + 1)) - contentSum <= 10
                    && textDistinctCount > System.Math.Max(box.bottom - box.top + 2, box.right - box.left + 2);
            });

            Utils.AppendTheseCandidates(appendBoxes, _boxes);
        }

        private static bool IsOverlapOrEqual(Boundary box1, List<Boundary> boxes2, bool exceptForward = false, bool exceptBackward = false, bool exceptSuppression = false)
        {
            return boxes2 != null && boxes2.Any(box2 => Utils.isOverlap(box1, box2, exceptForward, exceptBackward, exceptSuppression));
        }
    }
}
```

## File: code/TableSense.Heuristic.csproj
```
<Project Sdk="Microsoft.NET.Sdk">

  <PropertyGroup>
    <TargetFrameworks>net6.0;net462</TargetFrameworks>
    <Platforms>x64</Platforms>
    <RootNamespace>SpreadsheetLLM.Heuristic</RootNamespace>
    <AssemblyName>SpreadsheetLLM.Heuristic</AssemblyName>
  </PropertyGroup>

  <ItemGroup>
    <ProjectReference Include="..\..\SheetCore\SheetCore.csproj" />
    <ProjectReference Include="..\..\SheetCore.Extension\SheetCore.Extension.csproj" />
  </ItemGroup>

</Project>
```

## File: code/Utils.cs
```csharp
using System;
using System.Linq;
using System.Collections.Generic;

namespace SpreadsheetLLM.Heuristic
{
    internal static class Utils
    {
        #region remove from Box List
        public static void RemoveTheseCandidates(IEnumerable<Boundary> CandidatesToRemoved, List<Boundary> boxes)
        {
            if (CandidatesToRemoved.Any())
            {
                boxes.RemoveAll(CandidatesToRemoved.Contains);
            }
        }

        public static void AppendTheseCandidates(IEnumerable<Boundary> CandidatesToAppend, List<Boundary> boxes)
        {
            if (CandidatesToAppend.Any())
            {
                if (!(CandidatesToAppend is HashSet<Boundary> candidatesHashSet))
                    candidatesHashSet = new HashSet<Boundary>(CandidatesToAppend);
                candidatesHashSet.ExceptWith(boxes);
                boxes.AddRange(candidatesHashSet);
            }
        }

        public static void RemoveAndAppendCandidates(IEnumerable<Boundary> CandidatesToRemoved, IEnumerable<Boundary> CandidatesToAppend, List<Boundary> boxes)
        {
            RemoveTheseCandidates(CandidatesToRemoved, boxes);
            AppendTheseCandidates(CandidatesToAppend, boxes);
        }
        #endregion

        #region Utils
        public static int CountTure(List<bool> diffs)
        {
            return diffs.Count(diff => diff);
        }

        public static bool VerifyValuesOpposite(int x, int y)
        {
            return x == 0 != (y == 0);
        }

        public static bool VerifyValuesDiff(int x, int y, int cellDiffThresh)
        {
            return Math.Abs(x - y) > cellDiffThresh;
        }

        public static bool IsFillBox(Boundary box1, List<Boundary> inBoxes, int step = 0)
        {
            if (inBoxes.Count == 0)
                return false;

            var rowLinesCombine = new List<int>();
            var colLinesCombine = new List<int>();
            foreach (var box2 in inBoxes)
            {
                if (!(box2.top <= box1.top + 2 && box2.bottom >= box1.bottom - 2))
                {
                    for (int i = box2.top - 2; i <= box2.bottom; i++)
                    {
                        rowLinesCombine.Add(i);
                    }
                }
                if (!(box2.left <= box1.left + 2 && box2.right >= box1.right - 2))
                {
                    for (int i = box2.left - 2; i <= box2.right; i++)
                    {
                        colLinesCombine.Add(i);
                    }
                }
            }

            if (Min(rowLinesCombine) > box1.top || Max(rowLinesCombine) < box1.bottom || Min(colLinesCombine) > box1.left || Max(colLinesCombine) < box1.right)
                return false;

            if (!IsFillLines(box1.top, box1.bottom, rowLinesCombine))
                return false;

            if (!IsFillLines(box1.left, box1.right, colLinesCombine))
                return false;

            return true;
        }

        public static bool IsFillBoxRowColLines(Boundary box1, List<int> rowLinesCombine, List<int> colLinesCombine, int step = 0)
        {
            return IsFillLines(box1.top, box1.bottom, rowLinesCombine)
                || IsFillLines(box1.left, box1.right, colLinesCombine);
        }
        public static bool IsFillLines(int start, int end, List<int> lines, int step = 0)
        {
            if (lines.Count == 0)
                return false;

            var linesInside = lines.Where(line => !(line < start || line > end)).ToList();
            if (Min(linesInside) <= start && Max(linesInside) >= end)
            {
                if (linesInside.Distinct().Count() > (end - start + 1) * 0.8)
                {
                    return true;
                }
                if (end - start > 5)
                {
                    int maxGap = 0;
                    linesInside.Sort();
                    int lineUp = linesInside[0];
                    foreach (int line in linesInside)
                    {
                        maxGap = Math.Max(maxGap, line - lineUp);
                        lineUp = line;
                    }
                    maxGap = Math.Max(maxGap, end - lineUp);
                    if (maxGap <= Math.Max(5, (end - start) * 0.2))
                        return true;
                }
            }

            return false;
        }

        public static List<Boundary> DistinctBoxes(List<Boundary> boxes)
        {
            return boxes.Distinct().ToList();
        }

        public static int DistinctStrs(string[,] contents, int up, int down, int left, int right)
        {
            var strings = new HashSet<string>();
            for (int i = up - 1; i <= down - 1; i++)
            {
                for (int j = left - 1; j <= right - 1; j++)
                {
                    if (contents[i, j] != "")
                        strings.Add(contents[i, j]);
                }
            }
            return strings.Count;
        }

        public static double AreaSize(Boundary box)
        {
            if (box.bottom < box.top || box.right < box.left)
                return 0;

            return (box.bottom - box.top + 1) * (box.right - box.left + 1);
        }

        public static double Height(Boundary box)
        {
            if (box.bottom < box.top)
                return 0;

            return box.bottom - box.top + 1;
        }
        public static double Width(Boundary box)
        {
            if (box.right < box.left)
                return 0;

            return box.right - box.left + 1;
        }

        public static Boundary LeftCol(Boundary box, int start = 0, int step = 1)
        {
            return new Boundary(box.top, box.bottom, box.left + start, box.left + start + step - 1);
        }

        public static Boundary UpRow(Boundary box, int start = 0, int step = 1)
        {
            return new Boundary(box.top + start, box.top + start + step - 1, box.left, box.right);
        }
        public static Boundary RightCol(Boundary box, int start = 0, int step = 1)
        {
            return new Boundary(box.top, box.bottom, box.right - start - step + 1, box.right - start);
        }
        public static Boundary DownRow(Boundary box, int start = 0, int step = 1)
        {
            return new Boundary(box.bottom - start - step + 1, box.bottom - start, box.left, box.right);
        }
        public static double AreaSize(List<Boundary> boxes)
        {
            double sizeSum = 0;
            var boxesCalculated = new List<Boundary>();
            foreach (var box in boxes)
            {
                sizeSum = sizeSum + AreaSize(box);
                bool markIn = false;
                foreach (var boxOut in boxes)
                {
                    if (ContainsBox(boxOut, box) && !boxOut.Equals(box))
                    {
                        markIn = true;
                        break;
                    }
                }
                if (markIn) continue;
                foreach (var boxIn in boxesCalculated)
                {
                    if (isOverlap(boxIn, box))
                    {
                        sizeSum = sizeSum - AreaSize(OverlapBox(boxIn, box));

                    }
                }
                foreach (var boxIn1 in boxesCalculated)
                {
                    foreach (var boxIn2 in boxesCalculated)
                    {
                        if (isOverlap(boxIn1, box) && isOverlap(boxIn2, box) && isOverlap(boxIn2, boxIn1) && !boxIn2.Equals(boxIn1) && !boxIn2.Equals(box) && !box.Equals(boxIn1))
                        {
                            sizeSum = sizeSum + AreaSize(OverlapBox(box, boxIn1, boxIn2));

                        }
                    }
                }
                boxesCalculated.Add(box);
            }
            return sizeSum;
        }

        public static List<Boundary> GetUnifiedRanges(List<Boundary> mergeBoxes)
        {
            // unify the overlapping and neighboring boxes

            List<Boundary> newBoxes = new List<Boundary>(mergeBoxes);
            List<Boundary> prevBoxes;

            int cntUnify = -1;

            // cycling in the loop until no unify happens
            while (cntUnify != 0)
            {
                cntUnify = 0;
                //dic record which boxes are unified already
                Dictionary<int, bool> boxDic = new Dictionary<int, bool>();

                prevBoxes = newBoxes;
                newBoxes = new List<Boundary>();

                for (int i = 0; i < prevBoxes.Count; i++)
                {
                    if (boxDic.ContainsKey(i)) continue;
                    bool markUnified = false;
                    // search for boxes overlap or neibouring with  the selected one
                    for (int j = i + 1; j < prevBoxes.Count; j++)
                    {
                        if (boxDic.ContainsKey(j)) continue;
                        if (isOverlapOrNeighbor(prevBoxes[i], prevBoxes[j]))
                        {
                            // unify the two boxes
                            Boundary newBox = UnifyBox(prevBoxes[i], prevBoxes[j]);
                            newBoxes.Add(newBox);

                            boxDic[j] = true;
                            markUnified = true;
                            cntUnify++;

                            break;
                        }
                    }
                    if (!markUnified) newBoxes.Add(prevBoxes[i]);
                }
            }
            return newBoxes;
        }

        public static double Min(List<int> arr)
        {
            if (arr.Count == 0)
                return int.MaxValue;

            return arr.Min();
        }

        public static double Max(List<int> arr)
        {
            if (arr.Count == 0)
                return int.MinValue;

            return arr.Max();
        }

        public static Boundary OverlapBox(Boundary box1, Boundary box2)
        {
            return new Boundary(
                Math.Max(box1.top, box2.top),
                Math.Min(box1.bottom, box2.bottom),
                Math.Max(box1.left, box2.left),
                Math.Min(box1.right, box2.right));
        }

        public static Boundary UnifyBox(Boundary box1, Boundary box2)
        {
            return new Boundary(
                Math.Min(box1.top, box2.top),
                Math.Max(box1.bottom, box2.bottom),
                Math.Min(box1.left, box2.left),
                Math.Max(box1.right, box2.right));
        }
        public static Boundary OverlapBox(Boundary box1, Boundary box2, Boundary box3)
        {
            return OverlapBox(box1, OverlapBox(box2, box3));
        }
        public static int GetNumberFormatCode(string format)
        {
            switch (format)
            {
                case "": return 0;
                case "General": return 0;
                case "0": return 1;
                case "0.00": return 1;
                case "#,##0": return 1;
                case "#,##0.00": return 1;
                case "0%": return 1;
                case "0.00%": return 1;
                case "0.00E+00": return 1;
                case "# ?/?": return 2;
                case "# ??/??": return 2;
                case "d/m/yyyy": return 3;
                case "d-mmm-yy": return 3;
                case "d-mmm": return 3;
                case "mmm-yy": return 3;
                case "h:mm tt": return 3;
                case "h:mm:ss tt": return 3;
                case "H:mm": return 3;
                case "H:mm:ss": return 3;
                case "m/d/yyyy H:mm": return 3;
                case "#,##0 ;(#,##0)": return 1;
                case "#,##0 ;[Red](#,##0)": return 1;
                case "#,##0.00;(#,##0.00)": return 1;
                case "#,##0.00;[Red](#,##0.00)": return 1;
                case "mm:ss": return 3;
                case "[h]:mm:ss": return 3;
                case "mmss.0": return 3;
                case "##0.0E+0": return 1;
                case "@": return 2;
                default: return 0;
            }
        }
        #endregion

        #region statistics features for rows/cols
        public static double average(List<double> Valist)
        {
            double sum = 0;
            foreach (double d in Valist)
            {
                sum = sum + d;
            }
            double revl = Math.Round(sum / Valist.Count, 3);
            return revl;
        }

        public static double stdev(List<double> ValList)
        {
            double avg = average(ValList);
            double sumstdev = 0;
            foreach (double d in ValList)
            {
                sumstdev = sumstdev + (d - avg) * (d - avg);
            }

            double stdeval = System.Math.Sqrt(sumstdev / ValList.Count);
            return Math.Round(stdeval, 3);
        }

        public static double r1(List<double> ValList1, List<double> ValList2)
        {
            double sumR1 = 0;
            for (int i = 0; i < ValList1.Count; i++)
            {
                sumR1 = sumR1 + Math.Abs(ValList1[i] - ValList2[i]);
            }
            return sumR1 / ValList1.Count;
        }

        public static double correl(List<double> array1, List<double> array2)
        {
            double avg1 = average(array1);
            double stdev1 = stdev(array1);

            double avg2 = average(array2);
            double stdev2 = stdev(array2);

            double sum = 0;
            for (int i = 0; i < array1.Count && i < array2.Count; i++)
            {
                sum = sum + (array1[i] - avg1) / stdev1 * ((array2[i] - avg2) / stdev2);
            }
            return Math.Round(sum / array1.Count, 3);
        }
        #endregion

        #region rank boxes
        public static List<Boundary> RankBoxesByLocation(List<Boundary> ranges)
        {
            ranges.Sort((x, y) => ComputeBoxByLocation(x).CompareTo(ComputeBoxByLocation(y)));
            return ranges;
        }

        public static List<Boundary> RankBoxesBySize(List<Boundary> ranges)
        {
            ranges.Sort((x, y) => AreaSize(y).CompareTo(AreaSize(x)));
            return ranges;
        }

        public static double ComputeBoxByLocation(Boundary box)
        {
            return box.left + 0.00001 * box.top;
        }
        #endregion

        #region box position related
        public static bool ContainsBox(Boundary box1, Boundary box2, int step = 0)
        {
            return box1.top <= box2.top + step && box1.bottom >= box2.bottom - step && box1.left <= box2.left + step && box1.right >= box2.right - step;
        }

        public static bool ContainsBox(List<Boundary> boxes1, Boundary box2, int step = 0)
        {
            return boxes1.Any(box1 => ContainsBox(box1, box2, step) && !box1.Equals(box2));
        }

        public static bool ContainsBox(Boundary box1, List<Boundary> boxes2, int step = 0)
        {
            return boxes2.Any(box2 => ContainsBox(box1, box2, step) && !box1.Equals(box2));
        }

        public static bool isOverlapOrNeighbor(Boundary box1, Boundary box2)
        {
            return !((box1.top > box2.bottom + 1) || (box1.bottom + 1 < box2.top) || (box1.left > box2.right + 1) || (box1.right + 1 < box2.left));
        }

        public static bool isOverlap(Boundary box1, List<Boundary> boxes2, bool exceptForward = false, bool exceptBackward = false, bool exceptSuppression = false)
        {
            return boxes2 != null && boxes2.Any(box2 => isOverlap(box1, box2, exceptForward, exceptBackward, exceptSuppression) && !box1.Equals(box2));
        }

        public static bool isOverlap(Boundary box1, Boundary box2)
        {
            return !((box1.top > box2.bottom) || (box1.bottom < box2.top) || (box1.left > box2.right) || (box1.right < box2.left));
        }

        public static bool isOverlap(Boundary box1, Boundary box2, bool exceptForward = false, bool exceptBackward = false, bool exceptSuppression = false)
        {
            return isOverlap(box1, box2) && !(exceptForward && ContainsBox(box1, box2)) && !(exceptBackward && ContainsBox(box2, box1)) && !(exceptSuppression && isSuppressionBox(box1, box2));
        }

        public static bool isSuppressionBox(Boundary box1, Boundary box2, int step = 2, int directionNum = -1)
        {
            for (int i = 0; i < 4; i++)
            {
                if (directionNum >= 0 && i != directionNum && directionNum != 4)
                {
                    if (Math.Abs(box1[i] - box2[i]) > 0)
                        return false;
                }
                else
                {
                    if (Math.Abs(box1[i] - box2[i]) > step)
                        return false;
                }
            }
            return true;
        }
        #endregion

        #region sheet map related
        /// <summary>
        /// Calculate sum value to each cell of the submatrix from upper-left to the cell.
        /// </summary>
        public static int[,] CalcSumMatrix(this int[,] valueMap)
        {
            // calculate the sum map from raw map
            int height = valueMap.GetLength(0);
            int width = valueMap.GetLength(1);
            if (height == 0 || width == 0)
                return valueMap;

            int[,] result = new int[height, width];
            result[0, 0] = valueMap[0, 0];
            for (int i = 1; i < height; ++i)
                result[i, 0] = result[i - 1, 0] + valueMap[i, 0];
            for (int j = 1; j < width; ++j)
                result[0, j] = result[0, j - 1] + valueMap[0, j];
            for (int i = 1; i < height; ++i)
                for (int j = 1; j < width; ++j)
                    result[i, j] = result[i - 1, j] + result[i, j - 1] - result[i - 1, j - 1] + valueMap[i, j];

            return result;
        }

        /// <summary>
        /// Summarize rectangular values in submatrix from summarized value map.
        /// </summary>
        public static int SubmatrixSum(this int[,] valueSumMap, Boundary box)
        {
            box.top = Math.Min(Math.Max(box.top, 1), valueSumMap.GetLength(0));
            box.bottom = Math.Min(Math.Max(box.bottom, 1), valueSumMap.GetLength(0));
            box.left = Math.Min(Math.Max(box.left, 1), valueSumMap.GetLength(1));
            box.right = Math.Min(Math.Max(box.right, 1), valueSumMap.GetLength(1));
            if (box.top > box.bottom || box.left > box.right) return 0;

            int result = valueSumMap[box.bottom - 1, box.right - 1];
            if (box.left > 1) result -= valueSumMap[box.bottom - 1, box.left - 2];
            if (box.top > 1) result -= valueSumMap[box.top - 2, box.right - 1];
            if (box.left > 1 && box.top > 1) result += valueSumMap[box.top - 2, box.left - 2];

            return result;
        }
        #endregion
    }

}
```

## File: tool/Heuristics_tool/result.txt
...omitted 

## File: tool/Heuristics_tool/SheetCore.xml
...omitted 

## File: tool/README.txt
```
TableSense.Heuristic.exe spreadshet_file_path sheet_name result.txt
```
