package com.aura.agent.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.aura.agent.core.AuraTool
import com.aura.agent.core.ContactMatch
import com.aura.agent.core.LambdaTool
import com.aura.agent.core.Risk
import com.aura.agent.core.ToolResult
import com.aura.agent.core.pStr
import com.aura.agent.core.str

/**
 * Looks up saved contacts. Off by default: the user must enable "Contacts access for AURA"
 * in settings, because contact names and numbers are sent to Gemini as part of the reply.
 */
class ContactLookupTool(private val context: Context) {

    private val prefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)

    fun isConsentGiven(): Boolean = prefs.getBoolean(KEY_CONSENT, false)

    fun tools(): List<AuraTool> = listOf(
        LambdaTool(
            name = "find_contact",
            description = "Search saved contacts by name. Returns up to 5 matches with name and phone.",
            parameters = listOf(pStr("name", "Full or partial contact name")),
            risk = Risk.SAFE,
        ) { args ->
            if (!isConsentGiven()) {
                return@LambdaTool ToolResult(false, "Contacts access band hai. Settings me on karein.")
            }
            val matches = search(args.str("name").orEmpty())
            if (matches.isEmpty()) ToolResult(false, "Koi contact nahi mila.")
            else ToolResult(true, matches.joinToString("\n") { "${it.name}: ${it.phone}" }, verified = true)
        },
    )

    /** Used by other tools: returns a match only when exactly one contact fits. */
    fun findSingle(name: String): ContactMatch? {
        if (!isConsentGiven() || name.isBlank()) return null
        val matches = search(name).distinctBy { it.phone }
        return matches.singleOrNull()
    }

    private fun search(query: String): List<ContactMatch> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        val results = mutableListOf<ContactMatch>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%${query.trim()}%"),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext() && results.size < 5) {
                val n = cursor.getString(0) ?: continue
                val p = cursor.getString(1) ?: continue
                results += ContactMatch(n, p)
            }
        }
        return results
    }

    companion object {
        const val KEY_CONSENT = "contacts_consent"
    }
}
