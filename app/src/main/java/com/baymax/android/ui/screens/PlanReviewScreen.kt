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
import com.baymax.android.data.plans.ImmutablePlan
import com.baymax.android.data.plans.PlanActionDetail
import com.baymax.android.data.plans.PlanStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlanReviewScreen(
    plans: List<ImmutablePlan>,
    onApprovePlan: (String) -> Unit,
    onDiscardPlan: (String) -> Unit,
    onSimulatePlanSubmit: () -> Unit
) {
    val pendingPlans = plans.filter { it.status == PlanStatus.PENDING_REVIEW }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mobile Plan Approval Header
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Server-Side Immutable Plan Review (Phase 4)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                Text(
                    text = "Plans are stored immutably on Windows Core with a 5-minute expiry. Android verifies exact action details, risk warnings, and plan hash before signing approval with hardware ECDSA Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onSimulatePlanSubmit) {
                        Text(text = "Create Demo Plan Request")
                    }
                }
            }
        }

        Text(
            text = "Pending Plan Inbox (${pendingPlans.size})",
            style = MaterialTheme.typography.titleLarge
        )

        if (pendingPlans.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No pending plans awaiting approval. Inbox is up to date.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pendingPlans, key = { it.planId }) { plan ->
                    PlanCard(
                        plan = plan,
                        onApprove = { onApprovePlan(plan.planId) },
                        onDiscard = { onDiscardPlan(plan.planId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: ImmutablePlan,
    onApprove: () -> Unit,
    onDiscard: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val createdStr = remember(plan.createdAt) { formatter.format(Date(plan.createdAt)) }
    val expiryStr = remember(plan.expiresAt) { formatter.format(Date(plan.expiresAt)) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Plan: ${plan.planId}",
                    style = MaterialTheme.typography.titleSmall
                )

                Surface(
                    color = Color(0xFF0288D1),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = "EXPIRES AT $expiryStr",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Text(
                text = "Instruction: \"${plan.userInstruction}\"",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Plan Hash (SHA-256): ${plan.planHash}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Actions in Plan (${plan.actions.size}):",
                style = MaterialTheme.typography.labelLarge
            )

            plan.actions.forEach { action ->
                ActionDetailRow(action = action)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1B5E20)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Approve & Sign ECDSA")
                }

                OutlinedButton(
                    onClick = onDiscard,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Discard Plan")
                }
            }
        }
    }
}

@Composable
private fun ActionDetailRow(action: PlanActionDetail) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (action.isMutation) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = action.actionType,
                    style = MaterialTheme.typography.labelMedium
                )
                if (action.isMutation) {
                    Surface(
                        color = Color(0xFFB71C1C),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "FILE MUTATION",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Text(
                text = "Target: ${action.path}",
                style = MaterialTheme.typography.bodySmall
            )

            action.impactWarning?.let { warning ->
                Text(
                    text = "⚠️ Warning: $warning",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
