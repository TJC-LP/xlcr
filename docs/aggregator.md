This file is a merged representation of a subset of the codebase, containing specifically included files, combined into a single document by Repomix.

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
- Only files matching these patterns are included: compress*/**
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded

## Additional Info

# Directory Structure
```
compress_aggregation/
  CaseJudge.py
  config.py
  TableDataAggregation.py
  utils.py
  ValueAddress.py
compress_struture/
  Compress_structure.py
  config.py
  utils.py
```

# Files

## File: compress_aggregation/CaseJudge.py
```python
import re

def is_scientific_notation(s):
    
    pattern = r'^[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?$'
    return re.match(pattern, s) is not None


def is_percentage(s):
    
    pattern = r'^[+-]?(\d+(\.\d*)?|\.\d+)%$'
    return re.match(pattern, s) is not None


def is_currency(s):
    
    pattern = r'^[\$\€\£\¥]\d+(\.\d{1,2})?$'
    return bool(re.match(pattern, s))


def is_date(s):
       
    
    date_patterns = [
    r"\d{4}[-/]\d{1,2}[-/]\d{1,2}",  
    r"\d{1,2}[-/]\d{1,2}[-/]\d{4}",  
    r"\d{1,2}[-/]\d{1,2}",           
    r"\d{4}[-/]\d{1,2}"              
    ]
    
    combined_pattern = "(" + ")|(".join(date_patterns) + ")"
    
    match = re.match(combined_pattern, s)
    
    return bool(match)

def is_time(s):
    
    pattern = re.compile(r"^(2[0-3]|[01]?\d):([0-5]?\d)(\s?(AM|PM|am|pm))?$")
    return bool(pattern.match(s))

def is_fraction(s): 
    try:  
        parts = s.split('/')
        if len(parts) != 2:
            return False  
        numerator, denominator = map(int, parts)
        return denominator != 0
    except ValueError:
        return False

def check_ip(ip):
    def is_ipv4(ip):
        pattern = r'^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$'
        return re.match(pattern, ip)
    def is_ipv6(ip):
        pattern = r'^([\da-fA-F]{1,4}:){7}([\da-fA-F]{1,4})$'
        return re.match(pattern, ip)
    
    if is_ipv4(ip) or is_ipv6(ip):
        return True
  

def is_email(email):
    pattern = r'^[\w\.-]+@[\w\.-]+\.\w+$'
    return re.match(pattern, email)
```

## File: compress_aggregation/config.py
```python
DIC = {

            "0":1,
            "0.00":2,
            "#,##0":1,
            "#,##0.00":2,
            "0%":3,
            "0.00%":3,
            "0.00E+00":4,
            "#,##0;(#,##0)":1,
            "#,##0;[Red](#,##0)":1,
            "#,##0.00;(#,##0.00)":2,
            "#,##0.00;[Red](#,##0.00)":2,
            "##0.0E+0":4,

            "d/m/yyyy": 5,
            "d-mm-yy":5,
            "d-mmm":5,
            "mmm-yy":5,

            "h:mm tt": 6,
            "h:mm:ss tt":6,
            "H:mm":6,
            "H:mm:ss":6,
            "m/d/yyyy H:mm":6,
            "mm:ss":6,
            "[h]:mm:ss":6,
            "mmss.0":6
        }

AGGREGATION_INPUT_FILE_PATH = ""

AGGREGATION_OUTPUT_FILE_PATH = ""

VA_INPUT_FILE_PATH = ""

VA_OUTPUT_FILE_PATH = ""

FMT1_TAG = False    

FMT3_TAG = False    

NFS_TAG = True
```

## File: compress_aggregation/TableDataAggregation.py
```python
import json
import re
import sys
import config
import CaseJudge
import openpyxl
from utils import get_table_cell_input
from utils import get_table_nfs_input
from utils import parse_excel_row_value
from utils import tuple_to_excel_cell
from utils import excel_address_to_coords
from utils import get_table_fmt_input
from utils import parse_excel_row


sys.setrecursionlimit(10000)



class MyCustomException(Exception):
    pass


class TableDataAggregation:

    def __init__(self, input_file_path, output_file_path):
        self.input_file_path = input_file_path
        self.output_file_path = output_file_path

 
    @staticmethod
    def check_and_process_string(input_str):
        
        if all(char.isdigit() or char == ',' or char == '.' or (char in '+-' and input_str.index(char) == 0) for char in input_str):
            return input_str.replace(',', '')

        else:
            return input_str

    
    def identify_number(self, s):
        if re.match(r'^-?\d+$', s):
            return 1
        elif re.match(r'^-?\d+\.\d+$', s):
            return 2
        elif CaseJudge.is_percentage(s):
            return 3
        elif CaseJudge.is_scientific_notation(s):
            return 4
        elif CaseJudge.is_date(s):
            return 5
        elif CaseJudge.is_time(s):
            return 6
        elif CaseJudge.is_currency(s):
            return 7
        elif CaseJudge.is_email(s):
            return 8
        else:
            return 9


    def get_type(self, nfs_cell, cell):

        if cell == "":
            return -1
        if config.NFS_TAG: 
            
            if nfs_cell == "None":
                year_pattern = r'^19\d{2}$|^20\d{2}$'

                if bool(re.match(year_pattern, cell)):  
                   r = 0
                
                else:
                    r = self.identify_number(self.check_and_process_string(cell))
                    if r == 9 :
                        r = cell
                return r
            else:
                return nfs_cell
        
        else:   
            if nfs_cell in config.DIC:
                return config.DIC[nfs_cell]
            
            year_pattern = r'^19\d{2}$|^20\d{2}$'
            if bool(re.match(year_pattern, cell)):
                r = 0
            
            else:
                r = self.identify_number(self.check_and_process_string(cell))

                if r == 9:
                    r = cell    
                    # if nfs_cell == "None":
                    #     r = cell
                    # else:
                    #     r = nfs_cell
                    
            return r


    def aggregate_similar_areas(self, input, nfs_input):
        rows, cols = len(input), len(input[0]) if input else 0
        visited = [[False for _ in range(cols)] for _ in range(rows)]
        

        def is_valid(r, c, val_type):
            if not (0 <= r < rows and 0 <= c < cols and not visited[r][c]):
                return False
            type = self.get_type(nfs_input[r][c], input[r][c])

            if val_type == type:
                tag = True
            else:
                tag = False
            
            return 0 <= r < rows and 0 <= c < cols and not visited[r][c] and tag

        def dfs(r, c, val_type):

            if not is_valid(r, c, val_type):
                return [r, c, r-1, c-1]  

            visited[r][c] = True
            bounds = [r, c, r, c]  

            for dr, dc in [(-1, 0), (1, 0), (0, -1), (0, 1)]:  
                new_r, new_c = r + dr, c + dc
                if is_valid(new_r, new_c, val_type):
                    new_bounds = dfs(new_r, new_c, val_type)
                    
                    bounds[0], bounds[1] = min(bounds[0], new_bounds[0]), min(bounds[1], new_bounds[1])
                    bounds[2], bounds[3] = max(bounds[2], new_bounds[2]), max(bounds[3], new_bounds[3])

            return bounds
        areas = []
        for r in range(rows):
            for c in range(cols):
                if not visited[r][c]:
                    if input[r][c]!="":
                        val_type = self.get_type(nfs_input[r][c],input[r][c])
                        bounds = dfs(r, c, val_type)
                        if bounds[0] <= bounds[2] and bounds[1] <= bounds[3]:  
                            areas.append(((bounds[0], bounds[1]), (bounds[2], bounds[3]), val_type))
        
        return areas


    def change_data(self, begin, end, val_type, input):
        begin_position = excel_address_to_coords(begin)
        end_position = excel_address_to_coords(end)

        tag = val_type

        for i in range(0,len(input)):

            if i>=begin_position[0] and i <= end_position[0]:
                for j in range(0,len(input[0])):
                    if j >= begin_position[1] and j <= end_position[1]:
                        cell_address = input[i][j].split(",",1)[0]
                        cell_value = input[i][j].split(",",1)[1]
                        if isinstance(tag, str):
                            new_value = tag
                        else:
                            if tag == 0:
                                new_value = "YearData"
                        
                            elif tag == 1:
                                new_value = "IntNum"

                            elif tag == 2:
                                new_value = "FloatNum"
                       
                            elif tag == 3:
                                new_value = "PercentageNum"
                        
                            elif tag == 4:
                                new_value = "SentificNum"

                            elif tag == 5:
                                new_value = "DateData"
                        
                            elif tag == 6:
                                new_value = "TimeData"
                       
                            elif tag == 7:
                                new_value = "CurrencyData"
                        
                            elif tag == 8:
                                new_value = "EmailData"
                        
                            else:
                                new_value = cell_value
                        input[i][j] = cell_address + "," + new_value
        return input


    def get_new_input(self, input, fmt = None):
        new_rows = []
        for i in range(0,len(input)):
            new_row = ""
            for j in range(0,len(input[0])):
                new_row += "|" + input[i][j]
            new_rows.append(new_row)
    
        input_change = "\nInput: "
        for k in range(0,len(new_rows)):
            input_change += new_rows[k] + "\n"
        
        input_change += "\n\n###\n\n"

    
        return input_change


    def process_file(self):
        with open(self.input_file_path, 'r', encoding='utf-8') as input_file, open(self.output_file_path, 'w', encoding='utf-8') as output_file:
            for line in input_file:
                try:
                    data = json.loads(line)
                    prompt = data["messages"][1]["content"]
                    
                    cell_rows = get_table_cell_input(prompt)
                    nfs_rows = get_table_nfs_input(prompt)
                    
                    fmt = None
                    if config.FMT1_TAG or config.FMT3_TAG:
                        fmt = get_table_fmt_input(prompt)

                    cell_input = []
                    for row in cell_rows:
                        cell_input.append(parse_excel_row_value(row))
                    
                    nfs_input = []
                    for row in nfs_rows:
                        nfs_input.append(parse_excel_row_value(row))
                
                    

                    areas = self.aggregate_similar_areas(cell_input, nfs_input)
                    
                    first_address = parse_excel_row(cell_rows[0])[0].split(",", 1)[0]
                    matches = re.match(r"([A-Z]+)(\d+)", first_address)
                    uppercase_part = matches.group(1)
                    numeric_part = matches.group(2)
                    row_l = int(numeric_part) - 1
                    col_l = openpyxl.utils.column_index_from_string(uppercase_part) - 1



                    new_areas = []
                    new_areas1 = []

                    for area in areas:
                        begin_num_index = [area[0][0]+row_l, area[0][1] + col_l]
                        end_num_index = [area[1][0]+row_l, area[1][1] + col_l]
                        begin_address = tuple_to_excel_cell(begin_num_index)
                        end_address = tuple_to_excel_cell(end_num_index)

                        new_areas1.append((begin_address + ":" + end_address, area[2]))
                        if begin_address != end_address and area[2] != 9:
                            new_areas.append((begin_address + ":" + end_address, area[2]))
                    
                    data["areas"] = new_areas
                    data["areas1"] = new_areas1
               
                    cell_input = []
                    for row in cell_rows:
                        cell_input.append(parse_excel_row(row))
                    for area in new_areas:
                        begin_address = area[0].split(":")[0]
                        end_addresss = area[0].split(":")[1]
                        cell_input = self.change_data(begin_address,end_addresss,area[1],cell_input)
                    
                    new_input = self.get_new_input(cell_input,fmt=fmt)
                    

                    new_prompt = new_input
                    
                    data["messages"][1]["content"] = new_prompt
                    output_file.write(json.dumps(data) + '\n')
                    
                except RecursionError:
                    print("RecursionError: " + data["file_name"])
                    continue  
                except Exception as e:
                    print(f"An error occurred on line : {e}, file_name: " + data["file_name"])
                    continue


if __name__ == "__main__":

    compressor = TableDataAggregation(config.AGGREGATION_INPUT_FILE_PATH, 
                                      config.AGGREGATION_OUTPUT_FILE_PATH)
    compressor.process_file()
```

## File: compress_aggregation/utils.py
```python
import re
import json
import tiktoken
import config


def get_table_input(prompt):
    if config.FMT3_TAG:
        
        pattern = r'\nInput: (.*?)\nFormat_Dict: '
    else:
        pattern = r'(?<=Input: ).+?(?=\n\n###\n\n)'
    
    matches = re.findall(pattern, prompt, re.DOTALL)
    sheet = matches[0].strip()
    first_c = sheet[1]
    rows_ = sheet.split('\n|'+first_c)
    rows = [rows_[0]]
    for k in range(1,len(rows_)):
        rows.append("|"+ first_c+rows_[k])
    
    return rows

def get_table_cell_input(prompt):
    input_pattern = r'\nCell_Input: (.*?)\nNFS_Input:' 
   
    matches = re.findall(input_pattern, prompt, re.DOTALL)
    
    sheet = matches[0].strip()      
    first_c = sheet[1]
    rows_ = sheet.split('\n|' + first_c)
    rows = [rows_[0]] + ["|" + first_c + row for row in rows_[1:]]
    return rows

def get_table_nfs_input(prompt):
    if config.FMT1_TAG and config.FMT3_TAG:
        input_pattern = r'\nNFS_Input: (.*?)\nFormat_Input: ' 
    elif config.FMT3_TAG and not config.FMT1_TAG:
        input_pattern = r'\nNFS_Input: (.*?)\nFormat_Dict: '
    else: 
        input_pattern = r'\nNFS_Input: (.*?)\n\n###\n\n'
    matches = re.findall(input_pattern, prompt, re.DOTALL)
    sheet = matches[0].strip()     
    
    first_c = sheet[1]
    rows_ = sheet.split('\n|' + first_c)
    rows = [rows_[0]] + ["|" + first_c + row for row in rows_[1:]]
    return rows

def get_table_fmt_input(prompt):
    
    input_pattern = r'\nFormat_Dict: (.*?)\n\n###\n\n'  
      
    matches = re.findall(input_pattern, prompt, re.DOTALL)
    sheet = matches[0].strip()      

    return sheet

def parse_excel_row(row):
    cell_starts = [match.start() for match in re.finditer(r'\|[A-Z]+\d+,', row)]
    cells = []
    for i in range(len(cell_starts)):
        start = cell_starts[i] + 1
        end = cell_starts[i + 1] if i + 1 < len(cell_starts) else len(row)
        cells.append(row[start:end])
    return cells


def excel_address_to_coords(address):
    column_letters, row_numbers = '', ''
    for char in address:
        if char.isalpha():
            column_letters += char
        else:
            row_numbers += char

 
    row_index = int(row_numbers) - 1
    column_index = 0
    for i, letter in enumerate(reversed(column_letters)):
        column_index += (ord(letter.upper()) - 64) * (26 ** i)

    return (row_index, column_index - 1)


def col_num_to_letter(col_num):
 
    letter = ''
    while col_num > 0:
        col_num, remainder = divmod(col_num - 1, 26)
        letter = chr(remainder + ord('A')) + letter
    return letter

def col_letter_to_num(col_str):
 
    num = 0
    for c in col_str:
        num = num * 26 + (ord(c) - ord('A') + 1)
    return num

def tuple_to_excel_cell(coord):
    row, col = coord
    excel_col = ''
    while col >= 0:
        excel_col = chr(col % 26 + 65) + excel_col
        col = col // 26 - 1
    excel_row = str(row + 1)
    return excel_col + excel_row


def parse_excel_row_value(row):
    cell_starts = [match.start() for match in re.finditer(r'\|[A-Z]+\d+,', row)]
    cells = []
    for i in range(len(cell_starts)):
        start = cell_starts[i] + 1
        end = cell_starts[i + 1] if i + 1 < len(cell_starts) else len(row)
        cells.append(row[start:end])
    cell_values = []
    for cell in cells:
        cell_value = cell.split(",",1)[1].strip()   
        if all(char.isdigit() or char == ',' for char in cell_value):
            cell_value = cell_value.replace(',', '')
        cell_values.append(cell_value)
    return cell_values

def cal_tokens(input_file_path):
    
    enc = tiktoken.get_encoding("cl100k_base")
    length = 0
    with open(input_file_path,'r',encoding='utf-8') as input_file:
            for line in input_file:
                data = json.loads(line)
                length += len(enc.encode(data["messages"][0]["content"])) + len(enc.encode(data["messages"][1]["content"])) + len(enc.encode(data["messages"][2]["content"]))

    return length

def rows_to_new_input(txt_rows, nfs_rows = None, nfs_tag = False):
    if nfs_tag:
        if nfs_rows is None:
            raise ValueError("nfs_rows must be provided when nfs_tag is True.")
        else:
            input_change = "\nCell_Input: " + "\n".join(txt_rows) + "\n\nNFS_Input: " + "\n".join(nfs_rows) + "\n\n###\n\n"
            return input_change
    else:
        input_change = "\nInput: " + "\n".join(txt_rows) + "\n\n###\n\n"

def get_new_prompt(prompt, input_change):
    start1 = prompt.find("Instruction :")
    if start1 != -1:
        start2 = prompt.find("Input:")
        if start2 != -1:
            extracted_text = prompt[start1 + len("Instruction :"):start2].strip()

    prompt_change = "Instruction :" + extracted_text + input_change
    return prompt_change
```

## File: compress_aggregation/ValueAddress.py
```python
import json
import config
import tiktoken
import re
import openpyxl
from utils import get_table_input
from utils import parse_excel_row
from utils import col_num_to_letter
from utils import get_table_fmt_input



class ValueAddress:

    def __init__(self, input_file_path, output_file_path) -> None:
        self.input_file_path = input_file_path
        self.output_file_path = output_file_path
    

    @staticmethod
    def get_row_name(row_num, row_l) -> str:
        res = "["
        for i in range(0 + row_l, row_num + row_l):
            res += str(i+1) + ","
        res = res[:-1]
        res += "]"
        return res
    

    @staticmethod
    def get_col_name(col_num, col_l) -> str:
        res = "["
        for i in range(0 + col_l, col_num + col_l):
            res += str(col_num_to_letter(i+1) + ",")
        res = res[:-1]
        res += "]"
        return res


    def len_change(self, data):
        enc = tiktoken.get_encoding("cl100k_base")
        length = len(enc.encode(data["messages"][0]["content"])) + len(enc.encode(data["messages"][1]["content"])) #+ len(enc.encode(data["messages"][2]["content"]))
        if length <= 4096 - 250:
            len_type = "<4k"
        elif length > 4096 -250 and length <= 32768 - 250:
            len_type = "4-32k"
        else:
            len_type = ">32k"
        return len_type


    def process(self):

        with open(self.input_file_path, 'r', encoding='utf-8') as input_file:
                lines = input_file.readlines()
        new_lines = []
        
        for line in lines:
            data = json.loads(line)
            prompt = data["messages"][1]["content"]
            areas = data["areas1"]
            rows = get_table_input(prompt)
            if config.FMT3_TAG:
                fmt = get_table_fmt_input(prompt)
            else:
                fmt = None
            
            res = []
            input = [parse_excel_row(row) for row in rows]
            first_address = input[0][0].split(",", 1)[0]
            matches = re.match(r"([A-Z]+)(\d+)", first_address)
            uppercase_part = matches.group(1)
            numeric_part = matches.group(2)
            row_l = int(numeric_part) - 1
            col_l = openpyxl.utils.column_index_from_string(uppercase_part) - 1
            row_num = len(input)
            col_num = len(input[0])
            for area in areas:
                data_type = area[1]
                begin_address = area[0].split(":")[0]
                end_address = area[0].split(":")[1]
                if isinstance(data_type, int):
                    if data_type == 0:
                        area[1] = "YearData"
                
                    elif data_type == 1:
                        area[1] = "IntNum"
                    
                    elif data_type == 2:
                        area[1] = "FloatNum"
                
                    elif data_type == 3:
                        area[1] = "PercentageNum"
                
                    elif data_type == 4:
                        area[1] = "SentificNum"
                    elif data_type == 5:
                        area[1] = "DateData"
                
                    elif data_type == 6:
                        area[1] = "TimeData"
                
                    elif data_type == 7:
                        area[1] = "CurrencyData"
                
                    elif data_type == 8:
                        area[1] = "EmailData"


                if begin_address == end_address:
                    res.append((area[1], begin_address))
                else:
                    res.append((area[1], area[0]))

            new_input = ""
            for r in res:
                #without 2
                #new_input += "(" + r[1] + "|" + r[0] + ")" + ","
                
                #with 2
                new_input += "(" + r[0] + "|" + r[1] + ")" + ","
            new_input = new_input[:-1]

            description = "The spreadsheet has " + str(row_num) + " rows and " + str(col_num) + " columns. " + "Column names:" + self.get_col_name(col_num, col_l) + "; " + "Row numbers:" + self.get_row_name(row_num, row_l) 

            if config.FMT3_TAG:
                new_instructions = r"Instruction: Given an input that is a string denoting data of cells in a Excel spreadsheet. The input spreadsheet contains many tuples, describing the cells with content in the spreadsheet. Each tuple consists of two elements separated by a '|': the cell content and the cell address/region, like (Year|A1), ( |A1) or (IntNum|A1:B3). The content in some cells such as '#,##0'/'d-mmm-yy'/'H:mm:ss',etc., represents the CELL DATA FORMATS of Excel. The content in some cells such as 'IntNum'/'DateData'/'EmailData',etc., represents a category of data with the same format and similar semantics. For example, 'IntNum' represents integer type data, and 'ScientificNum' represents scientific notation type data. 'A1:B3' represents a region in spreadsheet, from the first row to the third row and from column A to column B. Some cells with empty content in the spreadsheet are not entered.  In addition, a dictionary will be provided to record the format information. The key of the dictionary is the format feature, and the value is the cell area with the feature: format_dict: {'Top Border': ['A13:D13', 'A14:D14'], 'Bottom Border': ['A12:D12', 'A13:D13', 'A26:D26'], 'Left Border': [], 'Right Border': ['A13:A26', 'B13:B26', 'C13:C26', 'D13:D26'], 'Fill Color': [], 'Font Bold': ['A1:F1']}. Cells with the same format may have similar semantic information and structural connections, which may help you to understand the table. Now you should tell me the range of the table in a format like A2:D5, and the range of table should only CONTAIN HEADER REGION and the data region, DON'T include the title or comments. Note that there can be more than one table in a string, so you should return all the RANGE, LIKE [{'range': 'A1:F9'}, {'range': 'A12:F18'}]. DON'T ADD OTHER WORDS OR EXPLANATION."
                new_prompt = new_instructions + "\nDescription: " + description + "\nInput: " + new_input + "\nFormat_Dict: " + fmt

            else:
                #1+3/3
                #new_instructions = r"Instruction: Given an input that is a string denoting data of cells in a Excel spreadsheet. The input spreadsheet contains many tuples, describing the cells with content in the spreadsheet. Each tuple consists of two elements separated by a '|': the cell address/region and the cell content , like (A1|Year), (A1| ) or (A1:B3|IntNum). The content in some cells such as '#,##0'/'d-mmm-yy'/'H:mm:ss',etc., represents the CELL DATA FORMATS of Excel. The content in some cells such as 'IntNum'/'DateData'/'EmailData',etc., represents a category of data with the same format and similar semantics. For example, 'IntNum' represents integer type data, and 'ScientificNum' represents scientific notation type data. 'A1:B3' represents a region in spreadsheet, from the first row to the third row and from column A to column B. Some cells with empty content in the spreadsheet are not entered. Now you should tell me the range of the table in a format like A2:D5, and the range of table should only CONTAIN HEADER REGION and the data region, DON'T include the title or comments. Note that there can be more than one table in a string, so you should return all the RANGE, LIKE [{'range': 'A1:F9'}, {'range': 'A12:F18'}]. DON'T ADD OTHER WORDS OR EXPLANATION."
                
                #2/1+2
                #new_instructions = r"Instruction: Given an input that is a string denoting data of cells in a Excel spreadsheet. The input spreadsheet contains many tuples, describing the cells with content in the spreadsheet. Each tuple consists of two elements separated by a '|': the cell address/region and the cell content , like (Year|A1), ( |A1) or (20|A1:B3). 'A1:B3' represents a region in spreadsheet, from the first row to the third row and from column A to column B. Some cells with empty content in the spreadsheet are not entered. Now you should tell me the range of the table in a format like A2:D5, and the range of table should only CONTAIN HEADER REGION and the data region, DON'T include the title or comments. Note that there can be more than one table in a string, so you should return all the RANGE, LIKE [{'range': 'A1:F9'}, {'range': 'A12:F18'}]. DON'T ADD OTHER WORDS OR EXPLANATION."
                
                #1+2+3/2+3    
                new_instructions = r"Instruction: Given an input that is a string denoting data of cells in a Excel spreadsheet. The input spreadsheet contains many tuples, describing the cells with content in the spreadsheet. Each tuple consists of two elements separated by a '|': the cell content and the cell address/region, like (Year|A1), ( |A1) or (IntNum|A1:B3). The content in some cells such as '#,##0'/'d-mmm-yy'/'H:mm:ss',etc., represents the CELL DATA FORMATS of Excel. The content in some cells such as 'IntNum'/'DateData'/'EmailData',etc., represents a category of data with the same format and similar semantics. For example, 'IntNum' represents integer type data, and 'ScientificNum' represents scientific notation type data. 'A1:B3' represents a region in spreadsheet, from the first row to the third row and from column A to column B. Some cells with empty content in the spreadsheet are not entered. Now you should tell me the range of the table in a format like A2:D5, and the range of table should only CONTAIN HEADER REGION and the data region, DON'T include the title or comments. Note that there can be more than one table in a string, so you should return all the RANGE, LIKE [{'range': 'A1:F9'}, {'range': 'A12:F18'}]. DON'T ADD OTHER WORDS OR EXPLANATION."
                
        

                new_prompt = new_instructions + "\nDescription: " + description + "\nInput: " + new_input

            data["messages"][1]["content"] = new_prompt

            del data["areas"]
            del data["areas1"]

            data["now_length"] = self.len_change(data)
            new_lines.append(json.dumps(data, ensure_ascii=False) + '\n')

        with open(self.output_file_path, 'w', encoding='utf-8') as output_file:
            output_file.writelines(new_lines)


if __name__ == '__main__':
    valueaddress = ValueAddress(config.VA_INPUT_FILE_PATH, 
                                config.VA_OUTPUT_FILE_PATH
                                )
    valueaddress.process()
```

## File: compress_struture/Compress_structure.py
```python
import openpyxl
import json
import re
import tiktoken
import config
from utils import parse_excel_row
from utils import get_table_nfs_input
from utils import get_table_cell_input
from utils import col_num_to_letter
from utils import rows_to_new_input



class MyCustomException(Exception):
    pass

def find_boundary(file_name):
    res = []
    with open(config.FIND_FILE_PATH,'r',encoding='utf-8') as find_file:
        for line in find_file:
            data = json.loads(line)
            if data["file_name"] == file_name:
                return data["boundarys"]
        
        return res

def row_delete(rows,row_boundarys):
    first = rows[0].split("|")[1:][0].split(",")[0]

    pattern = r"([A-Z]+)(\d+)"
    matches = re.match(pattern, first)

    if matches:
        numeric_part = matches.group(2)

    else:
        print("chucuol!!!!!!!")
    l = int(numeric_part) - 1  

    tag = [0]*(len(rows)+1) 
    for rb in row_boundarys:
        rb = rb - l
        if rb > len(rows):  rb = len(rows)
        tag[rb] = 1
        if (rb-config.DELTA)>=1 and (rb+config.DELTA)<=(len(rows)):   
            for i in range(rb-config.DELTA,rb+config.DELTA+1):
                tag[i] = 1
        if (rb-config.DELTA)<1 and (rb+config.DELTA)<=(len(rows)):   
            for i in range(1,rb+config.DELTA+1):
                tag[i] = 1
        if (rb-config.DELTA)>=1 and (rb+config.DELTA)>(len(rows)):    
            for i in range(rb-config.DELTA,len(rows)+1):
                tag[i] = 1
        if (rb-config.DELTA)<1 and (rb+config.DELTA)>(len(rows)):
            for i in range(1,len(rows)+1):
                tag[i] = 1
    res = []

    for j in range(0,len(rows)):
        if(tag[j+1]==1):
            res.append(rows[j])
    return res

def col_delete(rows,col_boundarys):
    
    first = rows[0].split("|")[1:][0].split(",")[0]

    patternpp = r"([A-Z]+)(\d+)"
    matches = re.match(patternpp, first)
    if matches:
        uppercase_part = matches.group(1)
        
    else:
        print("chucuol!!!!!!!!!!!!!!!")
    l = openpyxl.utils.column_index_from_string(uppercase_part) - 1
  
    width = len(parse_excel_row(rows[0]))
    
    tag = [0]*(width+1)     
    for cb in col_boundarys:
        cb = cb - l
        if cb > width:    cb = width
        tag[cb] = 1
        if(cb-config.DELTA)>=1 and (cb+config.DELTA)<=width:
            for i in range(cb-config.DELTA, cb+config.DELTA+1):
                tag[i] = 1
        if(cb-config.DELTA)<1 and (cb+config.DELTA)<=width:
            for i in range(1, cb+config.DELTA+1):
                tag[i] = 1
        if(cb-config.DELTA)>=1 and (cb+config.DELTA)>width:
            for i in range(cb-config.DELTA, width+1):
                tag[i] = 1
        if(cb-config.DELTA)<1 and (cb+config.DELTA)>width:
            for i in range(1, width+1):
                tag[i] = 1
    res = []
    for row in rows:
        r = parse_excel_row(row)
        row_res = ""
        
        for j in range(0,len(r)):
            if(tag[j+1]==1):
                row_res += "|" + r[j]
        res.append(row_res)
    return res
        
def txt_compress(prompt, row_boundarys, col_boundarys):
    
    cell_rows = get_table_cell_input(prompt)

    result = row_delete(cell_rows,row_boundarys)
    result = col_delete(result,col_boundarys)
    
    return result

def nfs_compress(prompt, row_boundarys, col_boundarys):
    
    nfs_rows = get_table_nfs_input(prompt)

    result = row_delete(nfs_rows,row_boundarys)
    result = col_delete(result,col_boundarys)
    
    return result

def get_row_tag(rows):
    
    def check_row_space(row):
        cells = parse_excel_row(row)
        
        for cell in cells:
            cell_value = cell.split(",",1)[1]
            if cell_value != "":
                return False
        return True  
    
    tag = [0]*len(rows)
    for i in range(0,len(rows)):
        if check_row_space(rows[i]):
            tag[i] = 1 
            
    return tag

def get_col_tag(rows):

    def check_col_space(input):
        tag = [1] * len(input[0])
        for j in range(0,len(input[0])):
            for i in range(0,len(input)):
                if input[i][j] != " ":
                    tag[j] = 0
        return tag
    
    input = []
    for row in rows:
        cells = parse_excel_row(row)
        new_cells = []
        
        for cell in cells:
            new_cells.append(cell.split(",",1)[1])
        
        input.append(new_cells)
    return check_col_space(input)

def find_consecutive_ones_intervals(nums):
        
        intervals = []
        
        start = None
        count = 0
        for i, num in enumerate(nums):
         
            if num == 1:
              
                if start is None:
                    start = i
                
                count += 1
            
            if num != 1 or i == len(nums) - 1:
                
                if count >= 2:
                    
                    end = i if num != 1 else i + 1
                    intervals.append((start, end - 1))
                
                start = None
                count = 0
        return intervals

def coordinate_rearrangement(rows):
    new_rows = []
    my_dic_r = { }
    my_dic = { }
    for i in range(0,len(rows)):
        
        row_list = parse_excel_row(rows[i])
        
        new_row_list = []

        for j in range(0,len(row_list)):
            
            cell = row_list[j].split(',',1)
            cell_address = cell[0]
            cell_value = cell[1]

            number = str(i+1)
           
            after_address = col_num_to_letter(j+1) + number
           
            before_address = cell_address
            my_dic[after_address] = before_address
            my_dic_r[before_address] = after_address
            new_cell = after_address +"," + cell_value
            new_row_list.append(new_cell)
        
        s = ""
        for n in new_row_list:
            s += "|" + n
        new_rows.append(s)
    
    return new_rows, my_dic, my_dic_r

def groundtruth_coordinate_rearrangement(my_dic_r, labels):
    # pattern = r"'(.*?)'"
    # matches = re.findall(pattern, labels)
    # for i in range(0,len(matches)):
    #     if i%2 != 0:
    #         add = matches[i]
    #         adds = add.split(":")
    #         new_add = my_dic_r[adds[0]] + ":" + my_dic_r[adds[1]] 
    #         matches[i] = new_add
    # j = 0
    # ans = ""
    # while(j<len(matches)):
    #     ans += "{'" + matches[j] + "'" + ":" + "'" + matches[j+1] + "'}" + ", "
    #     j += 2
    # new_labels = "[" + ans[:-2] + "]"
    # return new_labels
    for label in labels:
        for i in range(0,len(label)):
            adds = label[i].split(":")
            new_add = my_dic_r[adds[0]] + ":" + my_dic_r[adds[1]] 
            label[i] = new_add
    return labels

def delete_space(txt_rows, nfs_rows):

    row_tag = get_row_tag(txt_rows)
    row_intervals = find_consecutive_ones_intervals(row_tag)
    for row_interval in row_intervals:
            if row_interval[0] == 0 or row_interval[1] == len(row_tag)-1:   continue
            else: 
                
                row_tag[row_interval[0]] = 2
                row_tag[row_interval[1]] = 2


    col_tag = get_col_tag(txt_rows)
    col_intervals = find_consecutive_ones_intervals(col_tag)
    for col_interval in col_intervals:
        if col_interval[0] == 0 or col_interval[1] == len(col_tag) - 1: continue
        else:
            
            col_tag[col_interval[0]] = 2
            col_tag[col_interval[1]] = 2

    new_cell_rows = []
    for i in range(0, len(txt_rows)):
        if row_tag[i]!=1:
            new_cell_rows.append(txt_rows[i])
    
    new_nfs_rows=[] 
    for j in range(0, len(nfs_rows)):
        if row_tag[j]!=1:
            new_nfs_rows.append(nfs_rows[j])


    return new_cell_rows, new_nfs_rows

def change_length(data):
    enc = tiktoken.get_encoding("cl100k_base")
    length = 0
    length += len(enc.encode(data["messages"][0]["content"])) + len(enc.encode(data["messages"][1]["content"])) 
    if length < 4096 - 250:
        return "<4k"
    elif length >= 4096 - 250 and length < 32768 - 250:
        return "4-32k"
    elif length >= 32768 -250:
        return "32k"     

def compress_layout(input_file_path,output_file_path,mapping_file_path): 

    count = 0
    with open(input_file_path,'r',encoding='utf-8') as input_file, open(output_file_path,'w',encoding='utf-8') as output_file, open(mapping_file_path,'w',encoding='utf-8') as mapping_file:
        for line in input_file:
            try:
                data = json.loads(line)
                prompt = data["messages"][1]["content"]
                file_name = data["file_name"]
                labels = data["messages"][2]["content"]
                row_boundary = []
                col_boundary = []

                
                adds_pattern = r"([A-Z]+)(\d+)"
                for label in labels:
                    for i in range(0,len(label)):
                        adds = label[i].split(":")
                        adds_matches0 = re.match(adds_pattern, adds[0])
                        if adds_matches0:
                            row_boundary.append(int(adds_matches0.group(2)))
                            col_boundary.append(openpyxl.utils.column_index_from_string(adds_matches0.group(1)))
                        adds_matches1 = re.match(adds_pattern, adds[1])
                        if adds_matches1:
                            row_boundary.append(int(adds_matches1.group(2)))
                            col_boundary.append(openpyxl.utils.column_index_from_string(adds_matches1.group(1)))
                
                # adds_pattern = r"([A-Z]+)(\d+)"
                # lebels_pattern = r"'(.*?)'"
                # labels_matches = re.findall(lebels_pattern, labels)

                # for i in range(0,len(labels_matches)):
                #     if i%2 != 0:
                #         add = labels_matches[i]
                #         adds = add.split(":")
                #         adds_matches0 = re.match(adds_pattern, adds[0])
                #         if adds_matches0:
                #             row_boundary.append(int(adds_matches0.group(2)))
                #             col_boundary.append(openpyxl.utils.column_index_from_string(adds_matches0.group(1)))
                #         adds_matches1 = re.match(adds_pattern, adds[1])
                #         if adds_matches1:
                #             row_boundary.append(int(adds_matches1.group(2)))
                #             col_boundary.append(openpyxl.utils.column_index_from_string(adds_matches1.group(1)))

               
                boundary_list = find_boundary(file_name)
                if len(boundary_list) == 0:
                    print("!!!!!!!!!!!!")
                    output_file.write(json.dumps(data) + '\n')
                else:
                    if len(boundary_list) != 0:
                    
                        for boundary in boundary_list:
                            list = boundary.split(",")
                            row_boundary.append(int(list[0].strip()))
                            row_boundary.append(int(list[1].strip()))
                            col_boundary.append(int(list[2].strip()))
                            col_boundary.append(int(list[3].strip()))
                    row_boundarys = sorted(set(row_boundary))
                    col_boundarys = sorted(set(col_boundary))
                    
                    txt_res = txt_compress(prompt, row_boundarys, col_boundarys)
                    nfs_res = nfs_compress(prompt, row_boundarys, col_boundarys)
                 
                    txt_res, nfs_res = delete_space(txt_res, nfs_res)
                    if len(txt_res) != len(nfs_res) :
                        raise MyCustomException("1条件满足，抛出异常")
                
                    new_txt_rows, my_dic, my_dic_r = coordinate_rearrangement(txt_res)
                    new_nfs_rows, _, _, = coordinate_rearrangement(nfs_res)
                    if len(new_txt_rows) != len(new_nfs_rows) :
                        raise MyCustomException("2条件满足，抛出异常")
                    prompt_change = rows_to_new_input(new_txt_rows, new_nfs_rows)                
                    new_labels = groundtruth_coordinate_rearrangement(my_dic_r,labels) 
                    data["messages"][1]["content"] = prompt_change
                    data["messages"][2]['content'] = new_labels
                    data["now_length"] = change_length(data)
                    output_file.write(json.dumps(data) + '\n')
                    count+=1
                    print(count)
                    item = {
                    "file_name": file_name,
                    "reflection": my_dic,
                    "reflection_r": my_dic_r,
                    }   
                    mapping_file.write(json.dumps(item) + '\n')
            except json.JSONDecodeError as e:
                print(f"Error decoding JSON: {str(e)}")
                print(file_name)
                continue
            except Exception as e:
                print(f"Error processing data: {str(e)}")
                print(file_name)
                continue

if __name__ == "__main__":
    
    compress_layout(config.INPUT_FILE_PATH,
                    config.OUTPUT_FILE_PATH,
                    config.MAPPING_FILE_PATH
                    )
```

## File: compress_struture/config.py
```python
DATA_PRE_PATH = r""


HEURISTIC_OUTPUT_PATH = r""

TXT_FILE_PATH = ""


JSONL_FILE_PATH = ""


INPUT_FILE_PATH = ""


FIND_FILE_PATH = ""


OUTPUT_FILE_PATH = ""


MAPPING_FILE_PATH = ""


DELTA = 4


FMT1_TAG = False


FMT3_TAG = False


FMT_TAG = False
```

## File: compress_struture/utils.py
```python
import re
import json
import tiktoken
import config



def get_table_input(prompt):
  
    pattern = r'(?<=Input: ).+?(?=\n\n###\n\n)'
    matches = re.findall(pattern, prompt, re.DOTALL)
    sheet = matches[0].strip()

    first_c = sheet[1]
    rows_ = sheet.split('\n|'+first_c)

    rows = [rows_[0]]
    for k in range(1,len(rows_)):
        rows.append("|"+ first_c+rows_[k])
    
    return rows

def get_table_cell_input(prompt):
    input_pattern = r'\nCell_Input: (.*?)\nNFS_Input:'    
    matches = re.findall(input_pattern, prompt, re.DOTALL)
    
    sheet = matches[0].strip()     
    first_c = sheet[1]
    rows_ = sheet.split('\n|' + first_c)
    rows = [rows_[0]] + ["|" + first_c + row for row in rows_[1:]]
    return rows

def get_table_nfs_input(prompt):
    if config.FMT1_TAG:
        input_pattern = r'\nNFS_Input: (.*?)\nFormat_Input: '
    elif config.FMT3_TAG:
        input_pattern = r'\nNFS_Input: (.*?)\nFormat_Dict: '
    else:
        input_pattern = r'(?<=NFS_Input: ).+?(?=\n\n###\n\n)'    
    matches = re.findall(input_pattern, prompt, re.DOTALL)

    
    sheet = matches[0].strip()     
    first_c = sheet[1]
    rows_ = sheet.split('\n|' + first_c)
    rows = [rows_[0]] + ["|" + first_c + row for row in rows_[1:]]
    return rows

def get_table_fmt_input(prompt):
    if config.FMT3_TAG:
        fmt_pattern = r'\nFormat_Input: (.*?)\nFormat_Dict: '
    else:
        fmt_pattern = r'(?<=Format_Input: ).+?(?=\n\n###\n\n)'
    sheet = re.findall(fmt_pattern, prompt, re.DOTALL)[0].strip()
    first_c = sheet[1]
    rows_ = sheet.split('\n|' + first_c)
    rows = [rows_[0]] + ["|" + first_c + row for row in rows_[1:]]
    return rows

def parse_excel_row(row):
    cell_starts = [match.start() for match in re.finditer(r'\|[A-Z]+\d+,', row)]
    cells = []
    for i in range(len(cell_starts)):
        start = cell_starts[i] + 1
        end = cell_starts[i + 1] if i + 1 < len(cell_starts) else len(row)
        cells.append(row[start:end])
    return cells


def excel_address_to_coords(address):
    column_letters, row_numbers = '', ''
    for char in address:
        if char.isalpha():
            column_letters += char
        else:
            row_numbers += char

 
    row_index = int(row_numbers) - 1

  
    column_index = 0
    for i, letter in enumerate(reversed(column_letters)):
        column_index += (ord(letter.upper()) - 64) * (26 ** i)

    return (row_index, column_index - 1)


def col_num_to_letter(col_num):
   
    letter = ''
    while col_num > 0:
        col_num, remainder = divmod(col_num - 1, 26)
        letter = chr(remainder + ord('A')) + letter
    return letter

def col_letter_to_num(col_str):
    
    num = 0
    for c in col_str:
        num = num * 26 + (ord(c) - ord('A') + 1)
    return num


def tuple_to_excel_cell(coord):
    row, col = coord
    excel_col = ''
    while col >= 0:
        excel_col = chr(col % 26 + 65) + excel_col
        col = col // 26 - 1
    excel_row = str(row + 1)
    return excel_col + excel_row

def parse_excel_row_value(row):
    cell_starts = [match.start() for match in re.finditer(r'\|[A-Z]+\d+,', row)]
    cells = []
    for i in range(len(cell_starts)):
        start = cell_starts[i] + 1
        end = cell_starts[i + 1] if i + 1 < len(cell_starts) else len(row)
        cells.append(row[start:end])
    cell_values = []
    for cell in cells:
        cell_value = cell.split(",",1)[1].strip()  
        if all(char.isdigit() or char == ',' for char in cell_value):
            cell_value = cell_value.replace(',', '')
        cell_values.append(cell_value)
    return cell_values

def cal_tokens(input_file_path):
    
    enc = tiktoken.get_encoding("cl100k_base")
    length = 0
    with open(input_file_path,'r',encoding='utf-8') as input_file:
            for line in input_file:
                data = json.loads(line)
                length += len(enc.encode(data["messages"][0]["content"])) + len(enc.encode(data["messages"][1]["content"])) + len(enc.encode(data["messages"][2]["content"]))

    return length

def rows_to_new_input(txt_rows, nfs_rows):
    input_change = "\nCell_Input: " + "\n".join(txt_rows) + "\n\nNFS_Input: " + "\n".join(nfs_rows) + "\n\n###\n\n"
    return input_change

def rows_to_new_input_fmt(txt_rows, fmt_rows, nfs_rows):
    input_change = "\nCell_Input: " + "\n".join(txt_rows) + "\n\nNFS_Input: " + "\n".join(nfs_rows) + "\n\nFormat_Input: " +"\n".join(fmt_rows)+"\n\n###\n\n"
    return input_change


def get_new_prompt(prompt, input_change):
    start1 = prompt.find("Instruction :")
    if start1 != -1:
        start2 = prompt.find("Input:")
        if start2 != -1:
            extracted_text = prompt[start1 + len("Instruction :"):start2].strip()

    prompt_change = "Instruction :" + extracted_text + input_change
    return prompt_change
```
