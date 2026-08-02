package com.norvexa.clearup.data.privilege

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShizukuAuditStore(context: Context) {
    private val helper = Helper(context.applicationContext)

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
    }

    private class Helper(context: Context) : SQLiteOpenHelper(
        context,
        "clearup_shizuku_audit.db",
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
            db.execSQL("CREATE INDEX shizuku_audit_created_at ON $TABLE(created_at DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        private const val TABLE = "shizuku_audit"
        private const val MAX_ENTRIES = 300
        private const val MAX_ACTION_LENGTH = 80
        private const val MAX_TARGET_LENGTH = 300
        private const val MAX_PREVIEW_LENGTH = 2_000
    }
}
