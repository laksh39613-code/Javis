package com.aura.agent.tools

import com.aura.agent.accessibility.AuraAccessibilityService
import com.aura.agent.core.AuraTool
import com.aura.agent.core.LambdaTool
import com.aura.agent.core.Risk
import com.aura.agent.core.ToolResult

/**
 * Reads visible text from the current screen through the accessibility tree.
 * Password fields are skipped by the service. Screenshot/vision analysis is intentionally
 * NOT implemented here: it needs a separate, explicit, per-use user consent flow.
 */
class ScreenObservationTool {

    private val loginHints = listOf("log in", "login", "sign in", "enter password", "verify it's you")
    private val errorHints = listOf("something went wrong", "try again", "no internet", "connection error")
    private val loadingHints = listOf("loading", "please wait")

    fun tools(): List<AuraTool> = listOf(
        LambdaTool(
            name = "read_screen",
            description = "Read the visible text on the current screen and detect login, loading or error states. Use it to verify what is on screen.",
            parameters = emptyList(),
            risk = Risk.SAFE,
        ) { _ -> read() },
    )

    private fun read(): ToolResult {
        val service = AuraAccessibilityService.instance
            ?: return ToolResult(false, "Accessibility service on nahi hai. Settings > Accessibility > AURA on karein.")

        val texts = service.visibleTexts(limit = 60)
        if (texts.isEmpty()) {
            return ToolResult(false, "Screen par padhne layak text nahi mila (loading ya non-accessible screen ho sakti hai).")
        }
        val joined = texts.joinToString(" | ")
        val lower = joined.lowercase()

        val state = when {
            loginHints.any { lower.contains(it) } ->
                "LOGIN SCREEN: user ko khud login karna hoga, AURA login nahi karega."
            errorHints.any { lower.contains(it) } -> "ERROR state dikh raha hai."
            loadingHints.any { lower.contains(it) } -> "LOADING state: thoda ruk kar dobara read karein."
            else -> "OK"
        }
        return ToolResult(true, "State: $state\nText: ${joined.take(3000)}", verified = true)
    }
}
