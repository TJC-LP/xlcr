package com.tjclp.xlcr.server

import com.tjclp.xlcr.Pipeline
import com.tjclp.xlcr.models.excel.SheetData
import com.tjclp.xlcr.utils.FileUtils
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

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