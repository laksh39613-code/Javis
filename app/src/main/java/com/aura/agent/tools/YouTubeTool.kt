package com.aura.agent.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aura.agent.accessibility.AuraAccessibilityService
import com.aura.agent.core.AuraTool
import com.aura.agent.core.LambdaTool
import com.aura.agent.core.Risk
import com.aura.agent.core.ToolResult
import com.aura.agent.core.pStr
import com.aura.agent.core.str
import kotlinx.coroutines.delay

/**
 * YouTube search through the official app's search URL. Playback is then controlled
 * with MediaControlTool, or the user picks a result (tap_text, REVERSIBLE).
 */
class YouTubeTool(private val context: Context) {

    private val pkg = "com.google.android.youtube"

    fun tools(): List<AuraTool> = listOf(
        LambdaTool(
            name = "youtube_search",
            description = "Open YouTube search results for a query, e.g. 'very sad songs', 'peaceful music', 'motivational songs'. Turn mood descriptions into a good search query.",
            parameters = listOf(pStr("query", "YouTube search query in English or Hindi")),
            risk = Risk.SAFE,
        ) { args -> search(args.str("query").orEmpty()) },
    )

    private suspend fun search(query: String): ToolResult {
        if (query.isBlank()) return ToolResult(false, "Search query khali hai.")
        val encoded = Uri.encode(query)
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://results?search_query=$encoded"))
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=$encoded")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(appIntent)
            val verified = AuraAccessibilityService.awaitForeground(pkg, timeoutMs = 4_000)
            ToolResult(
                true,
                "YouTube par \"$query\" search khul gaya" + if (verified) " (verified)." else ".",
                verified,
            )
        } catch (e: Exception) {
            context.startActivity(webIntent)
            ToolResult(true, "YouTube app nahi mila, browser me search khola: \"$query\".")
        }
    }
}
