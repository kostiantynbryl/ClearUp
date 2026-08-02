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

enum class AccessibilityCacheStage {
    WAITING_APP_DETAILS,
    WAITING_STORAGE_PAGE,
    COMPLETED,
    FAILED,
    CANCELLED,
}

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

data class AccessibilityCacheState(
    val consentAccepted: Boolean = false,
    val serviceEnabled: Boolean = false,
    val session: AccessibilityCacheSession? = null,
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
    ): AccessibilityCacheSession {
        require(AccessibilityCachePolicy.isValidPackageName(packageName)) {
            "Invalid package name"
        }
        val current = readState()
        check(current.consentAccepted) {
            "Сначала подтвердите согласие на Accessibility-помощник"
        }
        check(current.serviceEnabled) {
            "Accessibility-помощник не включён в системных настройках"
        }

        val now = System.currentTimeMillis()
        val session = AccessibilityCacheSession(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            appLabel = appLabel.take(MAX_LABEL_LENGTH),
            estimatedBytes = estimatedBytes.coerceAtLeast(0),
            stage = AccessibilityCacheStage.WAITING_APP_DETAILS,
            startedAtMillis = now,
            expiresAtMillis = now + SESSION_TIMEOUT_MILLIS,
            message = "Ожидание системной карточки приложения",
            historyRecorded = false,
        )
        persist(session)
        return session
    }

    fun activeSession(): AccessibilityCacheSession? {
        expireIfNeeded()
        return readSession()?.takeIf { it.active }
    }

    fun markWaitingStorage(sessionId: String) {
        update(sessionId) { session ->
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

    fun complete(sessionId: String, message: String = "Команда очистки кэша отправлена") {
        update(sessionId) { session ->
            session.copy(
                stage = AccessibilityCacheStage.COMPLETED,
                message = message.take(MAX_MESSAGE_LENGTH),
            )
        }
    }

    fun fail(sessionId: String, message: String) {
        update(sessionId) { session ->
            if (!session.active) session else session.copy(
                stage = AccessibilityCacheStage.FAILED,
                message = message.take(MAX_MESSAGE_LENGTH),
            )
        }
    }

    fun cancel(message: String = "Операция отменена") {
        val session = readSession() ?: return
        if (!session.active) return
        persist(
            session.copy(
                stage = AccessibilityCacheStage.CANCELLED,
                message = message.take(MAX_MESSAGE_LENGTH),
            ),
        )
    }

    fun markHistoryRecorded(sessionId: String) {
        update(sessionId) { session -> session.copy(historyRecorded = true) }
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
            persist(
                session.copy(
                    stage = AccessibilityCacheStage.FAILED,
                    message = "Время ожидания системного экрана истекло",
                ),
            )
        }
    }

    private fun update(
        sessionId: String,
        transform: (AccessibilityCacheSession) -> AccessibilityCacheSession,
    ) {
        val session = readSession() ?: return
        if (session.id != sessionId) return
        persist(transform(session))
    }

    private fun persist(session: AccessibilityCacheSession) {
        preferences.edit()
            .putString(KEY_SESSION_ID, session.id)
            .putString(KEY_PACKAGE, session.packageName)
            .putString(KEY_LABEL, session.appLabel)
            .putLong(KEY_ESTIMATED_BYTES, session.estimatedBytes)
            .putString(KEY_STAGE, session.stage.name)
            .putLong(KEY_STARTED_AT, session.startedAtMillis)
            .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
            .putString(KEY_MESSAGE, session.message)
            .putBoolean(KEY_HISTORY_RECORDED, session.historyRecorded)
            .apply()
    }

    private fun publish() {
        _state.value = readState()
    }

    private fun readState(): AccessibilityCacheState = AccessibilityCacheState(
        consentAccepted = preferences.getBoolean(KEY_CONSENT, false),
        serviceEnabled = isServiceEnabled(),
        session = readSession(),
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
        private const val SESSION_TIMEOUT_MILLIS = 90_000L
        private const val MAX_LABEL_LENGTH = 120
        private const val MAX_MESSAGE_LENGTH = 300
    }
}
