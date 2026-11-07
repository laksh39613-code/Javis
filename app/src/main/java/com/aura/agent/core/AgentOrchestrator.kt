package com.aura.agent.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Plan -> classify -> confirm -> execute -> observe -> report loop.
 * Limits: max model rounds, max total steps, per-tool timeout.
 */
class AgentOrchestrator(
    private val planner: GeminiPlanner,
    private val registry: ToolRegistry,
    private val confirmations: ConfirmationGate,
    private val logger: ExecutionLogger,
    private val onStep: (ExecutionStep) -> Unit,
) {
    private var stepCounter = 0

    suspend fun handle(userText: String): String {
        val contents = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userText)))
        )

        repeat(MAX_MODEL_ROUNDS) {
            when (val out = planner.send(contents)) {
                is PlannerOutput.Text -> return out.text

                is PlannerOutput.Calls -> {
                    contents.put(out.rawContent)
                    val responseParts = JSONArray()

                    for (call in out.calls) {
                        if (stepCounter >= MAX_TOTAL_STEPS) {
                            return "Step limit (${MAX_TOTAL_STEPS}) tak pahunch gaye. Ruk kar dobara try karein."
                        }
                        stepCounter++
                        val result = runStep(stepCounter, call)
                        responseParts.put(functionResponse(call.name, result))
                    }
                    contents.put(JSONObject().put("role", "user").put("parts", responseParts))
                }
            }
        }
        return "Ye kaam poora nahi ho paya (round limit). Kripya command aur saaf likhein."
    }

    private suspend fun runStep(id: Int, call: ToolCall): ToolResult {
        val risk = RiskPolicy.classify(call, registry)
        var step = ExecutionStep(id, call, risk)
        emit(step)

        RiskPolicy.blockReason(call)?.let { reason ->
            val blocked = ToolResult(false, reason)
            emit(step.copy(state = StepState.BLOCKED, result = blocked))
            logger.log("BLOCKED ${call.name}: $reason")
            return blocked
        }

        val tool = registry.get(call.name)
        if (tool == null) {
            val r = ToolResult(false, "Unknown tool: ${call.name}")
            emit(step.copy(state = StepState.FAILED, result = r))
            return r
        }

        if (risk == Risk.SENSITIVE || risk == Risk.IRREVERSIBLE) {
            step = step.copy(state = StepState.AWAITING_CONFIRMATION)
            emit(step)
            val approved = confirmations.ask(describe(call, risk))
            if (!approved) {
                val r = ToolResult(false, "User ne is step ko cancel/reject kiya.")
                emit(step.copy(state = StepState.REJECTED, result = r))
                logger.log("REJECTED ${call.name}")
                return r
            }
        }

        step = step.copy(state = StepState.RUNNING)
        emit(step)
        logger.log("RUN ${call.name} ${call.args}")

        val result = try {
            withTimeout(TOOL_TIMEOUT_MS) { tool.execute(call.args) }
        } catch (e: TimeoutCancellationException) {
            ToolResult(false, "Timeout: ${call.name} ${TOOL_TIMEOUT_MS / 1000}s me complete nahi hua.")
        } catch (e: CancellationException) {
            // User pressed cancel: propagate so the whole job stops.
            throw e
        } catch (e: Exception) {
            ToolResult(false, "Error in ${call.name}: ${e.message ?: e::class.simpleName}")
        }

        emit(step.copy(state = if (result.success) StepState.DONE else StepState.FAILED, result = result))
        logger.log("RESULT ${call.name}: success=${result.success} verified=${result.verified} ${result.message}")
        return result
    }

    private fun emit(step: ExecutionStep) = onStep(step)

    private fun describe(call: ToolCall, risk: Risk): String {
        val args = call.args.entries.joinToString { "${it.key}=${it.value}" }
        return "Action: ${call.name}($args)\nRisk: $risk"
    }

    private fun functionResponse(name: String, result: ToolResult): JSONObject =
        JSONObject().put(
            "functionResponse",
            JSONObject()
                .put("name", name)
                .put(
                    "response",
                    JSONObject()
                        .put("success", result.success)
                        .put("verified", result.verified)
                        .put("message", result.message)
                )
        )

    companion object {
        const val MAX_MODEL_ROUNDS = 6
        const val MAX_TOTAL_STEPS = 12
        const val TOOL_TIMEOUT_MS = 20_000L
    }
}
