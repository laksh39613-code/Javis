package com.aura.agent.tools

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.aura.agent.core.AuraTool
import com.aura.agent.core.LambdaTool
import com.aura.agent.core.Risk
import com.aura.agent.core.ToolResult
import com.aura.agent.core.pStr
import com.aura.agent.core.str

/**
 * Finds the newest file in Downloads by extension and opens it with the right app.
 * Uses MediaStore (no broad storage access). Falls back to the Files app.
 */
class FileManagerTool(private val context: Context) {

    fun tools(): List<AuraTool> = listOf(
        LambdaTool(
            name = "find_latest_download",
            description = "Find the most recently downloaded file with an extension (e.g. pdf, jpg, apk) and open it.",
            parameters = listOf(pStr("extension", "File extension without dot, e.g. pdf")),
            risk = Risk.SAFE,
        ) { args -> findLatest(args.str("extension")?.trim('.', ' ')?.lowercase().orEmpty()) },

        LambdaTool(
            name = "open_files_app",
            description = "Open the device Files / file manager app.",
            parameters = emptyList(),
            risk = Risk.SAFE,
        ) { _ -> openFilesApp() },
    )

    private fun findLatest(ext: String): ToolResult {
        if (ext.isBlank()) return ToolResult(false, "File extension missing.")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return openFilesApp().copy(message = "Android version purana hai, Files app khol di.")
        }
        if (!hasReadPermission()) {
            return ToolResult(false, "Storage/media read permission chahiye.")
        }

        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.MIME_TYPE,
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%.$ext")
        val sort = "${MediaStore.Downloads.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, sort
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
                val mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE))
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)

                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(intent)
                    ToolResult(true, "Latest .$ext file mili: $name. Khol di gayi.", verified = true)
                } catch (e: Exception) {
                    ToolResult(false, "$name mili, lekin koi app ise khol nahi paya.")
                }
            }
        }
        return ToolResult(false, "Downloads me koi .$ext file nahi mili.")
    }

    private fun hasReadPermission(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return perms.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun openFilesApp(): ToolResult {
        val intent = Intent(Intent.ACTION_VIEW)
            .setType("resource/folder")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolResult(true, "Files app khul gaya.")
        } catch (e: Exception) {
            ToolResult(false, "Files app nahi khul paya.")
        }
    }
}
