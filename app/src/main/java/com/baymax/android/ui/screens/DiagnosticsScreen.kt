package com.baymax.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.baymax.android.core.storage.BaymaxDatabase

@Composable
fun DiagnosticsScreen(
    totalEventCount: Int,
    pendingEventCount: Int,
    syncedEventCount: Int,
    failedEventCount: Int,
    onTriggerWorker: () -> Unit,
    onTestCipher: () -> String,
) {
    var cipherTestResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Local Storage Invariants & Security",
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(text = "Database: ${BaymaxDatabase.DEFAULT_DATABASE_NAME}")
                Text(text = "Cipher: AES-256-GCM (AndroidKeyStore)")
                Text(text = "Total Records: $totalEventCount")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Pending: $pendingEventCount", color = Color(0xFFE65100))
                    Text(text = "Synced: $syncedEventCount", color = Color(0xFF1B5E20))
                    Text(text = "Failed: $failedEventCount", color = Color(0xFFB71C1C))
                }

                Button(
                    onClick = { cipherTestResult = onTestCipher() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Run AES-256-GCM Verification")
                }

                cipherTestResult?.let { res ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text = res,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "WorkManager Background Outbox Scheduler",
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    text = "Durable outbox uses OneTimeWorkRequest with EXPONENTIAL backoff (30s) and NetworkType.CONNECTED constraints.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Button(
                    onClick = onTriggerWorker,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Enqueue Unique WorkManager Task")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Fault Injection & Lifecycle Verification (Stage 14)",
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    text = "Simulate node disruptions to verify local state resilience and idempotent outbox sync.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Simulate Offline")
                    }

                    OutlinedButton(
                        onClick = { },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Simulate Core Down")
                    }
                }
            }
        }
    }
}
