package com.aura.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * Accessibility fallback for screen reading and permitted taps.
 * Skips password fields. Only exposes state through the companion object, so tools
 * never hold a reference to the service longer than needed.
 */
class AuraAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                foregroundPackage = event.packageName?.toString()
                lastWindowChangeAt = now
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> lastWindowChangeAt = now
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** Visible, non-password text and content descriptions, top to bottom. */
    fun visibleTexts(limit: Int): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = mutableListOf<String>()
        collect(root, out, limit)
        return out
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>, limit: Int) {
        if (node == null || out.size >= limit) return
        if (node.isVisibleToUser && !node.isPassword) {
            val label = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            if (label != null) out.add(label.trim())
        }
        for (i in 0 until node.childCount) collect(node.getChild(i), out, limit)
    }

    /** Finds a node with the given text and clicks it or its nearest clickable parent. */
    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = root.findAccessibilityNodeInfosByText(text).orEmpty()
        for (node in candidates) {
            if (node.isPassword) continue
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }
        return false
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        return findScrollable(root)?.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        ) ?: false
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            findScrollable(node.getChild(i))?.let { return it }
        }
        return null
    }

    fun performGlobal(action: Int): Boolean = performGlobalAction(action)

    companion object {
        const val GLOBAL_BACK = AccessibilityService.GLOBAL_ACTION_BACK
        const val GLOBAL_HOME = AccessibilityService.GLOBAL_ACTION_HOME

        @Volatile var instance: AuraAccessibilityService? = null
        @Volatile var foregroundPackage: String? = null
        @Volatile var lastWindowChangeAt: Long = 0L

        /** Polls until [pkg] is in front or the timeout expires. */
        suspend fun awaitForeground(pkg: String, timeoutMs: Long): Boolean {
            val end = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < end) {
                if (foregroundPackage == pkg) return true
                delay(250)
            }
            return false
        }

        /** Waits until a window change newer than [since] is seen. */
        suspend fun awaitChange(since: Long, timeoutMs: Long): Boolean {
            val end = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < end) {
                if (lastWindowChangeAt > since) return true
                delay(200)
            }
            return false
        }
    }
}
