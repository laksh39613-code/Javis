package com.aura.agent.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aura.agent.core.AuraTool
import com.aura.agent.core.LambdaTool
import com.aura.agent.core.Risk
import com.aura.agent.core.ToolResult
import com.aura.agent.core.pStr
import com.aura.agent.core.str

/**
 * Instagram navigation through official deep links only.
 * AURA never logs in, never bypasses private-account rules and never sends or likes anything
 * by itself. Viewing posts is done by the user on the official app, with optional screen reading.
 */
class InstagramTool(private val context: Context) {

    private val pkg = "com.instagram.android"

    fun tools(): List<AuraTool> = listOf(
        LambdaTool(
            name = "instagram_open_profile",
            description = "Open an Instagram profile in the official Instagram app by username (without @).",
            parameters = listOf(pStr("username", "Instagram username, e.g. rahul_official")),
            risk = Risk.SAFE,
        ) { args -> openProfile(args.str("username")?.removePrefix("@").orEmpty()) },

        LambdaTool(
            name = "instagram_open_messages",
            description = "Open the Instagram direct messages inbox. Does not send anything.",
            parameters = emptyList(),
            risk = Risk.SAFE,
        ) { _ -> openUri("instagram://direct/inbox", "https://www.instagram.com/direct/inbox/") },
    )

    private fun openProfile(username: String): ToolResult {
        if (username.isBlank() || !username.matches(Regex("[A-Za-z0-9._]{1,30}"))) {
            return ToolResult(false, "Username sahi nahi hai.")
        }
        return openUri(
            "instagram://user?username=$username",
            "https://www.instagram.com/$username/",
        ).let { if (it.success) it.copy(message = "@$username ka profile khol diya.") else it }
    }

    private fun openUri(appUri: String, webUri: String): ToolResult {
        val installed = try {
            context.packageManager.getPackageInfo(pkg, 0); true
        } catch (e: Exception) {
            false
        }
        val uri = if (installed) appUri else webUri
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (installed) intent.setPackage(pkg)
        return try {
            context.startActivity(intent)
            ToolResult(true, "Instagram khul raha hai: $uri")
        } catch (e: Exception) {
            ToolResult(false, "Instagram kholne me problem: ${e.message}")
        }
    }
}
