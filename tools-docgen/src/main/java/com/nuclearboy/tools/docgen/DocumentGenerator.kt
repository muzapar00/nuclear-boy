package com.nuclearboy.tools.docgen

import com.nuclearboy.common.AppError
import com.nuclearboy.common.AppResult
import com.nuclearboy.common.FileInfo
import com.nuclearboy.python.PythonResult
import com.nuclearboy.python.PythonSandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Generates Word (.docx) and Excel (.xlsx) documents by constructing
 * Python scripts and executing them via [PythonSandbox].
 *
 * All document formatting uses Chinese-friendly defaults:
 * - Font: Microsoft YaHei (微软雅黑) or SimSun (宋体)
 * - A4 page size, standard margins
 * - UTF-8 encoding throughout
 */
class DocumentGenerator(
    private val pythonSandbox: PythonSandbox,
) {
    /**
     * Generate a Word document at [outputPath] with the given [title] and
     * [content]. If a [template] .docx file path is provided, content is
     * merged into the template instead of building from scratch.
     *
     * [content] can be Markdown-style text which will be converted to
     * styled paragraphs (headings, bold, lists, etc.) within the script.
     */
    suspend fun generateWordDocument(
        outputPath: String,
        title: String,
        content: String,
        template: String? = null,
    ): AppResult<FileInfo> = withContext(Dispatchers.IO) {
        try {
            // Ensure the output directory exists
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            val pythonScript = if (template != null) {
                buildWordFromTemplateScript(outputPath, title, content, template)
            } else {
                buildWordFromScratchScript(outputPath, title, content)
            }

            val result = pythonSandbox.execute(pythonScript, "")

            if (result.exitCode != 0) {
                return@withContext AppResult.failure(
                    AppError.PythonRuntimeError,
                    "Word generation failed: ${result.stderr}"
                )
            }

            if (!outputFile.isFile) {
                return@withContext AppResult.failure(
                    AppError.FileWriteDenied,
                    "Word document was not created at $outputPath"
                )
            }

            AppResult.success(buildFileInfo(outputFile))
        } catch (e: Exception) {
            AppResult.failure(
                AppError.Unknown,
                "Word generation error: ${e.message}"
            )
        }
    }

    /**
     * Generate an Excel spreadsheet at [outputPath] with one or more [sheets].
     * Each sheet can optionally include a chart.
     */
    suspend fun generateExcelSpreadsheet(
        outputPath: String,
        sheets: List<SheetData>,
    ): AppResult<FileInfo> = withContext(Dispatchers.IO) {
        try {
            if (sheets.isEmpty()) {
                return@withContext AppResult.failure(
                    AppError.Unknown,
                    "At least one sheet is required"
                )
            }

            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            val pythonScript = buildExcelScript(outputPath, sheets)

            val result = pythonSandbox.execute(pythonScript, "")

            if (result.exitCode != 0) {
                return@withContext AppResult.failure(
                    AppError.PythonRuntimeError,
                    "Excel generation failed: ${result.stderr}"
                )
            }

            if (!outputFile.isFile) {
                return@withContext AppResult.failure(
                    AppError.FileWriteDenied,
                    "Excel file was not created at $outputPath"
                )
            }

            AppResult.success(buildFileInfo(outputFile))
        } catch (e: Exception) {
            AppResult.failure(
                AppError.Unknown,
                "Excel generation error: ${e.message}"
            )
        }
    }

    /**
     * Read and return the text content of a Word or Excel document.
     * For .docx files, reads paragraphs. For .xlsx, reads a summary of all sheets.
     */
    suspend fun readDocumentContent(filePath: String): AppResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.isFile) {
                    return@withContext AppResult.failure(
                        AppError.FileNotFound,
                        "Document not found: $filePath"
                    )
                }

                val extension = file.extension.lowercase()
                val pythonScript = when (extension) {
                    "docx" -> buildDocxReaderScript(filePath)
                    "xlsx" -> buildXlsxReaderScript(filePath)
                    else -> return@withContext AppResult.failure(
                        AppError.Unknown,
                        "Unsupported document type: $extension (expected .docx or .xlsx)"
                    )
                }

                val result = pythonSandbox.execute(pythonScript, "")

                if (result.exitCode != 0) {
                    return@withContext AppResult.failure(
                        AppError.FileReadError,
                        "Failed to read document: ${result.stderr}"
                    )
                }

                AppResult.success(result.stdout)
            } catch (e: Exception) {
                AppResult.failure(
                    AppError.FileReadError,
                    "Document read error: ${e.message}"
                )
            }
        }

    // ──────────────────────────────────────────────
    //  Python script builders — Word
    // ──────────────────────────────────────────────

    private fun buildWordFromScratchScript(
        outputPath: String,
        title: String,
        content: String,
    ): String {
        val escapedTitle = escapePythonString(title)
        val escapedContent = escapePythonString(content)
        val escapedPath = escapePythonString(outputPath)

        return """
# -*- coding: utf-8 -*-
import os
from docx import Document
from docx.shared import Pt, Inches, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.style import WD_STYLE_TYPE
from docx.oxml.ns import qn

doc = Document()

# --- Page setup: A4, standard margins ---
for section in doc.sections:
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(2.54)
    section.bottom_margin = Cm(2.54)
    section.left_margin = Cm(3.18)
    section.right_margin = Cm(3.18)

# --- Configure default style ---
style = doc.styles['Normal']
font = style.font
font.name = 'Microsoft YaHei'
font.size = Pt(11)
style.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')

# --- Title ---
title_para = doc.add_heading("$escapedTitle", level=0)
title_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
for run in title_para.runs:
    run.font.name = 'Microsoft YaHei'
    run.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')
    run.font.size = Pt(22)

doc.add_paragraph()  # spacer

# --- Parse content (basic markdown-like) ---
lines = '''$escapedContent'''.split('\\n')
i = 0
while i < len(lines):
    line = lines[i]
    stripped = line.strip()

    if not stripped:
        doc.add_paragraph()
        i += 1
        continue

    # Heading 1
    if stripped.startswith('# ') and not stripped.startswith('## '):
        h = doc.add_heading(stripped[2:], level=1)
        for run in h.runs:
            run.font.name = 'Microsoft YaHei'
            run.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')
        i += 1
        continue

    # Heading 2
    if stripped.startswith('## '):
        h = doc.add_heading(stripped[3:], level=2)
        for run in h.runs:
            run.font.name = 'Microsoft YaHei'
            run.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')
        i += 1
        continue

    # Horizontal rule
    if stripped in ('---', '***', '___'):
        doc.add_paragraph('_' * 60)
        i += 1
        continue

    # Unordered list
    if stripped.startswith('- ') or stripped.startswith('* '):
        text = stripped[2:]
        p = doc.add_paragraph(style='List Bullet')
        # Parse inline bold/italic
        add_formatted_run(p, text)
        i += 1
        continue

    # Ordered list
    if stripped and stripped[0].isdigit() and '. ' in stripped:
        dot_idx = stripped.index('. ')
        text = stripped[dot_idx + 2:]
        p = doc.add_paragraph(style='List Number')
        add_formatted_run(p, text)
        i += 1
        continue

    # Code block
    if stripped.startswith('```'):
        i += 1
        code_lines = []
        while i < len(lines) and not lines[i].strip().startswith('```'):
            code_lines.append(lines[i])
            i += 1
        code_text = '\n'.join(code_lines)
        p = doc.add_paragraph()
        run = p.add_run(code_text)
        run.font.name = 'Consolas'
        run.font.size = Pt(9)
        run.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
        i += 1  # skip closing ```
        continue

    # Table (crude detection: line contains |)
    if '|' in stripped and not stripped.startswith('#'):
        table_rows = []
        while i < len(lines) and '|' in lines[i].strip():
            table_rows.append(lines[i].strip())
            i += 1
        if table_rows:
            build_table_from_markdown(doc, table_rows)
        continue

    # Normal paragraph
    p = doc.add_paragraph()
    add_formatted_run(p, stripped)
    i += 1

# --- Helper functions ---
def qn(tag):
    # Qualified name for XML elements.
    from docx.oxml.ns import qn as _qn
    return _qn(tag)

def add_formatted_run(paragraph, text):
    # Add runs with bold/italic parsing.
    import re
    # Bold **text**
    pattern = re.compile(r'(\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`|([^*`]+))')
    for match in pattern.finditer(text):
        if match.group(2):  # **bold**
            run = paragraph.add_run(match.group(2))
            run.bold = True
            run.font.name = 'Microsoft YaHei'
            run.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')
        elif match.group(3):  # *italic*
            run = paragraph.add_run(match.group(3))
            run.italic = True
            run.font.name = 'Microsoft YaHei'
            run.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')
        elif match.group(4):  # `code`
            run = paragraph.add_run(match.group(4))
            run.font.name = 'Consolas'
            run.font.size = Pt(9)
        elif match.group(5):  # plain text
            run = paragraph.add_run(match.group(5))
            run.font.name = 'Microsoft YaHei'
            run.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')

def build_table_from_markdown(doc, rows):
    # Build a Word table from markdown table rows.
    if not rows:
        return
    # Parse rows into cells
    parsed = []
    for row in rows:
        cells = [c.strip() for c in row.split('|') if c.strip()]
        # Skip separator rows like |---|---|
        if all(set(c) <= set('-: ') for c in cells):
            continue
        parsed.append(cells)

    if not parsed:
        return

    ncols = max(len(r) for r in parsed)
    table = doc.add_table(rows=len(parsed), cols=ncols)
    table.style = 'Light Grid Accent 1'

    for r, row_data in enumerate(parsed):
        for c, cell_text in enumerate(row_data):
            if c < ncols:
                cell = table.cell(r, c)
                cell.text = cell_text
                for paragraph in cell.paragraphs:
                    for run in paragraph.runs:
                        run.font.name = 'Microsoft YaHei'
                        run.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')
                        run.font.size = Pt(10)
                        if r == 0:
                            run.bold = True

# --- Save ---
output_dir = os.path.dirname(r"$escapedPath")
if output_dir and not os.path.exists(output_dir):
    os.makedirs(output_dir)
doc.save(r"$escapedPath")
print(f"Word document saved: $escapedPath")
        """.trimIndent()
    }

    private fun buildWordFromTemplateScript(
        outputPath: String,
        title: String,
        content: String,
        templatePath: String,
    ): String {
        val escapedTitle = escapePythonString(title)
        val escapedContent = escapePythonString(content)
        val escapedPath = escapePythonString(outputPath)
        val escapedTemplate = escapePythonString(templatePath)

        return """
# -*- coding: utf-8 -*-
import os
from docx import Document
from docx.shared import Pt, Cm

doc = Document(r"$escapedTemplate")

# Replace template placeholders
placeholder_map = {
    '{{title}}': "$escapedTitle",
    '{{content}}': '''$escapedContent''',
    '{{date}}': __import__('datetime').datetime.now().strftime('%Y年%m月%d日'),
}

for paragraph in doc.paragraphs:
    for key, value in placeholder_map.items():
        if key in paragraph.text:
            paragraph.text = paragraph.text.replace(key, value)
            for run in paragraph.runs:
                run.font.name = 'Microsoft YaHei'
                try:
                    from docx.oxml.ns import qn
                    run.element.rPr.rFonts.set(qn('w:eastAsia'), 'Microsoft YaHei')
                except Exception:
                    pass

# Also check tables
for table in doc.tables:
    for row in table.rows:
        for cell in row.cells:
            for paragraph in cell.paragraphs:
                for key, value in placeholder_map.items():
                    if key in paragraph.text:
                        paragraph.text = paragraph.text.replace(key, value)

output_dir = os.path.dirname(r"$escapedPath")
if output_dir and not os.path.exists(output_dir):
    os.makedirs(output_dir)
doc.save(r"$escapedPath")
print(f"Word document saved (from template): $escapedPath")
        """.trimIndent()
    }

    // ──────────────────────────────────────────────
    //  Python script builders — Excel
    // ──────────────────────────────────────────────

    private fun buildExcelScript(
        outputPath: String,
        sheets: List<SheetData>,
    ): String {
        val escapedPath = escapePythonString(outputPath)
        val sheetsCode = buildString {
            for ((index, sheet) in sheets.withIndex()) {
                val sheetVar = "sheet${index}"
                val escapedName = escapePythonString(sheet.name)
                appendLine("$sheetVar = wb.create_sheet(title=\"$escapedName\")")
                appendLine("# Sheet: ${sheet.name}")

                // Write headers
                if (sheet.headers.isNotEmpty()) {
                    val headersList = sheet.headers.joinToString(", ") {
                        "r\"\"\"${escapePythonString(it)}\"\"\""
                    }
                    appendLine("${sheetVar}_headers = [$headersList]")
                    appendLine("for c, header in enumerate(${sheetVar}_headers, 1):")
                    appendLine("    cell = $sheetVar.cell(row=1, column=c, value=header)")
                    appendLine("    cell.font = header_font")
                    appendLine("    cell.fill = header_fill")
                    appendLine("    cell.alignment = Alignment(horizontal='center', vertical='center')")
                }

                // Write rows
                if (sheet.rows.isNotEmpty()) {
                    appendLine("${sheetVar}_rows = [")
                    for (row in sheet.rows) {
                        val rowItems = row.joinToString(", ") { "r\"\"\"${escapePythonString(it)}\"\"\"" }
                        appendLine("    [$rowItems],")
                    }
                    appendLine("]")
                    appendLine("for r, row_data in enumerate(${sheetVar}_rows, 2):")
                    appendLine("    for c, value in enumerate(row_data, 1):")
                    appendLine("        cell = $sheetVar.cell(row=r, column=c, value=value)")
                    appendLine("        cell.font = data_font")
                    appendLine("        cell.alignment = Alignment(vertical='center')")
                }

                // Auto-fit column widths
                appendLine("for col in $sheetVar.columns:")
                appendLine("    max_length = 0")
                appendLine("    col_letter = get_column_letter(col[0].column)")
                appendLine("    for cell in col:")
                appendLine("        try:")
                appendLine("            if cell.value:")
                appendLine("                max_length = max(max_length, len(str(cell.value)))")
                appendLine("        except Exception:")
                appendLine("            pass")
                appendLine("    adjusted_width = min(max_length + 2, 40)")
                appendLine("    ${sheetVar}.column_dimensions[col_letter].width = adjusted_width")

                // Optional chart
                if (sheet.chartData != null) {
                    val chart = sheet.chartData
                    appendLine("# Chart: ${chart.title}")
                    appendLine("chart_${index} = ${chartTypeToClass(chart.type)}()")
                    appendLine("chart_${index}.title = \"${escapePythonString(chart.title)}\"")
                    appendLine("chart_${index}.style = 10")
                    appendLine("chart_${index}.y_axis.title = \"值\"")
                    appendLine("# Data reference: ${chart.dataRange}")
                    val ref = chart.dataRange
                    appendLine("data_ref_${index} = Reference($sheetVar, range_string=r'$ref')")
                    appendLine("cats_ref_${index} = Reference($sheetVar, min_col=1, max_col=1, min_row=2, max_row=${sheet.rows.size + 1})")
                    appendLine("chart_${index}.add_data(data_ref_${index}, titles_from_data=True)")
                    appendLine("chart_${index}.set_categories(cats_ref_${index})")
                    appendLine("${sheetVar}.add_chart(chart_${index}, \"F2\")")
                }

                appendLine()
            }

            // Remove default sheet if we have custom sheets
            if (sheets.isNotEmpty()) {
                appendLine("if 'Sheet' in wb.sheetnames:")
                appendLine("    del wb['Sheet']")
            }
        }

        return """
# -*- coding: utf-8 -*-
import os
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter

wb = Workbook()

# --- Styling ---
header_font = Font(name='Microsoft YaHei', size=11, bold=True, color='FFFFFF')
header_fill = PatternFill(start_color='2F5496', end_color='2F5496', fill_type='solid')
data_font = Font(name='Microsoft YaHei', size=10)
thin_border = Border(
    left=Side(style='thin'),
    right=Side(style='thin'),
    top=Side(style='thin'),
    bottom=Side(style='thin')
)

$sheetsCode

# --- Set the first sheet as active ---
if wb.sheetnames:
    wb.active = wb[wb.sheetnames[0]]

# --- Save ---
output_dir = os.path.dirname(r"$escapedPath")
if output_dir and not os.path.exists(output_dir):
    os.makedirs(output_dir)
wb.save(r"$escapedPath")
print(f"Excel workbook saved: $escapedPath (sheets: {len(wb.sheetnames)})")
        """.trimIndent()
    }

    // ──────────────────────────────────────────────
    //  Python script builders — Readers
    // ──────────────────────────────────────────────

    private fun buildDocxReaderScript(filePath: String): String {
        val escapedPath = escapePythonString(filePath)
        return """
# -*- coding: utf-8 -*-
from docx import Document

doc = Document(r"$escapedPath")
for paragraph in doc.paragraphs:
    if paragraph.text.strip():
        print(paragraph.text)

# Also read tables
for i, table in enumerate(doc.tables):
    print(f"\n[表格 {i + 1}]")
    for row in table.rows:
        cells = [cell.text.strip() for cell in row.cells]
        print(' | '.join(cells))
        """.trimIndent()
    }

    private fun buildXlsxReaderScript(filePath: String): String {
        val escapedPath = escapePythonString(filePath)
        return """
# -*- coding: utf-8 -*-
from openpyxl import load_workbook

wb = load_workbook(r"$escapedPath", data_only=True)
for sheet_name in wb.sheetnames:
    ws = wb[sheet_name]
    print(f"[工作表: {sheet_name}]")
    for row in ws.iter_rows(values_only=True):
        values = [str(cell) if cell is not None else '' for cell in row]
        if any(v.strip() for v in values):
            print(' | '.join(values))
    print()
        """.trimIndent()
    }

    // ──────────────────────────────────────────────
    //  Utilities
    // ──────────────────────────────────────────────

    private fun buildFileInfo(file: File): FileInfo {
        return FileInfo(
            path = file.absolutePath,
            name = file.name,
            extension = file.extension,
            size = file.length(),
            lastModified = file.lastModified(),
            isDirectory = false,
        )
    }

    private fun escapePythonString(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun chartTypeToClass(type: String): String = when (type.lowercase()) {
        "bar" -> "BarChart()"
        "line" -> "LineChart()"
        "pie" -> "PieChart()"
        "scatter" -> "ScatterChart()"
        "area" -> "AreaChart()"
        "doughnut" -> "DoughnutChart()"
        "radar" -> "RadarChart()"
        else -> "BarChart()"
    }
}

