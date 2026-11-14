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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baymax.android.data.pairing.PairedDevice
import com.baymax.android.data.tasks.AgentTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskComposerScreen(
    pairedDevices: List<PairedDevice>,
    activeTasks: List<AgentTask>,
    onSubmitTask: (String, String) -> Unit,
    onCancelTask: (String) -> Unit,
) {
    var instructionInput by remember { mutableStateOf("") }
    val targetPcName = pairedDevices.firstOrNull()?.deviceName ?: "Windows PC"
    val targetDeviceId = pairedDevices.firstOrNull()?.deviceId ?: "win-pc-primary"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Windows Remote Control",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                Text(
                    text = "1-Click Direct Submission: Type or select a command below. Windows Desktop verifies workspace policies and executes automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Task Instruction for $targetPcName", style = MaterialTheme.typography.titleSmall)

                OutlinedTextField(
                    value = instructionInput,
                    onValueChange = { instructionInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Natural language command") },
                    placeholder = { Text(text = "e.g. Create text file with text Happy Birthday") },
                )

                // Quick Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { instructionInput = "Create text file with text Happy Birthday" },
                        label = { Text(text = "Create File") },
                    )

                    FilterChip(
                        selected = false,
                        onClick = { instructionInput = "List all files in workspace" },
                        label = { Text(text = "List Files") },
                    )
                }

                Button(
                    onClick = {
                        if (instructionInput.isNotBlank()) {
                            onSubmitTask(instructionInput.trim(), targetDeviceId)
                            instructionInput = ""
                        }
                    },
                    enabled = instructionInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "🚀 Submit Remote Task to Windows")
                }
            }
        }

        Text(
            text = "Active & Recent Tasks (${activeTasks.size})",
            style = MaterialTheme.typography.titleLarge,
        )

        if (activeTasks.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No active tasks. Submit a command above to send it directly to your Windows PC.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = activeTasks, key = { it.taskId }) { task: AgentTask ->
                    TaskCard(
                        task = task,
                        onCancel = { onCancelTask(task.taskId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: AgentTask, onCancel: () -> Unit) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()) }
    val formattedDate = remember(task.createdAt) { formatter.format(Date(task.createdAt)) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.userInstruction,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                Surface(
                    color = when (task.status.name) {
                        "COMPLETED" -> Color(0xFF1B5E20) // Green
                        "RUNNING", "PLANNING", "VERIFYING" -> Color(0xFF0288D1) // Blue
                        "FAILED", "CANCELLED", "DENIED" -> Color(0xFFB71C1C) // Red
                        else -> Color(0xFFE65100) // Orange (RECEIVED, QUEUED)
                    },
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = task.status.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Text(text = "Step: ${task.currentStep}", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "Submitted: $formattedDate • ID: ${task.taskId.take(8)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (task.status.name == "RUNNING" || task.status.name == "QUEUED" || task.status.name == "PLANNING" || task.status.name == "RECEIVED") {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = "Cancel Task")
                }
            }
        }
    }
}
