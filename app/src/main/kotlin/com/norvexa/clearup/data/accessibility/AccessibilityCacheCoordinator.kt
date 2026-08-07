package com.norvexa.clearup.data.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityManager
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class AccessibilityCacheStage {
    WAITING_APP_DETAILS,
    WAITING_STORAGE_PAGE,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class AccessibilityBatchStage {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class AccessibilityCacheBatchItem(
    val packageName: String,
    val appLabel: String,
    val estimatedBytes: Long,
)

data class AccessibilityCacheSession(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val estimatedBytes: Long,
    val stage: AccessibilityCacheStage,
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
    val message: String,
    val historyRecorded: Boolean,
) {
    val active: Boolean
        get() = stage == AccessibilityCacheStage.WAITING_APP_DETAILS ||
            stage == AccessibilityCacheStage.WAITING_STORAGE_PAGE
}

data class AccessibilityCacheBatch(
    val id: String,
    val items: List<AccessibilityCacheBatchItem>,
    val currentIndex: Int,
    val completedCount: Int,
    val failedCount: Int,
    val clearedEstimateBytes: Long,
    val stage: AccessibilityBatchStage,
    val message: String,
    val historyRecorded: Boolean,
) {
    val active: Boolean get() = stage == AccessibilityBatchStage.RUNNING
    val totalCount: Int get() = items.size
    val currentItem: AccessibilityCacheBatchItem? get() = items.getOrNull(currentIndex)
}

data class AccessibilityCacheState(
    val consentAccepted: Boolean = false,
    val serviceEnabled: Boolean = false,
    val session: AccessibilityCacheSession? = null,
    val batch: AccessibilityCacheBatch? = null,
)

class AccessibilityCacheCoordinator(context: Context) :
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val accessibilityManager =
        appContext.getSystemService(AccessibilityManager::class.java)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<AccessibilityCacheState> = _state.asStateFlow()

    init {
        preferences.registerOnSharedPreferenceChangeListener(this)
        refreshCapabilities()
    }

    fun refreshCapabilities() {
        expireIfNeeded()
        publish()
    }

    fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    fun setConsentAccepted(accepted: Boolean) {
        preferences.edit().putBoolean(KEY_CONSENT, accepted).apply()
        if (!accepted) {
            cancel("Согласие на Accessibility-помощник отозвано")
        }
    }

    fun begin(
        packageName: String,
        appLabel: String,
        estimatedBytes: Long,
    ): AccessibilityCacheSession = beginBatch(
        listOf(AccessibilityCacheBatchItem(packageName, appLabel, estimatedBytes)),
    )

    fun beginBatch(items: List<AccessibilityCacheBatchItem>): AccessibilityCacheSession {
        val normalized = items
            .distinctBy { it.packageName }
            .take(MAX_BATCH_ITEMS)
        require(normalized.isNotEmpty()) { "Выберите хотя бы одно приложение" }
        normalized.forEach { item ->
            require(AccessibilityCachePolicy.isValidPackageName(item.packageName)) {
                "Invalid package name"
            }
        }
        val current = readState()
        check(current.consentAccepted) {
            "Сначала подтвердите согласие на Accessibility-помощник"
        }
        check(current.serviceEnabled) {
            "Accessibility-помощник не включён в системных настройках"
        }
        check(current.batch?.active != true && current.session?.active != true) {
            "Предыдущая пакетная очистка ещё выполняется"
        }

        val now = System.currentTimeMillis()
        val batchId = UUID.randomUUID().toString()
        val first = normalized.first()
        val session = newSession(first, now)
        preferences.edit()
            .putString(KEY_BATCH_ID, batchId)
            .putString(KEY_BATCH_ITEMS, serializeItems(normalized))
            .putInt(KEY_BATCH_INDEX, 0)
            .putInt(KEY_BATCH_COMPLETED, 0)
            .putInt(KEY_BATCH_FAILED, 0)
            .putLong(KEY_BATCH_CLEARED_BYTES, 0L)
            .putString(KEY_BATCH_STAGE, AccessibilityBatchStage.RUNNING.name)
            .putString(KEY_BATCH_MESSAGE, "Подготовлено ${normalized.size} приложений")
            .putBoolean(KEY_BATCH_HISTORY_RECORDED, false)
            .also { putSession(it, session) }
            .apply()
        return session
    }

    fun activeSession(): AccessibilityCacheSession? {
        expireIfNeeded()
        return readSession()?.takeIf { it.active }
    }

    fun markWaitingStorage(sessionId: String) {
        updateSession(sessionId) { session ->
            if (session.stage != AccessibilityCacheStage.WAITING_APP_DETAILS) {
                session
            } else {
                session.copy(
                    stage = AccessibilityCacheStage.WAITING_STORAGE_PAGE,
                    message = "Ожидание страницы хранилища и кэша",
                )
            }
        }
    }

    fun completeAndAdvance(
        sessionId: String,
        message: String = "Системная кнопка «Очистить кэш» нажата",
    ): AccessibilityCacheSession? {
        val session = readSession() ?: return null
        if (session.id != sessionId || !session.active) return null
        val batch = readBatch()
        if (batch == null || !batch.active) {
            persistSession(
                session.copy(
                    stage = AccessibilityCacheStage.COMPLETED,
                    message = message.take(MAX_MESSAGE_LENGTH),
                ),
            )
            return null
        }

        val completed = batch.completedCount + 1
        val clearedBytes = batch.clearedEstimateBytes + session.estimatedBytes.coerceAtLeast(0)
        val nextIndex = batch.currentIndex + 1
        val nextItem = batch.items.getOrNull(nextIndex)
        val editor = preferences.edit()
            .putInt(KEY_BATCH_COMPLETED, completed)
            .putLong(KEY_BATCH_CLEARED_BYTES, clearedBytes)

        if (nextItem == null) {
            editor
                .putString(KEY_BATCH_STAGE, AccessibilityBatchStage.COMPLETED.name)
                .putString(KEY_BATCH_MESSAGE, "Кэш очищен у $completed приложений")
            putSession(
                editor,
                session.copy(
                    stage = AccessibilityCacheStage.COMPLETED,
                    message = message.take(MAX_MESSAGE_LENGTH),
                ),
            )
            editor.apply()
            return null
        }

        val next = newSession(nextItem, System.currentTimeMillis())
        editor
            .putInt(KEY_BATCH_INDEX, nextIndex)
            .putString(
                KEY_BATCH_MESSAGE,
                "Очищено $completed из ${batch.totalCount}. Открываем ${next.appLabel}",
            )
        putSession(editor, next)
        editor.apply()
        return next
    }

    fun fail(sessionId: String, message: String) {
        val session = readSession() ?: return
        if (session.id != sessionId || !session.active) return
        val safeMessage = message.take(MAX_MESSAGE_LENGTH)
        val editor = preferences.edit()
        putSession(
            editor,
            session.copy(
                stage = AccessibilityCacheStage.FAILED,
                message = safeMessage,
            ),
        )
        readBatch()?.takeIf { it.active }?.let { batch ->
            editor
                .putInt(KEY_BATCH_FAILED, batch.failedCount + 1)
                .putString(KEY_BATCH_STAGE, AccessibilityBatchStage.FAILED.name)
                .putString(
                    KEY_BATCH_MESSAGE,
                    "Остановлено на ${session.appLabel}: $safeMessage",
                )
        }
        editor.apply()
    }

    fun cancel(message: String = "Операция отменена") {
        val session = readSession()
        val batch = readBatch()
        if (session?.active != true && batch?.active != true) return
        val safeMessage = message.take(MAX_MESSAGE_LENGTH)
        val editor = preferences.edit()
        if (session != null && session.active) {
            putSession(
                editor,
                session.copy(
                    stage = AccessibilityCacheStage.CANCELLED,
                    message = safeMessage,
                ),
            )
        }
        if (batch != null && batch.active) {
            editor
                .putString(KEY_BATCH_STAGE, AccessibilityBatchStage.CANCELLED.name)
                .putString(KEY_BATCH_MESSAGE, safeMessage)
        }
        editor.apply()
    }

    fun markHistoryRecorded(sessionId: String) {
        updateSession(sessionId) { session -> session.copy(historyRecorded = true) }
    }

    fun markBatchHistoryRecorded(batchId: String) {
        val batch = readBatch() ?: return
        if (batch.id != batchId) return
        preferences.edit().putBoolean(KEY_BATCH_HISTORY_RECORDED, true).apply()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        publish()
    }

    private fun expireIfNeeded() {
        val session = readSession() ?: return
        if (session.active && System.currentTimeMillis() > session.expiresAtMillis) {
            fail(session.id, "Не удалось найти безопасную кнопку очистки кэша на этом системном экране")
        }
    }

    private fun updateSession(
        sessionId: String,
        transform: (AccessibilityCacheSession) -> AccessibilityCacheSession,
    ) {
        val session = readSession() ?: return
        if (session.id != sessionId) return
        persistSession(transform(session))
    }

    private fun newSession(
        item: AccessibilityCacheBatchItem,
        now: Long,
    ): AccessibilityCacheSession = AccessibilityCacheSession(
        id = UUID.randomUUID().toString(),
        packageName = item.packageName,
        appLabel = item.appLabel.take(MAX_LABEL_LENGTH),
        estimatedBytes = item.estimatedBytes.coerceAtLeast(0),
        stage = AccessibilityCacheStage.WAITING_APP_DETAILS,
        startedAtMillis = now,
        expiresAtMillis = now + SESSION_TIMEOUT_MILLIS,
        message = "Ожидание системной карточки приложения",
        historyRecorded = false,
    )

    private fun persistSession(session: AccessibilityCacheSession) {
        preferences.edit().also { putSession(it, session) }.apply()
    }

    private fun putSession(
        editor: SharedPreferences.Editor,
        session: AccessibilityCacheSession,
    ): SharedPreferences.Editor = editor
        .putString(KEY_SESSION_ID, session.id)
        .putString(KEY_PACKAGE, session.packageName)
        .putString(KEY_LABEL, session.appLabel)
        .putLong(KEY_ESTIMATED_BYTES, session.estimatedBytes)
        .putString(KEY_STAGE, session.stage.name)
        .putLong(KEY_STARTED_AT, session.startedAtMillis)
        .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
        .putString(KEY_MESSAGE, session.message)
        .putBoolean(KEY_HISTORY_RECORDED, session.historyRecorded)

    private fun publish() {
        _state.value = readState()
    }

    private fun readState(): AccessibilityCacheState = AccessibilityCacheState(
        consentAccepted = preferences.getBoolean(KEY_CONSENT, false),
        serviceEnabled = isServiceEnabled(),
        session = readSession(),
        batch = readBatch(),
    )

    private fun readSession(): AccessibilityCacheSession? {
        val id = preferences.getString(KEY_SESSION_ID, null) ?: return null
        val packageName = preferences.getString(KEY_PACKAGE, null) ?: return null
        val stageName = preferences.getString(KEY_STAGE, null) ?: return null
        val stage = runCatching { AccessibilityCacheStage.valueOf(stageName) }.getOrNull()
            ?: return null
        return AccessibilityCacheSession(
            id = id,
            packageName = packageName,
            appLabel = preferences.getString(KEY_LABEL, packageName).orEmpty(),
            estimatedBytes = preferences.getLong(KEY_ESTIMATED_BYTES, 0),
            stage = stage,
            startedAtMillis = preferences.getLong(KEY_STARTED_AT, 0),
            expiresAtMillis = preferences.getLong(KEY_EXPIRES_AT, 0),
            message = preferences.getString(KEY_MESSAGE, "").orEmpty(),
            historyRecorded = preferences.getBoolean(KEY_HISTORY_RECORDED, false),
        )
    }

    private fun readBatch(): AccessibilityCacheBatch? {
        val id = preferences.getString(KEY_BATCH_ID, null) ?: return null
        val items = deserializeItems(preferences.getString(KEY_BATCH_ITEMS, null))
        if (items.isEmpty()) return null
        val stageName = preferences.getString(KEY_BATCH_STAGE, null) ?: return null
        val stage = runCatching { AccessibilityBatchStage.valueOf(stageName) }.getOrNull()
            ?: return null
        return AccessibilityCacheBatch(
            id = id,
            items = items,
            currentIndex = preferences.getInt(KEY_BATCH_INDEX, 0).coerceIn(0, items.lastIndex),
            completedCount = preferences.getInt(KEY_BATCH_COMPLETED, 0).coerceAtLeast(0),
            failedCount = preferences.getInt(KEY_BATCH_FAILED, 0).coerceAtLeast(0),
            clearedEstimateBytes = preferences.getLong(KEY_BATCH_CLEARED_BYTES, 0).coerceAtLeast(0),
            stage = stage,
            message = preferences.getString(KEY_BATCH_MESSAGE, "").orEmpty(),
            historyRecorded = preferences.getBoolean(KEY_BATCH_HISTORY_RECORDED, false),
        )
    }

    private fun serializeItems(items: List<AccessibilityCacheBatchItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("package", item.packageName)
                    .put("label", item.appLabel.take(MAX_LABEL_LENGTH))
                    .put("bytes", item.estimatedBytes.coerceAtLeast(0)),
            )
        }
        return array.toString()
    }

    private fun deserializeItems(raw: String?): List<AccessibilityCacheBatchItem> = runCatching {
        val array = JSONArray(raw ?: return@runCatching emptyList())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val packageName = item.optString("package")
                if (!AccessibilityCachePolicy.isValidPackageName(packageName)) continue
                add(
                    AccessibilityCacheBatchItem(
                        packageName = packageName,
                        appLabel = item.optString("label").take(MAX_LABEL_LENGTH),
                        estimatedBytes = item.optLong("bytes", 0).coerceAtLeast(0),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun isServiceEnabled(): Boolean = runCatching {
        val expected = ComponentName(appContext, ClearUpAccessibilityService::class.java)
        accessibilityManager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo.serviceInfo
                ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
            }
    }.getOrDefault(false)

    companion object {
        private const val PREFERENCES = "clearup_accessibility_cache"
        private const val KEY_CONSENT = "consent"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_PACKAGE = "package"
        private const val KEY_LABEL = "label"
        private const val KEY_ESTIMATED_BYTES = "estimated_bytes"
        private const val KEY_STAGE = "stage"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_MESSAGE = "message"
        private const val KEY_HISTORY_RECORDED = "history_recorded"
        private const val KEY_BATCH_ID = "batch_id"
        private const val KEY_BATCH_ITEMS = "batch_items"
        private const val KEY_BATCH_INDEX = "batch_index"
        private const val KEY_BATCH_COMPLETED = "batch_completed"
        private const val KEY_BATCH_FAILED = "batch_failed"
        private const val KEY_BATCH_CLEARED_BYTES = "batch_cleared_bytes"
        private const val KEY_BATCH_STAGE = "batch_stage"
        private const val KEY_BATCH_MESSAGE = "batch_message"
        private const val KEY_BATCH_HISTORY_RECORDED = "batch_history_recorded"
        private const val SESSION_TIMEOUT_MILLIS = 90_000L
        private const val MAX_LABEL_LENGTH = 120
        private const val MAX_MESSAGE_LENGTH = 300
        private const val MAX_BATCH_ITEMS = 100
    }
}
