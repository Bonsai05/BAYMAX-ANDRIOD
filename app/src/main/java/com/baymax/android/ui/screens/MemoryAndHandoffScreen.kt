package com.baymax.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.baymax.android.core.memory.MemoryItem
import com.baymax.android.core.memory.MemoryKind
import com.baymax.android.core.memory.TrustTier
import com.baymax.android.core.task.TaskCheckpoint
import com.baymax.android.core.task.TaskStatus

@Composable
fun MemoryAndHandoffScreen(
    memories: List<MemoryItem>,
    checkpoints: List<TaskCheckpoint>,
    onAddMemory: (String, MemoryKind, TrustTier) -> Unit,
    onTriggerHandoff: (String, String) -> Unit,
    onSimulateTokenExhaustion: () -> Unit,
) {
    var memoryInput by remember { mutableStateOf("") }
    val selectedKind by remember { mutableStateOf(MemoryKind.EPISODIC) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val activeTask = checkpoints.firstOrNull { (it.status == TaskStatus.HANDOFF_REQUIRED || it.status == TaskStatus.RUNNING) }
        val tokenProgress = activeTask?.let { it.tokenCount.toFloat() / it.tokenLimit.toFloat() } ?: 0.35f
        val isTokenExhausted = activeTask?.isTokenLimitExceeded ?: false

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isTokenExhausted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Model-to-Model Context Handoff Engine",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isTokenExhausted) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                )

                Text(
                    text = if (isTokenExhausted) {
                        "⚠️ Model Token Limit Reached (94%). Context Pack generated. Handoff context to Windows Core to continue without starting over."
                    } else {
                        "Active Model Token Budget: ${(tokenProgress * 4096).toInt()} / 4096 tokens"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isTokenExhausted) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                )

                LinearProgressIndicator(
                    progress = { tokenProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isTokenExhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            activeTask?.let { onTriggerHandoff(it.taskId, "windows-core-main") }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTokenExhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Handoff to Core")
                    }

                    OutlinedButton(
                        onClick = onSimulateTokenExhaustion,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Simulate Limit")
                    }
                }
            }
        }

        Text(
            text = "Conversation & Task Recovery (${checkpoints.size})",
            style = MaterialTheme.typography.titleLarge,
        )

        if (checkpoints.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No active task checkpoints. Tasks automatically save state for seamless resume.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(0.4f),
            ) {
                items(items = checkpoints, key = { it.taskId }) { cp: TaskCheckpoint ->
                    CheckpointCard(
                        cp = cp,
                        onResume = { onTriggerHandoff(cp.taskId, "android-node") },
                    )
                }
            }
        }

        Text(
            text = "Cross-Device Persistent Memory (${memories.size})",
            style = MaterialTheme.typography.titleLarge,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = memoryInput,
                    onValueChange = { memoryInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "New Memory Fact") },
                    placeholder = { Text(text = "e.g. User prefers dark mode & concise summaries") },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            if (memoryInput.isNotBlank()) {
                                onAddMemory(memoryInput.trim(), selectedKind, TrustTier.T0)
                                memoryInput = ""
                            }
                        },
                        enabled = memoryInput.isNotBlank(),
                    ) {
                        Text(text = "Save Memory (T0)")
                    }
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(0.6f),
        ) {
            items(items = memories, key = { it.memoryId }) { mem: MemoryItem ->
                MemoryCard(mem = mem)
            }
        }
    }
}

@Composable
private fun CheckpointCard(cp: TaskCheckpoint, onResume: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = cp.title, style = MaterialTheme.typography.titleSmall)
                Text(text = cp.status.name, style = MaterialTheme.typography.labelSmall, color = Color(0xFF0288D1))
            }

            Text(text = "Step: ${cp.currentStep}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Last Node: ${cp.lastNodeId} • Tokens: ${cp.tokenCount}/${cp.tokenLimit}", style = MaterialTheme.typography.labelSmall)

            Button(
                onClick = onResume,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = "Resume Task")
            }
        }
    }
}

@Composable
private fun MemoryCard(mem: MemoryItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = mem.content, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    color = when (mem.trustTier) {
                        TrustTier.T0 -> Color(0xFF1B5E20)
                        TrustTier.T1 -> Color(0xFF0288D1)
                        TrustTier.T2 -> Color(0xFFE65100)
                    },
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = mem.trustTier.name,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Text(
                text = "Kind: ${mem.kind.label} • Source: ${mem.sourceDevice}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
