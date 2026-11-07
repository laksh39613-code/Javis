package com.aura.agent.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed class PlannerOutput {
    data class Calls(val calls: List<ToolCall>, val rawContent: JSONObject) : PlannerOutput()
    data class Text(val text: String) : PlannerOutput()
}

/**
 * Talks to Gemini using structured function calling.
 * Gemini only ever sees the declarations from [ToolRegistry]; it cannot run shell
 * commands or arbitrary code. Every call it returns is validated by the orchestrator.
 */
class GeminiPlanner(
    private val apiKey: String,
    private val registry: ToolRegistry,
    private val model: String = "gemini-2.0-flash",
) {
    private val endpoint get() =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    suspend fun send(contents: JSONArray): PlannerOutput = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
            )
            .put("contents", contents)
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations())))
            .put(
                "toolConfig",
                JSONObject().put("functionCallingConfig", JSONObject().put("mode", "AUTO"))
            )

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Gemini HTTP $code: ${text.take(300)}")
            parse(JSONObject(text))
        } finally {
            conn.disconnect()
        }
    }

    private fun declarations(): JSONArray {
        val arr = JSONArray()
        for (tool in registry.all()) {
            val props = JSONObject()
            val required = JSONArray()
            for (p in tool.parameters) {
                val schema = JSONObject()
                    .put("type", p.type)
                    .put("description", p.description)
                p.enumValues?.let { schema.put("enum", JSONArray(it)) }
                props.put(p.name, schema)
                if (p.required) required.put(p.name)
            }
            arr.put(
                JSONObject()
                    .put("name", tool.name)
                    .put("description", tool.description)
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "OBJECT")
                            .put("properties", props)
                            .put("required", required)
                    )
            )
        }
        return arr
    }

    private fun parse(json: JSONObject): PlannerOutput {
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            ?: return PlannerOutput.Text("Gemini se jawab nahi mila.")
        val content = candidate.optJSONObject("content")
            ?: return PlannerOutput.Text("Gemini se khaali jawab mila.")
        val parts = content.optJSONArray("parts") ?: JSONArray()

        val calls = mutableListOf<ToolCall>()
        val text = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            part.optJSONObject("functionCall")?.let { fc ->
                calls += ToolCall(fc.getString("name"), fc.optJSONObject("args")?.toMap().orEmpty())
            }
            part.optString("text").takeIf { it.isNotBlank() }?.let { text.append(it) }
        }
        return if (calls.isNotEmpty()) PlannerOutput.Calls(calls, content)
        else PlannerOutput.Text(text.toString().ifBlank { "Done." })
    }

    companion object {
        private const val SYSTEM_PROMPT = """
You are AURA, an Android phone assistant. The user speaks Hindi, Hinglish or English.
Rules:
- Use the provided tools to act. Never invent tool results.
- Never claim an action succeeded unless the tool result says verified=true. If verified=false, say it was attempted and what the user should check.
- For messages, calls, likes, follows, comments, deletions or purchases, only prepare the action; the user confirms and performs the final tap.
- Never try to bypass logins, passwords, OTPs, private-account limits or security screens. If a login screen appears, tell the user to log in themselves.
- Keep final answers short and in the user's language.
"""
    }
}

/** Convert a org.json object to a plain Map so tools can read arguments easily. */
fun JSONObject.toMap(): Map<String, Any?> {
    val out = mutableMapOf<String, Any?>()
    val keys = keys()
    while (keys.hasNext()) {
        val k = keys.next()
        val v = opt(k)
        out[k] = if (v == JSONObject.NULL) null else v
    }
    return out
}
