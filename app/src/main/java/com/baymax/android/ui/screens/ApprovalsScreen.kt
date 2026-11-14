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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.baymax.android.core.capabilities.ApprovalRequest
import com.baymax.android.core.capabilities.ApprovalStatus
import com.baymax.android.core.capabilities.CapabilityRegistry
import com.baymax.android.core.capabilities.RiskLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ApprovalsScreen(
    requests: List<ApprovalRequest>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onSimulateRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Capability Authorization & Policy Gate",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                Text(
                    text = "Explicit policy gate ensures AI Agent or Windows Core cannot execute unauthorized actions on this Android device without human-in-the-loop approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onSimulateRequest) {
                        Text(text = "Simulate Core Request")
                    }
                }
            }
        }

        Text(
            text = "Pending Human Approvals (${requests.count { it.status == ApprovalStatus.PENDING }})",
            style = MaterialTheme.typography.titleLarge,
        )

        val pendingList = requests.filter { it.status == ApprovalStatus.PENDING }

        if (pendingList.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No pending approval requests. System policy is secure.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(pendingList, key = { it.id }) { req ->
                    ApprovalCard(
                        request = req,
                        onApprove = { onApprove(req.id) },
                        onReject = { onReject(req.id) },
                    )
                }
            }
        }

        Text(
            text = "Registered Device Capabilities (${CapabilityRegistry.REGISTERED_CAPABILITIES.size})",
            style = MaterialTheme.typography.titleMedium,
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(CapabilityRegistry.REGISTERED_CAPABILITIES, key = { it.id }) { cap ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(text = cap.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = cap.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        RiskChip(riskLevel = cap.riskLevel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(request.requestedAt) { formatter.format(Date(request.requestedAt)) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = request.capabilityName,
                    style = MaterialTheme.typography.titleSmall,
                )

                RiskChip(riskLevel = request.riskLevel)
            }

            Text(
                text = request.description,
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "Requested by: ${request.requestedBy} at $timeStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1B5E20),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Approve Action")
                }

                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Reject")
                }
            }
        }
    }
}

@Composable
private fun RiskChip(riskLevel: RiskLevel) {
    val (color, text) = when (riskLevel) {
        RiskLevel.LOW -> Color(0xFF2E7D32) to "LOW"
        RiskLevel.MEDIUM -> Color(0xFFF57C00) to "MEDIUM"
        RiskLevel.HIGH -> Color(0xFFC62828) to "HIGH"
        RiskLevel.CRITICAL -> Color(0xFF880E4F) to "CRITICAL"
    }

    Surface(
        color = color,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
