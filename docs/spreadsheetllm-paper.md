# SpreadsheetLLM: Encoding Spreadsheets for Large Language Models

**Authors**:  
Yuzhang Tian\(^\dagger\)\(^\dagger\)\(^\text{equal contribution}\), Jianbo Zhao\(^{1,2}\), Haoyu Dong\(^\dagger\)\(^\text{corresponding author}\), Junyu Xiong\(^\dagger\), Shiyu Xia\(^\dagger\),  
Mengyu Zhou, Yun Lin\(^2\), José Cambronero, Yeye He, Shi Han, Dongmei Zhang  
Microsoft Corporation  

---

## Abstract

Spreadsheets are characterized by their extensive two-dimensional grids, flexible layouts, and varied formatting options, which pose significant challenges for large language models (LLMs). In response, we introduce **SpreadsheetLLM**, pioneering an efficient encoding method designed to unleash and optimize LLMs’ powerful understanding and reasoning capability on spreadsheets.

Initially, we propose a **vanilla serialization** approach that incorporates cell addresses, values, and formats. However, this approach was limited by LLMs’ token constraints, making it impractical for most applications. To tackle this challenge, we develop **SheetCompressor**, an innovative encoding framework that compresses spreadsheets effectively for LLMs. It comprises three modules:

1. **Structural-anchor-based compression**  
2. **Inverse index translation**  
3. **Data-format-aware aggregation**  

It significantly improves performance in spreadsheet table detection tasks, outperforming the vanilla approach by 25.6% in GPT4’s in-context learning setting. Moreover, a fine-tuned LLM with SheetCompressor has an average compression ratio of 25×, yet achieves a state-of-the-art 78.9% F1 score—surpassing the best existing models by 12.3%.

Finally, we propose a **Chain of Spreadsheet** for downstream tasks of spreadsheet understanding and validate it in a new and demanding **spreadsheet QA** task. We methodically leverage the inherent layout and structure of spreadsheets, demonstrating that SpreadsheetLLM is highly effective across a variety of spreadsheet tasks.

---

## 1. Introduction

Spreadsheets are ubiquitous for data management and widely used within platforms like Microsoft Excel and Google Sheets. Understanding spreadsheet layout and structure
<sup>[Dong et al. (2019b); Gol et al. (2019); Hulsebos et al. (2019); Dou et al. (2018); Wang et al. (2021); Deng et al. (2022); Chen and Cafarella (2014)]</sup>,
a longstanding challenge for traditional models, is crucial for effective data analysis and intelligent user interaction.

Recently, the rapid development of **large language models (LLMs)** has opened new frontiers in table processing
<sup>[Li et al. (2023b)]</sup>
and reasoning
<sup>[Cheng et al. (2022)]</sup>.
However, spreadsheets pose unique challenges for LLMs due to their expansive grids that usually exceed the token limitations of popular LLMs, as well as their inherent two-dimensional layouts and structures—poorly suited to linear and sequential input. Furthermore, LLMs often struggle with spreadsheet-specific features such as cell addresses and formats, complicating their ability to effectively parse and utilize spreadsheet data (see Appendix A).

In this paper, we introduce **SpreadsheetLLM**, a pioneering framework to unleash and maximize the potential of LLMs for spreadsheet understanding and reasoning. We initially propose a **vanilla encoding method** to serialize spreadsheets into sequences, augmenting the Markdown encoding method by including essential cell addresses (and optionally, formats). However, large spreadsheets that exceed LLM token limits not only limit processing capacity but—as observed in prior studies—degrade accuracy performance as the size increases
<sup>[Liu et al. (2024)]</sup>.

To address this challenge, we propose **SheetCompressor**, featuring a novel encoding framework comprising three portable modules:

1. **Structural Anchors for Efficient Layout Understanding**.  
   Observations indicate that large spreadsheets often contain numerous homogeneous rows or columns, which contribute minimally to understanding the layout and structure (see left panel in Figure&nbsp;2(a)). To address this, we identify *structural anchors*—heterogeneous rows and columns at possible table boundaries that offer substantial layout insights, as depicted in Figure&nbsp;2(b). We remove distant, homogeneous rows and columns, producing a condensed “skeleton” version of the spreadsheet, as illustrated in Figure&nbsp;2(c).

