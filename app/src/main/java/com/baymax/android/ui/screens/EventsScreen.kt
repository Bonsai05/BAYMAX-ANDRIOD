package com.baymax.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.baymax.android.core.storage.EventItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EventsScreen(
    events: List<EventItem>,
    loading: Boolean,
    error: String?,
    onSaveEvent: (String) -> Unit,
    onRefresh: () -> Unit,
    onSyncNow: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val pendingCount = events.count { it.syncStatus == "PENDING" }
    val syncedCount = events.count { it.syncStatus == "SYNCED" }
    val failedCount = events.count { it.syncStatus == "FAILED" }

    val filteredEvents = when (selectedFilter) {
        "PENDING" -> events.filter { it.syncStatus == "PENDING" }
        "SYNCED" -> events.filter { it.syncStatus == "SYNCED" }
        "FAILED" -> events.filter { it.syncStatus == "FAILED" }
        else -> events
    }

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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Local Event Store & Outbox",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Pending: $pendingCount", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Synced: $syncedCount", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Failed: $failedCount", style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !loading,
                    ) {
                        Text(text = "Sync Outbox Now")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "New Local Event", style = MaterialTheme.typography.titleSmall)

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Event description") },
                    placeholder = { Text(text = "e.g. Health metric update") },
                    enabled = !loading,
                )

                Button(
                    onClick = {
                        val trimmed = input.trim()
                        if (trimmed.isNotEmpty()) {
                            onSaveEvent(trimmed)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Save Event (Local First)")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("ALL", "PENDING", "SYNCED", "FAILED").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(text = filter) },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onRefresh) {
                Text(text = "🔄")
            }
        }

        error?.let { errText ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Error: $errText",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = filteredEvents, key = { it.id }) { event: EventItem ->
                EventCard(event = event)
            }
        }
    }
}

@Composable
private fun EventCard(event: EventItem) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()) }
    val formattedDate = remember(event.createdAt) { formatter.format(Date(event.createdAt)) }

    val (statusBg, statusText) = when (event.syncStatus) {
        "SYNCED" -> Color(0xFF1B5E20) to "SYNCED"
        "FAILED" -> Color(0xFFB71C1C) to "FAILED"
        else -> Color(0xFFE65100) to "PENDING"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = event.text,
                    style = MaterialTheme.typography.bodyLarge,
                )

                Surface(
                    color = statusBg,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Text(
                text = "Created: $formattedDate • ID: ${event.id.take(8)}...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (event.attempts > 0) {
                Text(
                    text = "Attempts: ${event.attempts} ${event.lastError?.let { "• $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
