package com.aura.agent

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.contracts.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aura.agent.core.ExecutionStep
import com.aura.agent.core.StepState

class MainActivity : ComponentActivity() {

    private val vm: AuraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuraScreen(vm)
                }
            }
        }
    }
}

@Composable
fun AuraScreen(vm: AuraViewModel) {
    val messages by vm.messages.collectAsState()
    val steps by vm.steps.collectAsState()
    val busy by vm.busy.collectAsState()
    val pending by vm.gate.pending.collectAsState()
    val logs by vm.logger.entries.collectAsState()

    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { input = it }
    }

    LaunchedEffect(messages.size, steps.size) {
        val total = messages.size + steps.size
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("AURA", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { showSettings = true }) { Text("Settings") }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { msg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(if (msg.fromUser) "Aap" else "AURA", style = MaterialTheme.typography.labelMedium)
                        Text(msg.text)
                    }
                }
            }
            items(steps) { step -> StepCard(step) }
            if (logs.isNotEmpty()) {
                item { Text("Log (last lines)", style = MaterialTheme.typography.labelSmall) }
                items(logs.takeLast(5)) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                Spacer(Modifier.height(0.dp))
                TextButton(onClick = { vm.cancel() }) { Text("Cancel") }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Jaise: Instagram kholo aur Rahul ki latest post dikhao") },
                maxLines = 3,
            )
            TextButton(onClick = {
                speechLauncher.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                )
            }) { Text("Mic") }
            Button(
                enabled = !busy && input.isNotBlank(),
                onClick = {
                    vm.send(input)
                    input = ""
                },
            ) { Text("Send") }
        }
    }

    pending?.let { request ->
        AlertDialog(
            onDismissRequest = { vm.confirm(false) },
            title = { Text("Confirm karein") },
            text = { Text(request.description) },
            confirmButton = { Button(onClick = { vm.confirm(true) }) { Text("Haan, karo") } },
            dismissButton = { OutlinedButton(onClick = { vm.confirm(false) }) { Text("Mana karein") } },
        )
    }

    if (showSettings) {
        SettingsDialog(
            vm = vm,
            onDismiss = { showSettings = false },
            onOpenAccessibility = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onOpenWriteSettings = {
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
            },
            onOpenDnd = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            },
        )
    }
}

@Composable
private fun StepCard(step: ExecutionStep) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("${step.call.name}  [${step.risk}]", style = MaterialTheme.typography.labelMedium)
            Text("Status: ${stateLabel(step.state)}", style = MaterialTheme.typography.bodySmall)
            step.result?.let { r ->
                val tag = when {
                    !r.success -> "failed"
                    r.verified -> "verified"
                    else -> "not verified"
                }
                Text("$tag: ${r.message}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun stateLabel(state: StepState): String = when (state) {
    StepState.PENDING -> "pending"
    StepState.AWAITING_CONFIRMATION -> "waiting for your confirmation"
    StepState.RUNNING -> "running"
    StepState.DONE -> "done"
    StepState.FAILED -> "failed"
    StepState.REJECTED -> "rejected by you"
    StepState.BLOCKED -> "blocked for safety"
}

@Composable
private fun SettingsDialog(
    vm: AuraViewModel,
    onDismiss: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenDnd: () -> Unit,
) {
    var key by remember { mutableStateOf(vm.apiKey()) }
    var consent by remember { mutableStateOf(vm.contactsConsent()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AURA Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Contacts access (Gemini ko naam/number bhejne ki permission)", modifier = Modifier.weight(1f))
                    Switch(checked = consent, onCheckedChange = { consent = it })
                }
                OutlinedButton(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Text("Accessibility service on karein")
                }
                OutlinedButton(onClick = onOpenWriteSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Brightness permission dein")
                }
                OutlinedButton(onClick = onOpenDnd, modifier = Modifier.fillMaxWidth()) {
                    Text("Do Not Disturb access dein")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.saveApiKey(key)
                vm.setContactsConsent(consent)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