2. **Inverted-Index Translation for Token Efficiency**.  
   A vanilla encoding method can be extremely token-consuming when handling spreadsheets with many empty cells and repetitive values (see Figure&nbsp;2(c)). To improve efficiency, we depart from traditional row-by-row serialization and employ a **lossless inverted-index translation** in JSON format. This creates a dictionary indexing non-empty cell texts, merging addresses with identical text, thus optimizing token usage while preserving data integrity.

3. **Data Format Aggregation for Numerical Cells**.  
   Adjacent numerical cells often share similar number formats. Recognizing that **exact** numerical values are less crucial for grasping spreadsheet structure, we extract number format strings and data types from these cells. Then, adjacent cells with the same formats or types are clustered. This is visualized in the right example of Figure&nbsp;2, where rectangular regions are replaced by uniform format strings and data types—streamlining numerical data representation without excessive token expenditure.

We conducted a comprehensive evaluation on a variety of LLMs. Our experiments show that SheetCompressor significantly reduces token usage by **96%**. Moreover, **SpreadsheetLLM** significantly improves spreadsheet table detection accuracy, surpassing a previous SOTA method by **12.3%**. We also applied SpreadsheetLLM to a representative spreadsheet QA task. Inspired by the Chain of Thought (CoT) methodology
<sup>[Zheng et al. (2023); Jiang et al. (2023b)]</sup>,
we propose **Chain of Spreadsheet (CoS)**, which decomposes spreadsheet reasoning into a table detection–match–reasoning pipeline. It significantly outperforms existing SOTA methods for table QA
<sup>[Herzig et al. (2020); Cheng et al. (2022)]</sup>.

**Our primary contributions**:

- We propose **SpreadsheetLLM**, the first work that substantially leverages LLMs for understanding and analyzing spreadsheet data. To address the challenges in scale, diversity, and complexity of spreadsheets, we propose **SheetCompressor**, an innovative encoding framework to compress spreadsheets for LLMs with efficient encoding.
- We fine-tune a variety of cutting-edge LLMs to achieve optimal performance on spreadsheet table detection, demonstrating the high effectiveness of SpreadsheetLLM in accurately understanding complex spreadsheet layouts and structures.
- We extend the horizontal capabilities of SpreadsheetLLM to a wide range of downstream tasks by proposing **CoS** and verifying it on **Spreadsheet QA**, highlighting its potential for intelligent user interaction.

---

>The description of Figure 2 in the paper presents the SheetCompressor framework, which has three main components for compressing spreadsheet data:
>
> Figure 2 illustrates the SheetCompressor framework through a visual example with three sequential steps. It shows how a large, complex spreadsheet is progressively compressed through different techniques. The original spreadsheet contains two distinct tables with numerous data entries and hierarchical headers. This complete spreadsheet is quite large - 576 rows by 23 columns - which would require 61,240 tokens with vanilla encoding.
>
> The framework applies three compression techniques in sequence:
>1. Structural-anchor-based Extraction: The first technique identifies important structural elements (like headers and table boundaries) and extracts only these key components, rearranging them into a much smaller 24×8 sheet. This preserves the meaningful structure while dramatically reducing size.
>2. Inverted-index Translation: The second technique transforms the data representation by inverting the traditional row-column encoding. Rather than listing each cell with its address, it creates a dictionary where values point to their locations, which efficiently handles empty cells and repeated values.
>3. Data-format-aware Aggregation: The final technique recognizes patterns in data formats (like dates, numbers, percentages) and groups cells with similar formats together, replacing specific values with format descriptors.
>
>The result is an extremely compressed representation that contains only 708 tokens - achieving a compression ratio of about 86× while preserving the essential structural and semantic information needed for LLM understanding.
---

## 2. Related Work

### Spreadsheet Representation

Spreadsheet representation involves converting spreadsheets into specific formats for modeling. Various methods exist for table representation:
- **Visual / spatial models** <sup>[Dong et al. (2019a, 2019b); Deng et al. (2024)]</sup>.
- **Sequence-based models (LSTMs)** <sup>[Nishida et al. (2017); Gol et al. (2019)]</sup>.
- **Pre-trained LMs** <sup>[Dong et al. (2022); Wang et al. (2021)]</sup> specifically for spreadsheet data <sup>[Zhang et al. (2023); Li et al. (2023b); Sui et al. (2023)]</sup>.

However, these methods (Markdown, HTML) do not adapt well to spreadsheets with *multiple tables* and complex formatting; see Appendix B.

### Spreadsheet Understanding

