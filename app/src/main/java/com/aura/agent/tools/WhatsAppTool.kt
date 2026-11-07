package com.aura.agent.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aura.agent.core.AuraTool
import com.aura.agent.core.ContactMatch
import com.aura.agent.core.LambdaTool
import com.aura.agent.core.Risk
import com.aura.agent.core.ToolResult
import com.aura.agent.core.pStr
import com.aura.agent.core.str

/**
 * WhatsApp helpers. AURA opens a chat or drafts a message; the user presses Send in WhatsApp.
 * WhatsApp does not expose an official intent to start a video call, so AURA opens the chat
 * and the user taps the video button themselves.
 */
class WhatsAppTool(
    private val context: Context,
    private val contacts: ContactLookupTool,
) {

    fun tools(): List<AuraTool> = listOf(
        LambdaTool(
            name = "whatsapp_draft_message",
            description = "Open WhatsApp with a contact and a pre-filled draft. The user must press Send. Never sends by itself.",
            parameters = listOf(
                pStr("contact_name", "Name of the contact as saved on the phone"),
                pStr("text", "Draft message text"),
            ),
            risk = Risk.REVERSIBLE,
        ) { args ->
            val match = contacts.findSingle(args.str("contact_name").orEmpty())
                ?: return@LambdaTool ToolResult(false, "Contact nahi mila ya ek se zyada match hue.")
            openChat(match, args.str("text").orEmpty())
        },

        LambdaTool(
            name = "whatsapp_prepare_video_call",
            description = "Open the WhatsApp chat of a contact so the user can tap the video call button. AURA does NOT start the call.",
            parameters = listOf(pStr("contact_name", "Name of the contact as saved on the phone")),
            risk = Risk.SENSITIVE,
        ) { args ->
            val match = contacts.findSingle(args.str("contact_name").orEmpty())
                ?: return@LambdaTool ToolResult(false, "Contact nahi mila ya ek se zyada match hue.")
            openChat(match, null).copy(
                message = "${match.name} ki WhatsApp chat khul gayi. Video call button aap khud tap karein."
            )
        },
    )

    private fun openChat(match: ContactMatch, text: String?): ToolResult {
        val digits = match.phone.filter { it.isDigit() }
        if (digits.length < 8) return ToolResult(false, "Is contact ka valid number nahi hai.")

        val builder = Uri.parse("https://wa.me/$digits").buildUpon()
        if (!text.isNullOrBlank()) builder.appendQueryParameter("text", text)
        val intent = Intent(Intent.ACTION_VIEW, builder.build())
            .setPackage("com.whatsapp")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolResult(true, "WhatsApp chat khul gayi (${match.name}).")
        } catch (e: Exception) {
            ToolResult(false, "WhatsApp nahi khul paya: ${e.message}")
        }
    }
}
