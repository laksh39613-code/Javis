package com.aura.agent.core

/** Risk classes used by the planner and the confirmation gate. */
enum class Risk { SAFE, REVERSIBLE, SENSITIVE, IRREVERSIBLE }

enum class StepState { PENDING, AWAITING_CONFIRMATION, RUNNING, DONE, FAILED, REJECTED, BLOCKED }

data class ToolCall(val name: String, val args: Map<String, Any?>)

/**
 * Result of a tool execution.
 * [verified] is true ONLY when the outcome was actually checked (e.g. read back from
 * the system or seen on screen). AURA must not claim success when verified == false.
 */
data class ToolResult(
    val success: Boolean,
    val message: String,
    val verified: Boolean = false,
)

data class ExecutionStep(
    val id: Int,
    val call: ToolCall,
    val risk: Risk,
    val state: StepState = StepState.PENDING,
    val result: ToolResult? = null,
)

data class ParamSpec(
    val name: String,
    val type: String,          // Gemini type: STRING | INTEGER | BOOLEAN
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null,
)

fun pStr(name: String, desc: String, enum: List<String>? = null, required: Boolean = true) =
    ParamSpec(name, "STRING", desc, required, enum)

fun pInt(name: String, desc: String, required: Boolean = true) =
    ParamSpec(name, "INTEGER", desc, required)

fun pBool(name: String, desc: String, required: Boolean = true) =
    ParamSpec(name, "BOOLEAN", desc, required)

interface AuraTool {
    val name: String
    val description: String
    val parameters: List<ParamSpec>
    val risk: Risk
    suspend fun execute(args: Map<String, Any?>): ToolResult
}

class LambdaTool(
    override val name: String,
    override val description: String,
    override val parameters: List<ParamSpec>,
    override val risk: Risk,
    private val block: suspend (Map<String, Any?>) -> ToolResult,
) : AuraTool {
    override suspend fun execute(args: Map<String, Any?>): ToolResult = block(args)
}

fun Map<String, Any?>.str(key: String): String? = this[key]?.toString()?.takeIf { it.isNotBlank() }

fun Map<String, Any?>.int(key: String): Int? =
    (this[key] as? Number)?.toInt() ?: this[key]?.toString()?.toIntOrNull()

fun Map<String, Any?>.bool(key: String): Boolean? =
    this[key] as? Boolean ?: this[key]?.toString()?.lowercase()?.let {
        when (it) {
            "true", "yes", "on" -> true
            "false", "no", "off" -> false
            else -> null
        }
    }
