package com.aura.agent.core

/**
 * Decides how risky a planned call is and whether it must be blocked outright.
 * Tap targets are inspected because the same tool can press harmless or harmful buttons.
 */
object RiskPolicy {

    // Taps on these labels are never allowed through AURA.
    private val blockedWords = listOf(
        "password", "passcode", "otp", "pin", "upi", "transfer", "pay now",
        "factory reset", "delete account", "deactivate", "remove device"
    )

    private val irreversibleWords = listOf(
        "send", "delete", "remove", "reset", "post", "publish", "call", "buy",
        "pay", "confirm", "uninstall", "block", "report", "submit", "share"
    )

    private val sensitiveWords = listOf(
        "follow", "unfollow", "like", "comment", "message", "reply", "invite"
    )

    /** Returns a human-readable reason when the call must not run, otherwise null. */
    fun blockReason(call: ToolCall): String? {
        if (call.name != "tap_text") return null
        val target = call.args.str("text")?.lowercase().orEmpty()
        val hit = blockedWords.firstOrNull { target.contains(it) } ?: return null
        return "AURA ye action nahi karega (\"$hit\" wale buttons block hain). Ye step khud karein."
    }

    fun classify(call: ToolCall, registry: ToolRegistry): Risk {
        val base = registry.get(call.name)?.risk ?: return Risk.IRREVERSIBLE
        if (call.name != "tap_text") return base

        val target = call.args.str("text")?.lowercase().orEmpty()
        return when {
            irreversibleWords.any { target.contains(it) } -> Risk.IRREVERSIBLE
            sensitiveWords.any { target.contains(it) } -> Risk.SENSITIVE
            else -> Risk.REVERSIBLE
        }
    }
}
