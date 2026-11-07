package com.aura.agent

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aura.agent.core.AgentOrchestrator
import com.aura.agent.core.ConfirmationGate
import com.aura.agent.core.ExecutionLogger
import com.aura.agent.core.ExecutionStep
import com.aura.agent.core.GeminiPlanner
import com.aura.agent.core.ToolRegistry
import com.aura.agent.tools.ContactLookupTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String)

class AuraViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val contacts = ContactLookupTool(app)
    val registry = ToolRegistry.build(app, contacts)
    val logger = ExecutionLogger()
    val gate = ConfirmationGate()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _steps = MutableStateFlow<List<ExecutionStep>>(emptyList())
    val steps: StateFlow<List<ExecutionStep>> = _steps.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var job: Job? = null

    fun apiKey(): String = prefs.getString(KEY_API, "").orEmpty()

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API, key.trim()).apply()
    }

    fun contactsConsent(): Boolean = contacts.isConsentGiven()

    fun setContactsConsent(on: Boolean) {
        prefs.edit().putBoolean(ContactLookupTool.KEY_CONSENT, on).apply()
    }

    fun send(text: String) {
        if (text.isBlank() || job?.isActive == true) return
        val key = apiKey()
        if (key.isBlank()) {
            _messages.update { it + ChatMessage(false, "Pehle Settings me Gemini API key dalein.") }
            return
        }

        _messages.update { it + ChatMessage(true, text) }
        _steps.value = emptyList()
        logger.log("USER: $text")

        job = viewModelScope.launch {
            _busy.value = true
            val orchestrator = AgentOrchestrator(
                planner = GeminiPlanner(key, registry),
                registry = registry,
                confirmations = gate,
                logger = logger,
                onStep = ::upsertStep,
            )
            val reply = try {
                orchestrator.handle(text)
            } catch (e: CancellationException) {
                "Cancel kar diya gaya."
            } catch (e: Exception) {
                "Error: ${e.message ?: e::class.simpleName}"
            } finally {
                _busy.value = false
            }
            _messages.update { it + ChatMessage(false, reply) }
        }
    }

    fun cancel() {
        gate.resolve(false)
        job?.cancel()
        logger.log("CANCEL requested by user")
    }

    fun confirm(approved: Boolean) = gate.resolve(approved)

    private fun upsertStep(step: ExecutionStep) {
        _steps.update { list ->
            val index = list.indexOfFirst { it.id == step.id }
            if (index >= 0) list.toMutableList().also { it[index] = step } else list + step
        }
    }

    companion object {
        const val PREFS = "aura_prefs"
        const val KEY_API = "gemini_api_key"
    }
}
