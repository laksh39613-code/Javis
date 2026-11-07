package com.aura.agent.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.aura.agent.accessibility.AuraAccessibilityService
import com.aura.agent.core.LambdaTool
import com.aura.agent.core.AuraTool
import com.aura.agent.core.Risk
import com.aura.agent.core.ToolResult
import com.aura.agent.core.pStr
import com.aura.agent.core.str
import kotlinx.coroutines.delay

/** Launches installed apps, switches apps and opens the gallery. Uses Android intents first. */
class AppControlTool(private val context: Context) {

    private val aliases = mapOf(
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "camera" to "com.android.camera",
        "gallery" to "com.google.android.apps.photos",
        "photos" to "com.google.android.apps.photos",
        "files" to "com.google.android.documentsui",
        "settings" to "com.android.settings",
    )

    fun tools(): List<AuraTool> = listOf(
        LambdaTool(
            name = "open_app",
            description = "Open an installed Android app by its name, e.g. Instagram, WhatsApp, YouTube.",
            parameters = listOf(pStr("app_name", "Human name of the app to open")),
            risk = Risk.SAFE,
        ) { args -> openApp(args.str("app_name").orEmpty()) },

        LambdaTool(
            name = "open_gallery",
            description = "Open the gallery / photos app showing images.",
            parameters = emptyList(),
            risk = Risk.SAFE,
        ) { _ -> openGallery() },
    )

    private suspend fun openApp(name: String): ToolResult {
        val pkg = findPackage(name)
            ?: return ToolResult(false, "\"$name\" naam ka app installed nahi mila.")
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return ToolResult(false, "$name ko launch nahi kar sakte.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        // Verify through the accessibility service when it is enabled.
        val verified = AuraAccessibilityService.awaitForeground(pkg, timeoutMs = 4_000)
        return ToolResult(
            success = true,
            message = if (verified) "$name khul gaya (verified)."
            else "$name launch kiya gaya. Screen par confirm nahi hua (accessibility on karein).",
            verified = verified,
        )
    }

    private fun openGallery(): ToolResult {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://media/external/images/media"), "image/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolResult(true, "Gallery khol di gayi.")
        } catch (e: Exception) {
            val pkg = aliases["gallery"] ?: return ToolResult(false, "Gallery app nahi mila.")
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return ToolResult(false, "Gallery app nahi mila.")
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult(true, "Gallery app khol di gayi.")
        }
    }

    /** Matches the label exactly, then by contains, then by known alias. */
    fun findPackage(name: String): String? {
        val wanted = name.trim().lowercase()
        aliases[wanted]?.let { if (isInstalled(it)) return it }

        val pm = context.packageManager
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val labels = launchable.map { ri ->
            val label = ri.loadLabel(pm).toString()
            label to ri.activityInfo.packageName
        }
        labels.firstOrNull { it.first.equals(wanted, ignoreCase = true) }?.let { return it.second }
        labels.firstOrNull { it.first.contains(wanted, ignoreCase = true) }?.let { return it.second }
        return null
    }

    private fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