While most table-LLMs are restricted to single-table settings, spreadsheets with multiple tables often exceed token limits. The diversity in multi-table layout and structure further complicates the problem. **Spreadsheet table detection**
<sup>[Dong et al. (2019b); Christodoulakis et al. (2020); Doush and Pontelli (2010); Vitagliano et al. (2022)]</sup>
aims to identify all tables on a given sheet and determine their respective ranges. As a fundamental task, it sees hundreds of millions of uses daily
<sup>[Zhang et al. (2024)]</sup>, yet still demands accuracy improvements given spreadsheets’ flexibility and complexity.

### Spreadsheet Downstream Tasks

Spreadsheet understanding also enables tasks such as **table question answering**
<sup>[He et al. (2024); Cheng et al. (2021b, 2022); Jiang et al. (2022); Herzig et al. (2020)]</sup>,  
**table extraction**
<sup>[Chen and Cafarella (2013, 2014); Li et al. (2024)]</sup>,  
**formula/code generation**
<sup>[Chen et al. (2021, 2024); Cheng et al. (2021a); Joshi et al. (2024); Li et al. (2023a)]</sup>,  
and **error detection**
<sup>[Wang and He (2019); Dou et al. (2016)]</sup>.  

In this paper, we choose **spreadsheet QA** as a representative. It extends “table QA” to multi-table spreadsheets.

### LLMs’ Token Efficiency

LLMs degrade significantly with long contexts
<sup>[Liu et al. (2024); Xu et al. (2023)]</sup>, driving methods to reduce token usage (e.g., **DBCopilot** <sup>[Wang et al. (2023)]</sup>). However, purely “SQL-like” solutions or naive text compression can lose essential structure. In contrast, our **SheetCompressor** specifically addresses multi-table layout, preserving structural cues for LLM interpretation.

---

## 3. Method

We propose a novel spreadsheet encoding framework in a Markdown-like style. To achieve a more compact and efficient representation, we introduce three **independent** yet **combinable** modules:

1. **Structural-anchor-based extraction**  
2. **Inverted-index translation**  
3. **Data-format-aware aggregation**

These enable efficient data compression and enhance performance on downstream tasks.

### 3.1. Vanilla Spreadsheet Encoding

<small>**(Referencing from “From Section 3.1 - Vanilla Spreadsheet Encoding with Cell Value, Address, and Format”)**</small>

> The paper defines the spreadsheet representation as:
>
> $$
\mathcal{S} = \{\,Cell_{i,j}\}_{i \in m, j \in n}
\quad\text{and}\quad
\mathcal{T} = \mathrm{markdown}\{\mathrm{encode}(Cell_{i,j})\}
:= |Address_{i,j}, Value_{i,j}, Format|...
$$
>
> Where \(\mathcal{S} \in \mathbb{R}^{m,n}\) denotes the spreadsheet, \(\mathcal{T} \in \mathbb{R}^1\) denotes the text representation, and \(i, j, m, n\) respectively are the row index, column index, and row/column ranges.

Due to the absence of standardized practices in spreadsheet encoding for LLMs, we explore a **Markdown-like** style representation:

```text
|Addressᵢⱼ, Valueᵢⱼ, Format| ... \n
```

Here, the spreadsheet \(\mathcal{S}\) is a 2D grid \(\in \mathbb{R}^{m,n}\), and \(\mathcal{T}\in\mathbb{R}^1\) is the textual serialization. Including cell format information (e.g., background color, bold font, borders) often rapidly exceeds token limits, and LLMs struggle with it effectively (Appendix A).

### 3.2. Structural-Anchor-Based Extraction

<small>**(Referencing from “From Section 3.2 - Structural-anchor-based Extraction”)**</small>

Large spreadsheets often have numerous homogeneous rows/columns that minimally contribute to layout understanding. We propose a *heuristic-based* method to identify “structural anchors”: **heterogeneous rows/columns** that mark potential table boundaries. Mathematically:

$$
\mathcal{A} = \{\,r_p,\,c_q\}_{p\in m,q\in n}, \quad
r_p = \{Cell_{i,j}\}_{i=p, j \in n}, \quad
c_q = \{Cell_{i,j}\}_{i\in m, j=q}.
$$

We keep rows/columns within a threshold \(k\) of these anchors (Appendix C). Formally,

$$
r_{p_+} = \{Cell_{i,j}\}_{|i - p|\le k,\,j\in n},
\quad
c_{q_+} = \{Cell_{i,j}\}_{i\in m,\,|j - q|\le k}.
$$

