package com.norvexa.clearup.data.privilege

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.norvexa.clearup.domain.model.RootAuditEntry
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RootAuditStore(context: Context) : Closeable {
    private val helper = Helper(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _entries = MutableStateFlow<List<RootAuditEntry>>(emptyList())
    val entries: StateFlow<List<RootAuditEntry>> = _entries.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    suspend fun record(
        action: String,
        target: String,
        result: ShellResult,
    ) = withContext(Dispatchers.IO) {
        helper.writableDatabase.insert(
            TABLE,
            null,
            ContentValues().apply {
                put("action", action.take(MAX_ACTION_LENGTH))
                put("target", target.take(MAX_TARGET_LENGTH))
                put("success", if (result.success) 1 else 0)
                put("exit_code", result.exitCode)
                put("output_preview", result.output.take(MAX_PREVIEW_LENGTH))
                put("error_preview", result.error.take(MAX_PREVIEW_LENGTH))
                put("created_at", System.currentTimeMillis())
            },
        )
        helper.writableDatabase.execSQL(
            "DELETE FROM $TABLE WHERE id NOT IN " +
                "(SELECT id FROM $TABLE ORDER BY created_at DESC LIMIT $MAX_ENTRIES)",
        )
        refresh()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE, null, null)
        refresh()
    }

    private fun refresh() {
        val loaded = mutableListOf<RootAuditEntry>()
        helper.readableDatabase.query(
            TABLE,
            arrayOf(
                "id",
                "action",
                "target",
                "success",
                "exit_code",
                "output_preview",
                "error_preview",
                "created_at",
            ),
            null,
            null,
            null,
            null,
            "created_at DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                loaded += RootAuditEntry(
                    id = cursor.getLong(0),
                    action = cursor.getString(1).orEmpty(),
                    target = cursor.getString(2).orEmpty(),
                    success = cursor.getInt(3) == 1,
                    exitCode = cursor.getInt(4),
                    outputPreview = cursor.getString(5).orEmpty(),
                    errorPreview = cursor.getString(6).orEmpty(),
                    createdAtMillis = cursor.getLong(7),
                )
            }
        }
        _entries.value = loaded
    }

    override fun close() {
        scope.cancel()
        helper.close()
    }

    private class Helper(context: Context) : SQLiteOpenHelper(
        context,
        "clearup_root_audit.db",
        null,
        1,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    action TEXT NOT NULL,
                    target TEXT NOT NULL,
                    success INTEGER NOT NULL,
                    exit_code INTEGER NOT NULL,
                    output_preview TEXT NOT NULL,
                    error_preview TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX root_audit_created_at ON $TABLE(created_at DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        private const val TABLE = "root_audit"
        private const val MAX_ENTRIES = 300
        private const val MAX_ACTION_LENGTH = 80
        private const val MAX_TARGET_LENGTH = 300
        private const val MAX_PREVIEW_LENGTH = 2_000
    }
}