/**
 * Data structure representing a single sheet in an Excel workbook.
 *
 * @param name The sheet name (tab label)
 * @param headers Column header texts
 * @param rows Data rows, each row being a list of cell values
 * @param chartData Optional chart configuration for this sheet
 */
data class SheetData(
    val name: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val chartData: ChartData? = null,
) {
    init {
        require(name.isNotBlank()) { "Sheet name must not be blank" }
    }

    val rowCount: Int get() = rows.size
    val columnCount: Int get() = headers.size.coerceAtLeast(rows.maxOfOrNull { it.size } ?: 0)
}

/**
 * Chart configuration for Excel sheets.
 *
 * @param type Chart type: "bar", "line", "pie", "scatter", "area", "doughnut", "radar"
 * @param title Display title of the chart
 * @param dataRange Excel-style range string for chart data (e.g. "B1:D10")
 */
data class ChartData(
    val type: String,
    val title: String,
    val dataRange: String,
) {
    init {
        require(type.lowercase() in setOf("bar", "line", "pie", "scatter", "area", "doughnut", "radar")) {
            "Unsupported chart type: $type"
        }
        require(title.isNotBlank()) { "Chart title must not be blank" }
        require(dataRange.isNotBlank()) { "Chart data range must not be blank" }
    }
}