Then we form the extracted compact spreadsheet:

$$
\mathcal{S}_e = \mathrm{extract}(\mathcal{S})
= \mathrm{address\_map}(r_{p_+}\,\cap\,c_{q_+}).
$$

Finally, we **remap coordinates** to maintain continuity. This procedure discards large blocks of trivial/homogeneous data but preserves essential structural rows/columns (over 97% coverage when \(k=4\), see Appendix D).

### 3.3. Inverted-Index Translation

<small>**(Referencing from “From Section 3.3 - Inverted-index Translation”)**</small>

Traditional row-by-row encoding is token-consuming for large or sparse spreadsheets. We propose a **two-stage inverted index** approach:

1. **Dictionary Conversion**. Convert from matrix-style encoding into a dictionary \(\{\, Value : Addresses \}\).
2. **Merging Identical Values**. Cells sharing the same value are merged; empty cells are excluded.

Formally:

$$
\mathcal{T}_t = \mathrm{invert}(\mathcal{T})
:= \{\,Value : Address\text{ or }Address\_Region,\dots\}.
$$

This is **lossless** compression, drastically reducing redundancy. In Table 1 (Section 4.2.1), we show a 14.91× ratio improvement in our dataset.

### 3.4. Data-Format-Aware Aggregation

<small>**(Referencing from “From Section 3.4 - Data-format-aware Aggregation”)**</small>

