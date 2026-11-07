package com.aura.agent.core

import android.content.Context
import com.aura.agent.tools.AccessibilityTool
import com.aura.agent.tools.AppControlTool
import com.aura.agent.tools.CallLauncherTool
import com.aura.agent.tools.ContactLookupTool
import com.aura.agent.tools.DeviceControlTool
import com.aura.agent.tools.FileManagerTool
import com.aura.agent.tools.InstagramTool
import com.aura.agent.tools.MediaControlTool
import com.aura.agent.tools.ScreenObservationTool
import com.aura.agent.tools.WhatsAppTool
import com.aura.agent.tools.YouTubeTool

/** The only tools Gemini can reach. Anything not registered here cannot run. */
class ToolRegistry(tools: List<AuraTool>) {
    private val byName = tools.associateBy { it.name }

    fun get(name: String): AuraTool? = byName[name]
    fun all(): Collection<AuraTool> = byName.values

    companion object {
        fun build(context: Context, contacts: ContactLookupTool): ToolRegistry {
            val tools = buildList {
                addAll(AppControlTool(context).tools())
                addAll(DeviceControlTool(context).tools())
                addAll(MediaControlTool(context).tools())
                addAll(FileManagerTool(context).tools())
                addAll(ScreenObservationTool().tools())
                addAll(AccessibilityTool().tools())
                addAll(InstagramTool(context).tools())
                addAll(YouTubeTool(context).tools())
                addAll(WhatsAppTool(context, contacts).tools())
                addAll(CallLauncherTool(context, contacts).tools())
                addAll(contacts.tools())
            }
            return ToolRegistry(tools)
        }
    }
}
