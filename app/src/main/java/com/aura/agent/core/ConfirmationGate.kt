package com.aura.agent.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Suspends the agent until the user approves or rejects a sensitive step.
 * No answer within the timeout counts as rejection.
 */
class ConfirmationGate {

    data class Request(val id: Long, val description: String, val answer: CompletableDeferred<Boolean>)

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    private var counter = 0L

    suspend fun ask(description: String, timeoutMs: Long = 60_000L): Boolean {
        val request = Request(++counter, description, CompletableDeferred())
        _pending.value = request
        return try {
            withTimeoutOrNull(timeoutMs) { request.answer.await() } ?: false
        } finally {
            _pending.value = null
        }
    }

    fun resolve(approved: Boolean) {
        _pending.value?.answer?.complete(approved)
    }
}