In spreadsheets, many cells share data formats (e.g., “yyyy-mm-dd,” “#,##0,” etc.). We exploit **Number Format Strings (NFS)**—built-in attributes. If no NFS is present, we fall back on a rule-based recognizer (e.g., integer, float, date, currency). Then we **cluster** adjacent cells with the same data format or type, summarizing them under a single key. Formally:

$$
NFSs = \mathrm{nfs}(\{Cell_{i,j}\}), \quad
\mathcal{T}_a = \mathrm{aggregator}(\{Cell_{i,j}\},\, NFSs,\, R).
$$

Here, $R$ is a predefined rule set for typed data (Algorithm 1 in Appendix M). This final step can push compression to **24.79×**.

### 3.5. Chain of Spreadsheet

We further extend to **downstream tasks** via a **Chain of Spreadsheet (CoS)**:

1. **Table Identification & Boundary Detection**: The compressed spreadsheet + query are fed to the LLM, which identifies relevant table(s) and boundaries.
2. **Response Generation**: The query + extracted region are re-input to the LLM to generate the final answer.

In Section 4.2, we validate this CoS approach on a **Spreadsheet QA** task, demonstrating strong performance.

---

## 4. Experiments

We evaluate our method first on **spreadsheet table detection**—a foundational task that underscores the ability to interpret spreadsheet structure. We then explore a **spreadsheet QA** task, testing the model’s end-to-end comprehension and question-answering abilities.

### 4.1. Spreadsheet Table Detection

#### 4.1.1. Dataset

We use the dataset from <sup>[Dong et al. (2019b)]</sup>, with real-world spreadsheets and annotated table boundaries. We further improved the test set labeling quality with five human experts (Appendix E), yielding **188 spreadsheets** with **311 tables**. We partition them into categories **Small** (<4k tokens), **Medium** (4–8k), **Large** (8–32k), and **Huge** (≥32k). We evaluate with the **EoB-0** (Error-of-Boundary 0) metric, requiring exact top/left/bottom/right matches.

#### 4.1.2. Experiment Setup

**Baseline & Metrics.** We compare to **TableSense-CNN** <sup>[Dong et al. (2019b)]</sup>, using **F1** on the EoB-0 metric.

**Model Selection.** We test both closed-source (GPT4, GPT3.5) and open-source (Llama2, Llama3, Phi3, Mistral-v2) models, as described in Appendix G.

### 4.2. Spreadsheet QA

#### 4.2.1. Dataset

Existing Table QA focuses on single tables, ignoring multi-table complexity. We built a new **Spreadsheet QA** dataset with 64 spreadsheets, each containing 4–6 questions, totaling **307 items**. Questions focus on fundamental search, comparison, and arithmetic. Answers are a specific address or formula referencing addresses. Details in Appendix H.

**Table 1: Average Compression Ratio on the test sets**. (See also Appendix J.1 for train/valid details.)

#### 4.2.2. Experiment Setup

**Baseline & Metrics.** No direct prior LLMs for Spreadsheet QA exist, so we adapt **TaPEx** and **Binder** <sup>[Herzig et al. (2020); Cheng et al. (2022)]</sup>. For multi-table, we first identify relevant table region (fine-tuned model) and feed it to the baseline. Excess tokens are truncated. Answers are correct if the cell address/combination is correct.

**Model Selection.** We primarily use GPT4 (Appendix G).

#### 4.2.3. Experiment Procedure

We use the table-detection–fine-tuned model in a **CoS** pipeline:

1. Identify relevant table region(s).
2. If the region is too large, apply further compression or **table-splitting** (Appendix M.2).
3. Feed the final region into the model for Q&A.

---

## 5. Results

### 5.1. Compression Ratio

We measure compression ratio \(r = n/n'\), where \(n\) is original tokens and \(n'\) is compressed. Our method achieves ~25× on the test set, drastically reducing cost. Table&nbsp;1 shows results for different module combos, demonstrating robust compression gains.

### 5.2. Spreadsheet Table Detection

**Table 2** (below) shows various model/method configurations. Detailed case studies in Appendix K.

#### 5.2.1. Main Results

1. **Enhanced Performance with various LLMs**. Fine-tuned GPT4 yields ~76% F1 overall; using our encoding (minus final aggregation) hits ~79%. That is +27% over GPT4 on original data, +13% over TableSense-CNN, and is new SOTA. Similar improvements appear for Llama3 (+25%), Phi3 (+36%), Llama2 (+38%), Mistral-v2 (+18%).

2. **Larger Spreadsheets Benefit Most**. Gains on “Huge” spreadsheets are especially large (+75% over GPT4, +19% over TableSense-CNN), confirming that token-limit constraints hamper naive approaches.

3. **Improvement in ICL**. Compact encoding also boosts in-context learning: GPT4 improves ~26% with the compressed format (Appendix J.2).

4. **Significant Cost Reduction**. Token usage shrinks ~96%, saving cost near-proportionally (Appendix I).

#### 5.2.2. Ablation Results

**Table 3** highlights ablations:

- **Removing extraction** drastically reduces F1, showing it’s critical for preserving structure.
- **Removing aggregation** *slightly* increases F1 but at the cost of higher token usage. NFS-based representation is more abstract, and LLM may prefer raw numbers for boundary detection. Nevertheless, the aggregator is valuable in many tasks, especially QA with large numeric columns.

### 5.3. Spreadsheet QA

**Table 4** compares results on spreadsheet QA. The key observations:

1. **Effectiveness of CoS**. Our chain-of-spreadsheet approach yields a +22% accuracy over baseline GPT4, by focusing only on relevant sub-tables.
2. **Generalization of Fine-Tuned Model**. The fine-tuned model (table detection) also helps QA: +6% improvement, +37% over TaPEx, +12% over Binder.
3. **Table-Splitting**. Even further helps large cases, +3% (ICL) or +5% (fine-tuned), by chunking monstrous tables while retaining structural references.

---

## 6. Conclusion

We propose **SpreadsheetLLM**, a novel framework leveraging LLMs to process and understand spreadsheet data. **SheetCompressor** efficiently addresses the challenges of scale, diversity, and complexity, drastically reducing token usage by up to 25×. Fine-tuned LLMs achieve state-of-the-art results in **table detection** and excel in **spreadsheet QA** through a proposed **Chain of Spreadsheet** pipeline. Overall, **SpreadsheetLLM** broadens the horizons of intelligent spreadsheet analysis and paves the way for more advanced user interactions.

---

## Limitations

Our framework does not yet leverage full **format details** (colors, borders, merges) because they may consume excessive tokens and exceed LLM capacity. Meanwhile, semantic-based compression for text-heavy cells (e.g., grouping “China,” “America,” “France” under “Country”) is also not done. Future work will explore advanced semantic compression, further bridging the gap between raw data scale and LLM capacity.

---

## Ethics Statement

All data were collected, analyzed, and reported without bias or external influence. Privacy and confidentiality were strictly maintained; no personal identifiers were used. We followed fair labor practices for data annotators and are committed to transparent methodology sharing for further research and knowledge advancement in this domain.

---

## References

...omitted

---

## Appendix A. GPT4 Struggles to Understand Spreadsheets

(See Figures 3 and 4 in the original text.)  
We show examples of GPT4’s difficulties with large/spreadsheet input. We also tested whether encoding format info helps. The results suggest minimal or negative gains once token limits are reached (Table 5 in the original).

---

## Appendix B. Traditional Encoding Methods

We compared **Markdown**, **XML**, and **HTML** for spreadsheet data. XML/HTML require extensive repeated labels, inflating token counts. Markdown is less verbose but lacks explicit cell addresses for indexing. We validated with GPT4 on table detection (Table&nbsp;6). Though Markdown is more token-efficient than XML/HTML, it remains insufficient for complex spreadsheets (lack of merges, multi-table indexing, etc.).

---

## Appendix C. Lightweight Heuristics for Structural-Anchor Proposal

We first enumerate bounding lines by row/column differences (values, merges, borders, fill color, etc.). Rows/columns with few discrepancies are considered canonical data lines. Next, we form candidate boundaries by all pairs of top/bottom, left/right lines. We prune suspicious boundaries using numeric/text distribution checks. Overlapping candidates are also resolved (Figure&nbsp;6). The final set of candidate boundaries are not perfect (~46.3% EoB-0) but we keep a neighborhood of \(\pm k\) rows/columns to ensure coverage. Setting \(k=4\) preserves ~97% of real bounding lines. The LLM then refines exact boundaries.

---

## Appendix D. Ablation on Table Detection

### D.1. Results on Structure-Anchor Threshold

Table 7 compares different \(k\in\{2,4,8\}\). **\(k=4\)** yields the best F1 and balanced compression. Smaller \(k\) omits boundaries; larger \(k\) yields fewer token gains. This is consistent across small–huge data sets.

### D.2. Results of Spreadsheet Table Detection on ICL

Table 8 further shows ablation results in an ICL setting with GPT4.

---

## Appendix E. Test Dataset Quality Improvement

We refined the **Dong et al. (2019b)** test set by removing non-English sheets, discarding duplicates, and re-annotating with 5 experts. Type-2 data with multiple valid labels are kept as well. This yields 188 spreadsheets (type 1 + type 2) with 311 labeled tables. See supplements for raw files and multi-annotation results.

---

## Appendix F. Dataset Partition

We categorize test spreadsheets by token length (with vanilla encoding + format info) into **Small**, **Medium**, **Large**, and **Huge**. We show an encoding snippet in Markdown form, listing cells in row-major order plus format metadata (sample given in text).

---

## Appendix G. Experiment Setup

- **Open-source**: Llama2, Llama3, Mistral-v2, Phi3. Fine-tuned with LoRA, each on 8×A100 GPUs.
- **Closed-source**: GPT4, GPT3.5, with partial fine-tuning in Azure OpenAI.
- Full hyperparameters in the supplement.
- Prompt and output are typical: temperature=0, max_tokens=300, top_p=0.95, etc.

---

## Appendix H. Spreadsheet QA Test Dataset

64 spreadsheets:
- 9 single-table
- 35 double-table
- 11 triple-table
- 9 with ≥4 tables

We formulated 4–6 questions each, focusing on basic search and arithmetic, for 307 items total. Each question’s ground-truth is a cell address or formula referencing addresses. Two additional annotators cross-verified. Example:

```
Q: "What were the highest temperatures in Washington DC in 1998?"
A: "X23 AND X24"
```

---

## Appendix I. Cost Calculation

Using GPT4’s ICL price as reference. Original average usage ~1.548M tokens vs. compressed ~62k tokens. This yields a 96% cost saving (0.235→0.00939 for GPT4, 0.00391→0.000157 for GPT3.5), see details in table.

---

## Appendix J. Other Experimental Results

### J.1. Compression Results

Tables 9 and 10 show compression ratio details by module for train/valid, verifying consistent ~25× gains overall.

### J.2. ICL Results of Open-Source Models on Table Detection

Table 11 shows open-source LLMs are weaker in ICL vs. closed-source, but still see large improvements with compression.

### J.3. Spreadsheet QA Ablation

Table 12 removes each module to see the effect on final QA accuracy and region-detection accuracy. Extraction is again crucial; aggregator sometimes slightly lowers boundary detection but helps large numeric columns.

---

## Appendix K. Case Studies

### K.1. Before vs. After Structural-Anchor

Figure 7: Large region of empties misleads GPT4 to predict multiple tables. After anchor-based extraction, the skeleton is smaller and reveals a single table.

### K.2. Before vs. After Inverted-Index

Figure 8: Two tables sharing the same column headers are merged incorrectly. Inverted-index merges repeated text, clarifying they are separate.

### K.3. Before vs. After Data-Aware Aggregation

Figure 9: Repetitive numeric columns replaced with a single format “FloatNum.” The table range detection is unaffected but token usage drastically drops.

### K.4. SpreadsheetLLM vs. TableSense-CNN

Figure 10: A tricky case with an apparently separated column “R5:R14” that actually shares meaning with the left block. Traditional CNN misses it; ours captures the relation from semantic cues.

---

## Appendix L. Prompt Templates

### L.1. Vanilla Prompt for Table Detection

**Instruction**:  
Given a string denoting cell data in a spreadsheet, with pairs like `A1,Year|A2,Profit|...`. Return ranges like `[“range”: “A1:F9”, “range”: “A12:F18”]` for each table, ignoring titles or comments. **No extra words**.

### L.2. SpreadsheetLLM Prompt for Table Detection

Similar but references compression artifacts like `(IntNum|A1:B3)`, `(#,##0|A2)`, etc., and instructs to detect each table range precisely.

### L.3. Prompt Template for Spreadsheet QA

A two-stage CoS approach:

1. **Stage 1**: Identify which table region answers the question.
2. **Stage 2**: Provide the cell address or formula.

---

# Appendix M: Algorithm Steps

## M.1 Identical Cell Aggregation

**Algorithm 1: Identical Cell Aggregation**

**Input:** Matrix $nfs$ composed of all cell values in the spreadsheet.

1. Initialize $m$ and $n$ as the number of matrix $input$ rows and columns.
2. Initialize the $m \times n$ matrix $visited$ with all values set to $False$.
3. Initialize $areas$ as an empty list.
4. Initialize the $FormatDict$ dictionary, the key-value pairs are data values and predefined types respectively.
5. **Function** $dfs(r, c, val\_type)$:
6. &nbsp;&nbsp;&nbsp;&nbsp;if $visited[r][c] \lor val\_type \neq FormatDict[nfs[r,c]]$ then
7. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;return $[r,c,r-1,c-1]$;
8. &nbsp;&nbsp;&nbsp;&nbsp;$visited[r][c] \leftarrow True$;
9. &nbsp;&nbsp;&nbsp;&nbsp;$bounds \leftarrow [r,c,r,c]$;
10. &nbsp;&nbsp;&nbsp;&nbsp;foreach $(tr, tc)$ around $(r, c)$ do
11. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;if $\neg visited[tr][tc] \land val\_type == FormatDict[nfs[tr,tc]]$ then
12. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;$new\_bounds \leftarrow dfs(tr,tc,val\_type)$;
13. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;update bounds to include $new\_bounds$;
14. &nbsp;&nbsp;&nbsp;&nbsp;return bounds;
15. for $r = 0$ to $m-1$ do
16. &nbsp;&nbsp;&nbsp;&nbsp;for $c = 0$ to $n-1$ do
17. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;if $\neg visited[r][c]$ then
18. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;$val\_type \leftarrow FormatDict[nfs[r,c]]$;
19. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;$bounds \leftarrow dfs(r,c,val\_type)$;
20. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;$areas \leftarrow areas + ((bounds[0],bounds[1]),$
21. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;$(bounds[2],bounds[3]),$
22. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;$val\_type)$;

**Output:** Aggregation matrix $areas$, each cell which is filled with the corresponding datatype after applying custom rules.

## M.2 Table Split QA Algorithm

**Algorithm 2: Question Answering Process for Large Tables**

**Input:** $question$ composed of strings and two-dimensional matrix $region$

1. Initialize $header$ and $answers$ to empty lists
2. if $calculateTokens(region) \leq 4096$ then
3. &nbsp;&nbsp;&nbsp;&nbsp;return answer_question(question, region);
4. else
5. &nbsp;&nbsp;&nbsp;&nbsp;$header \leftarrow predict\_header(region)$;
6. &nbsp;&nbsp;&nbsp;&nbsp;$body \leftarrow region[length(header) + 1 : end]$;
7. &nbsp;&nbsp;&nbsp;&nbsp;for $i = 0$ to $length(body)$ do
8. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;$new\_table \leftarrow header + body[i : i+3]$;
9. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;$answer \leftarrow answer\_question(question, table)$;
10. &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;$answers.append(answer)$;

**Output:** final result $answers$
---
