package com.norvexa.clearup.data.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

class ClearUpAccessibilityService : AccessibilityService() {
    private val coordinator by lazy { AccessibilityCacheCoordinator(applicationContext) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastAttemptKey: String? = null
    private var lastAttemptAtMillis: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        coordinator.refreshCapabilities()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val session = coordinator.activeSession() ?: return
        val packageName = event?.packageName?.toString() ?: return
        if (!AccessibilityCachePolicy.isAllowedSettingsPackage(packageName)) return
        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            return
        }

        val root = rootInActiveWindow ?: return
        if (!rootMatchesTarget(root, session)) return

        when (session.stage) {
            AccessibilityCacheStage.WAITING_APP_DETAILS -> {
                if (tryClearCache(root, session)) return
                tryOpenStorage(root, session)
            }
            AccessibilityCacheStage.WAITING_STORAGE_PAGE -> {
                tryClearCache(root, session)
            }
            AccessibilityCacheStage.COMPLETED,
            AccessibilityCacheStage.FAILED,
            AccessibilityCacheStage.CANCELLED,
            -> Unit
        }
    }

    override fun onInterrupt() {
        coordinator.activeSession()?.let { session ->
            coordinator.fail(session.id, "Accessibility-помощник был прерван системой")
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        coordinator.refreshCapabilities()
        coordinator.close()
        super.onDestroy()
    }

    private fun rootMatchesTarget(
        root: AccessibilityNodeInfo,
        session: AccessibilityCacheSession,
    ): Boolean = findNode(root) { node ->
        AccessibilityCachePolicy.matchesTarget(
            value = node.text,
            packageName = session.packageName,
            appLabel = session.appLabel,
        ) || AccessibilityCachePolicy.matchesTarget(
            value = node.contentDescription,
            packageName = session.packageName,
            appLabel = session.appLabel,
        )
    } != null

    private fun tryOpenStorage(
        root: AccessibilityNodeInfo,
        session: AccessibilityCacheSession,
    ): Boolean {
        if (!canAttempt("${session.id}:storage")) return false
        val target = findNode(root) { node ->
            isAllowedNodePackage(node) &&
                !hasDangerousLabel(node) &&
                (
                    AccessibilityCachePolicy.isExactStorageLabel(node.text) ||
                        AccessibilityCachePolicy.isExactStorageLabel(node.contentDescription) ||
                        AccessibilityCachePolicy.isStorageViewId(node.viewIdResourceName)
                    )
        } ?: return false

        return if (performSafeClick(target)) {
            coordinator.markWaitingStorage(session.id)
            true
        } else {
            false
        }
    }

    private fun tryClearCache(
        root: AccessibilityNodeInfo,
        session: AccessibilityCacheSession,
    ): Boolean {
        if (!canAttempt("${session.id}:clear")) return false
        val target = findNode(root) { node ->
            isAllowedNodePackage(node) &&
                !hasDangerousLabel(node) &&
                (
                    AccessibilityCachePolicy.isExactClearCacheLabel(node.text) ||
                        AccessibilityCachePolicy.isExactClearCacheLabel(node.contentDescription) ||
                        AccessibilityCachePolicy.isClearCacheViewId(node.viewIdResourceName)
                    )
        } ?: return false

        return if (performSafeClick(target)) {
            val next = coordinator.completeAndAdvance(
                sessionId = session.id,
                message = "Системная кнопка «Очистить кэш» нажата",
            )
            scheduleNext(next)
            true
        } else {
            false
        }
    }

    private fun scheduleNext(next: AccessibilityCacheSession?) {
        mainHandler.postDelayed(
            {
                if (next == null) {
                    returnToClearUp()
                } else {
                    openApplicationDetails(next.packageName)
                }
            },
            NEXT_APP_DELAY_MILLIS,
        )
    }

    private fun openApplicationDetails(packageName: String) {
        if (!AccessibilityCachePolicy.isValidPackageName(packageName)) return
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { error ->
            coordinator.activeSession()?.let { session ->
                coordinator.fail(
                    session.id,
                    error.message ?: "Система не открыла карточку следующего приложения",
                )
            }
        }
    }

    private fun returnToClearUp() {
        runCatching {
            packageManager.getLaunchIntentForPackage(applicationContext.packageName)
                ?.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                ?.let(::startActivity)
        }
    }

    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_VISITED_NODES) {
            val node = queue.removeFirst()
            visited += 1
            if (predicate(node)) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return null
    }

    private fun performSafeClick(source: AccessibilityNodeInfo): Boolean {
        var node: AccessibilityNodeInfo? = source
        repeat(MAX_PARENT_DEPTH + 1) {
            val current = node ?: return false
            if (hasDangerousLabel(current)) return false
            if (
                current.isEnabled &&
                current.isVisibleToUser &&
                current.isClickable &&
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return true
            }
            node = current.parent
        }
        return false
    }

    private fun hasDangerousLabel(node: AccessibilityNodeInfo): Boolean =
        AccessibilityCachePolicy.isDangerousLabel(node.text) ||
            AccessibilityCachePolicy.isDangerousLabel(node.contentDescription)

    private fun isAllowedNodePackage(node: AccessibilityNodeInfo): Boolean =
        AccessibilityCachePolicy.isAllowedSettingsPackage(node.packageName?.toString())

    private fun canAttempt(key: String): Boolean {
        val now = System.currentTimeMillis()
        if (lastAttemptKey == key && now - lastAttemptAtMillis < ACTION_DEBOUNCE_MILLIS) {
            return false
        }
        lastAttemptKey = key
        lastAttemptAtMillis = now
        return true
    }

    companion object {
        private const val ACTION_DEBOUNCE_MILLIS = 700L
        private const val NEXT_APP_DELAY_MILLIS = 550L
        private const val MAX_VISITED_NODES = 600
        private const val MAX_PARENT_DEPTH = 4
    }
}
