package com.norvexa.clearup.data.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.norvexa.clearup.domain.model.HistoryEntry
import com.norvexa.clearup.domain.model.HistoryType
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

class HistoryStore(context: Context) : Closeable {
    private val helper = Helper(context.applicationContext)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    suspend fun record(
        type: HistoryType,
        itemCount: Int,
        bytes: Long,
        note: String,
    ) = withContext(Dispatchers.IO) {
        helper.writableDatabase.insert(
            "history",
            null,
            ContentValues().apply {
                put("type", type.name)
                put("item_count", itemCount)
                put("bytes", bytes)
                put("note", note.take(500))
                put("created_at", System.currentTimeMillis())
            },
        )
        trim()
        refresh()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("history", null, null)
        refresh()
    }

    private fun trim() {
        helper.writableDatabase.execSQL(
            "DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY created_at DESC LIMIT 200)",
        )
    }

    private fun refresh() {
        val result = mutableListOf<HistoryEntry>()
        helper.readableDatabase.query(
            "history",
            arrayOf("id", "type", "item_count", "bytes", "note", "created_at"),
            null,
            null,
            null,
            null,
            "created_at DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += HistoryEntry(
                    id = cursor.getLong(0),
                    type = runCatching {
                        HistoryType.valueOf(cursor.getString(1))
                    }.getOrDefault(HistoryType.SCAN),
                    itemCount = cursor.getInt(2),
                    bytes = cursor.getLong(3),
                    note = cursor.getString(4).orEmpty(),
                    createdAtMillis = cursor.getLong(5),
                )
            }
        }
        _entries.value = result
    }

    override fun close() {
        job.cancel()
        helper.close()
    }

    private class Helper(context: Context) : SQLiteOpenHelper(
        context,
        "clearup_history.db",
        null,
        1,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL,
                    item_count INTEGER NOT NULL,
                    bytes INTEGER NOT NULL,
                    note TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )""".trimIndent(),
            )
            db.execSQL("CREATE INDEX history_created_at ON history(created_at DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
