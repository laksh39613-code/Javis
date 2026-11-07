package com.aura.agent.tools

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import com.aura.agent.core.AuraTool
import com.aura.agent.core.LambdaTool
import com.aura.agent.core.Risk
import com.aura.agent.core.ToolResult
import com.aura.agent.core.bool
import com.aura.agent.core.int
import com.aura.agent.core.pBool
import com.aura.agent.core.pInt
import com.aura.agent.core.pStr
import com.aura.agent.core.str
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Device settings: brightness, volume, flashlight, Do Not Disturb, settings pages,
 * alarms, timers and calendar events. Each change is read back when Android allows it.
 */
class DeviceControlTool(private val context: Context) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val camera = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun tools(): List<AuraTool> = listOf(
        LambdaTool(
            name = "set_brightness",
            description = "Set screen brightness to a percentage 0-100. Needs the Modify system settings permission; otherwise opens that screen.",
            parameters = listOf(pInt("percent", "Brightness 0 to 100")),
            risk = Risk.SAFE,
        ) { args -> setBrightness((args.int("percent") ?: 50).coerceIn(0, 100)) },

        LambdaTool(
            name = "set_volume",
            description = "Set media volume to a percentage 0-100.",
            parameters = listOf(pInt("percent", "Volume 0 to 100")),
            risk = Risk.SAFE,
        ) { args ->
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = ((args.int("percent") ?: 50).coerceIn(0, 100) * max) / 100
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            val actual = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            ToolResult(actual == target, "Volume set: $actual/$max", verified = actual == target)
        },

        LambdaTool(
            name = "set_flashlight",
            description = "Turn the flashlight (torch) on or off.",
            parameters = listOf(pBool("on", "true to turn on, false to turn off")),
            risk = Risk.SAFE,
        ) { args -> setTorch(args.bool("on") ?: return@LambdaTool ToolResult(false, "on value missing")) },

        LambdaTool(
            name = "set_do_not_disturb",
            description = "Turn Do Not Disturb on or off. Needs notification policy access; otherwise opens that screen.",
            parameters = listOf(pBool("enabled", "true to enable DND")),
            risk = Risk.REVERSIBLE,
        ) { args -> setDnd(args.bool("enabled") ?: return@LambdaTool ToolResult(false, "enabled value missing")) },

        LambdaTool(
            name = "open_settings_page",
            description = "Open an Android settings page.",
            parameters = listOf(
                pStr(
                    "page", "Settings page to open",
                    enum = listOf("wifi", "bluetooth", "display", "sound", "notifications", "dnd", "location", "battery", "main"),
                ),
            ),
            risk = Risk.SAFE,
        ) { args -> openSettings(args.str("page").orEmpty()) },

        LambdaTool(
            name = "create_alarm",
            description = "Create an alarm at a 24-hour time, with a label.",
            parameters = listOf(
                pInt("hour", "Hour 0-23"),
                pInt("minute", "Minute 0-59"),
                pStr("label", "Alarm label", required = false),
            ),
            risk = Risk.REVERSIBLE,
        ) { args ->
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, args.int("hour") ?: 7)
                .putExtra(AlarmClock.EXTRA_MINUTES, args.int("minute") ?: 0)
                .putExtra(AlarmClock.EXTRA_MESSAGE, args.str("label") ?: "AURA alarm")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            start(intent, "Alarm set kiya gaya.")
        },

        LambdaTool(
            name = "set_timer",
            description = "Start a countdown timer for a number of seconds.",
            parameters = listOf(pInt("seconds", "Timer length in seconds")),
            risk = Risk.REVERSIBLE,
        ) { args ->
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, args.int("seconds") ?: 60)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            start(intent, "Timer start kiya gaya.")
        },

        LambdaTool(
            name = "create_calendar_event",
            description = "Open the calendar app with a new event pre-filled. start_datetime is local ISO format yyyy-MM-ddTHH:mm.",
            parameters = listOf(
                pStr("title", "Event title"),
                pStr("start_datetime", "Local start time, e.g. 2026-11-08T18:30"),
                pInt("duration_minutes", "Duration in minutes", required = false),
            ),
            risk = Risk.REVERSIBLE,
        ) { args ->
            val start = try {
                LocalDateTime.parse(args.str("start_datetime").orEmpty())
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) {
                return@LambdaTool ToolResult(false, "Date format galat hai (yyyy-MM-ddTHH:mm chahiye).")
            }
            val minutes = (args.int("duration_minutes") ?: 60).toLong()
            val intent = Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + minutes * 60_000)
                .putExtra(CalendarContract.Events.TITLE, args.str("title") ?: "AURA event")
            start(intent, "Calendar me event draft khul gaya. Save aap karein.")
        },
    )

    private fun setBrightness(percent: Int): ToolResult {
        if (!Settings.System.canWrite(context)) {
            openAction(Settings.ACTION_MANAGE_WRITE_SETTINGS, "Brightness ke liye 'Modify system settings' permission dein.")
            return ToolResult(false, "Write-settings permission nahi hai, settings khol di.")
        }
        val raw = (percent * 255) / 100
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
        val actual = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        val ok = actual == raw
        return ToolResult(ok, "Brightness ~$percent% set kiya.", verified = ok)
    }

    private fun setTorch(on: Boolean): ToolResult {
        val id = camera.cameraIdList.firstOrNull { idValue ->
            camera.getCameraCharacteristics(idValue)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ToolResult(false, "Is device me flashlight nahi hai.")
        camera.setTorchMode(id, on)
        return ToolResult(true, "Flashlight ${if (on) "on" else "off"} kar diya.")
    }

    private fun setDnd(enabled: Boolean): ToolResult {
        if (!notifications.isNotificationPolicyAccessGranted) {
            openAction(
                Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
                "DND ke liye AURA ko 'Do Not Disturb access' dein.",
            )
            return ToolResult(false, "DND access nahi hai, settings khol di.")
        }
        val filter = if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
        else NotificationManager.INTERRUPTION_FILTER_ALL
        notifications.setInterruptionFilter(filter)
        val ok = notifications.currentInterruptionFilter == filter
        return ToolResult(ok, "DND ${if (enabled) "on" else "off"}.", verified = ok)
    }

    private fun openSettings(page: String): ToolResult {
        val action = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            "dnd" -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return openAction(action, "$page settings khol di gayi.")
    }

    private fun openAction(action: String, message: String): ToolResult {
        val intent = Intent(action)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolResult(true, message)
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult(true, "Settings khol di (direct screen nahi mili).")
        }
    }

    private fun start(intent: Intent, message: String): ToolResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            return ToolResult(false, "Is kaam ke liye koi app available nahi hai.")
        }
        context.startActivity(intent)
        // Alarm/timer/calendar apps do not report back, so this is not marked verified.
        return ToolResult(true, message)
    }
}
