package com.norvexa.clearup.data.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.norvexa.clearup.domain.model.HistoryEntry
import java.io.File
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReportExporter(context: Context) {
    private val appContext = context.applicationContext

    suspend fun createHistoryShareIntent(
        entries: List<HistoryEntry>,
    ): Intent = withContext(Dispatchers.IO) {
        val reportsDirectory = File(appContext.cacheDir, REPORTS_DIRECTORY)
        check(reportsDirectory.exists() || reportsDirectory.mkdirs()) {
            "Не удалось создать каталог отчётов"
        }

        cleanupOldReports(reportsDirectory)
        val timestamp = System.currentTimeMillis()
        val target = File(reportsDirectory, "clearup-history-$timestamp.json")
        val temporary = File(reportsDirectory, ".clearup-history-$timestamp.tmp")
        temporary.writeText(buildHistoryJson(entries).toString(JSON_INDENT_SPACES))
        check(temporary.renameTo(target)) {
            temporary.delete()
            "Не удалось завершить создание отчёта"
        }

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.files",
            target,
        )
        Intent(Intent.ACTION_SEND).apply {
            type = JSON_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ClearUp history report")
            clipData = ClipData.newRawUri("ClearUp history report", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildHistoryJson(entries: List<HistoryEntry>): JSONObject = JSONObject().apply {
        put("schema_version", 1)
        put("application", "ClearUp by NORVEXA")
        put("package_name", appContext.packageName)
        put("exported_at", Instant.ofEpochMilli(System.currentTimeMillis()).toString())
        put("entry_count", entries.size)
        put(
            "entries",
            JSONArray().apply {
                entries.forEach { entry ->
                    put(
                        JSONObject().apply {
                            put("id", entry.id)
                            put("type", entry.type.name)
                            put("item_count", entry.itemCount)
                            put("bytes", entry.bytes)
                            put("note", entry.note)
                            put("created_at_millis", entry.createdAtMillis)
                            put(
                                "created_at",
                                Instant.ofEpochMilli(entry.createdAtMillis).toString(),
                            )
                        },
                    )
                }
            },
        )
    }

    private fun cleanupOldReports(directory: File) {
        directory.listFiles()
            ?.filter { file ->
                file.isFile &&
                    (file.name.endsWith(".json") || file.name.endsWith(".tmp"))
            }
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_RETAINED_REPORTS - 1)
            ?.forEach(File::delete)
    }

    companion object {
        private const val REPORTS_DIRECTORY = "reports"
        private const val JSON_MIME_TYPE = "application/json"
        private const val JSON_INDENT_SPACES = 2
        private const val MAX_RETAINED_REPORTS = 10
    }
}
