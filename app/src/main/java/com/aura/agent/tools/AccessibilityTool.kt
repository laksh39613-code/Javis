package com.aura.agent.tools

import com.aura.agent.accessibility.AuraAccessibilityService
import com.aura.agent.core.AuraTool
import com.aura.agent.core.LambdaTool
import com.aura.agent.core.Risk
import com.aura.agent.core.ToolResult
import com.aura.agent.core.pStr
import com.aura.agent.core.str
import kotlinx.coroutines.delay

/**
 * Permitted taps, scrolling and navigation. Taps are classified by RiskPolicy from the
 * target text before they reach this tool. Retries are limited to 3 attempts.
 */
class AccessibilityTool {

    fun tools(): List<AuraTool> = listOf(
        LambdaTool(
            name = "tap_text",
            description = "Tap the on-screen element whose visible text matches. Use exact labels seen with read_screen. Sending, deleting, paying or confirming buttons need user confirmation.",
            parameters = listOf(pStr("text", "Visible text of the element to tap")),
            risk = Risk.REVERSIBLE, // refined per target by RiskPolicy.classify
        ) { args -> tap(args.str("text").orEmpty()) },

        LambdaTool(
            name = "scroll_screen",
            description = "Scroll the current screen up (forward) or down (backward).",
            parameters = listOf(pStr("direction", "Scroll direction", enum = listOf("up", "down"))),
            risk = Risk.SAFE,
        ) { args -> scroll(forward = args.str("direction") != "up") },

        LambdaTool(
            name = "go_back",
            description = "Press the system Back button.",
            parameters = emptyList(),
            risk = Risk.SAFE,
        ) { _ -> global(AuraAccessibilityService.GLOBAL_BACK, "Back press kiya.") },

        LambdaTool(
            name = "go_home",
            description = "Go to the phone home screen.",
            parameters = emptyList(),
            risk = Risk.SAFE,
        ) { _ -> global(AuraAccessibilityService.GLOBAL_HOME, "Home screen par gaye.") },
    )

    private suspend fun tap(text: String): ToolResult {
        val service = AuraAccessibilityService.instance
            ?: return ToolResult(false, "Accessibility service on nahi hai.")
        if (text.isBlank()) return ToolResult(false, "Tap ka text khali hai.")

        repeat(MAX_ATTEMPTS) { attempt ->
            val before = AuraAccessibilityService.lastWindowChangeAt
            if (service.clickText(text)) {
                // Verify: wait briefly for the screen to change after the tap.
                val changed = AuraAccessibilityService.awaitChange(since = before, timeoutMs = 2_000)
                return ToolResult(
                    true,
                    "\"$text\" par tap kiya" + if (changed) " (screen change verified)." else ".",
                    verified = changed,
                )
            }
            if (attempt < MAX_ATTEMPTS - 1) delay(600)
        }
        return ToolResult(false, "\"$text\" screen par nahi mila.")
    }

    private fun scroll(forward: Boolean): ToolResult {
        val service = AuraAccessibilityService.instance
            ?: return ToolResult(false, "Accessibility service on nahi hai.")
        return if (service.scroll(forward)) ToolResult(true, "Scroll ho gaya.")
        else ToolResult(false, "Is screen par scrollable area nahi mila.")
    }

    private fun global(action: Int, message: String): ToolResult {
        val service = AuraAccessibilityService.instance
            ?: return ToolResult(false, "Accessibility service on nahi hai.")
        return ToolResult(service.performGlobal(action), message)
    }

    companion object {
        const val MAX_ATTEMPTS = 3
    }
}
