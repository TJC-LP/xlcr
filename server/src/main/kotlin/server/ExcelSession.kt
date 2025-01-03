package com.tjclp.xlcr.server

import com.tjclp.xlcr.Pipeline
import com.tjclp.xlcr.models.SheetData
import com.tjclp.xlcr.parsers.excel.ExcelMarkdownParser
import com.tjclp.xlcr.parsers.excel.ExcelSvgParser
import com.tjclp.xlcr.parsers.excel.MarkdownToExcelParser
import com.tjclp.xlcr.utils.FileUtils
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import scala.Some

class ExcelSession private constructor(private val originalFile: Path) {
    val sessionId: String = UUID.randomUUID().toString()
    @Volatile
    private var currentSheetData: List<SheetData> = emptyList()
    private val tempDir: Path = Files.createTempDirectory("xlcr_session_$sessionId")
    private val workingExcel: Path = tempDir.resolve("working.xlsx")
    private val workingJson: Path = tempDir.resolve("working.json")

    fun getJson(): Result<String> = runCatching {
        // Use the Pipeline or ExcelJsonParser to convert the workingExcel to JSON
        // For example, we can do:
        Pipeline.run(workingExcel.toString(), workingJson.toString(), false)
        val bytes = Files.readAllBytes(workingJson)
        String(bytes)
    }

    fun patchJson(jsonDiff: String): Result<Unit> = runCatching {
        // Write the jsonDiff to a temporary file
        val diffFile = tempDir.resolve("diff.json")
        Files.write(diffFile, jsonDiff.toByteArray())

        // Use the pipeline with diffMode = true to merge changes into workingExcel
        Pipeline.run(diffFile.toString(), workingExcel.toString(), true)
    }

    init {
        Files.copy(originalFile, workingExcel)
    }

    fun getMarkdown(): Result<String> = runCatching {
        ExcelMarkdownParser.extractContent(workingExcel, null).map { content ->
            String(content.data())
        }.get()
    }

    fun getSvg(): Result<String> = runCatching {
        ExcelSvgParser.extractContent(workingExcel, null).map { content ->
            String(content.data())
        }.get()
    }

    fun updateFromMarkdown(markdown: String): Result<Unit> = runCatching {
        // We'll keep the placeholder for Markdown->Excel logic, or use a pipeline if available.
        val tempMarkdownPath = tempDir.resolve("input.md")
        Files.write(tempMarkdownPath, markdown.toByteArray())

        // We'll invoke MarkdownToExcelParser to rebuild the Excel from the markdown content
        val parseResult = MarkdownToExcelParser.extractContent(tempMarkdownPath, Some(workingExcel))
        if (parseResult.isFailure) {
            throw parseResult.failed().get()
        }
    }

    fun save(): Result<Unit> = runCatching {
        Files.copy(workingExcel, originalFile)
    }

    fun cleanup() {
        FileUtils.deleteRecursively(tempDir)
    }

    companion object {
        private val activeSessions = mutableMapOf<String, ExcelSession>()

        fun create(file: Path): String {
            val session = ExcelSession(file)
            activeSessions[session.sessionId] = session
            return session.sessionId
        }

        fun get(sessionId: String): ExcelSession? = activeSessions[sessionId]

        fun remove(sessionId: String) {
            activeSessions[sessionId]?.let { session ->
                session.cleanup()
                activeSessions.remove(sessionId)
            }
        }
    }
}