package com.aura.agent.tools

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.aura.agent.core.AuraTool
import com.aura.agent.core.LambdaTool
import com.aura.agent.core.Risk
import com.aura.agent.core.ToolResult
import com.aura.agent.core.pStr

/** Play/pause/next/previous through the system media session. Works with YouTube and other players. */
class MediaControlTool(private val context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun tools(): List<AuraTool> = listOf(
        LambdaTool(
            name = "media_control",
            description = "Control the currently playing media: play_pause, next, previous.",
            parameters = listOf(pStr("action", "Media action", enum = listOf("play_pause", "next", "previous"))),
            risk = Risk.SAFE,
        ) { args ->
            val key = when (args["action"]?.toString()) {
                "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> return@LambdaTool ToolResult(false, "Unknown media action.")
            }
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            // Media keys give no direct feedback, so this is not marked verified.
            ToolResult(true, "Media command bheja gaya: ${args["action"]}.")
        },
    )
}
