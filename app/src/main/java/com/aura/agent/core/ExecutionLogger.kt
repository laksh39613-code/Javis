package com.aura.agent.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-memory execution log shown in the app. Keeps the last 300 lines. */
class ExecutionLogger {
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(message: String) {
        val line = "[${formatter.format(Date())}] $message"
        _entries.update { (it + line).takeLast(300) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
