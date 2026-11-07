package com.aura.agent.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.aura.agent.core.AuraTool
import com.aura.agent.core.ContactMatch
import com.aura.agent.core.LambdaTool
import com.aura.agent.core.Risk
import com.aura.agent.core.ToolResult
import com.aura.agent.core.pStr
import com.aura.agent.core.str

/** Phone calls. Real calls are IRREVERSIBLE and only run after the user confirms. */
class CallLauncherTool(
    private val context: Context,
    private val contacts: ContactLookupTool,
) {

    fun tools(): List<AuraTool> = listOf(
        LambdaTool(
            name = "phone_open_dialer",
            description = "Open the phone dialer with a contact's number. The user presses call.",
            parameters = listOf(pStr("contact_name", "Contact name as saved on the phone")),
            risk = Risk.REVERSIBLE,
        ) { args ->
            val match = contacts.findSingle(args.str("contact_name").orEmpty())
                ?: return@LambdaTool ToolResult(false, "Contact nahi mila ya ek se zyada match hue.")
            dial(match.phone, direct = false, name = match.name)
        },

        LambdaTool(
            name = "phone_start_call",
            description = "Start a phone call to a contact immediately. Only after the user explicitly confirmed.",
            parameters = listOf(pStr("contact_name", "Contact name as saved on the phone")),
            risk = Risk.IRREVERSIBLE,
        ) { args ->
            val match = contacts.findSingle(args.str("contact_name").orEmpty())
                ?: return@LambdaTool ToolResult(false, "Contact nahi mila ya ek se zyada match hue.")
            dial(match.phone, direct = true, name = match.name)
        },
    )

    private fun dial(number: String, direct: Boolean, name: String): ToolResult {
        val canDirect = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val action = if (direct && canDirect) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            val note = when {
                action == Intent.ACTION_CALL -> "$name ko call shuru ho gaya."
                direct -> "CALL_PHONE permission nahi hai, dialer khol diya. $name ke liye call button dabayein."
                else -> "Dialer me $name ka number khul gaya."
            }
            ToolResult(true, note)
        } catch (e: Exception) {
            ToolResult(false, "Call/dialer nahi khul paya: ${e.message}")
        }
    }
}
