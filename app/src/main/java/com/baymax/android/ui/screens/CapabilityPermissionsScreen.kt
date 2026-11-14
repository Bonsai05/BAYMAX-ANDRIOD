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
import com.baymax.android.data.permissions.CapabilityGrant

@Composable
fun CapabilityPermissionsScreen(
    grants: List<CapabilityGrant>,
    onGrantCapability: (String, String) -> Unit,
    onRevokeGrant: (String) -> Unit,
) {
    var capInput by remember { mutableStateOf("FS_COPY") }
    var pathInput by remember { mutableStateOf("C:\\Users\\Public\\Documents") }

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
                    text = "Windows Bounded Capability Policy",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )

                Text(
                    text = "Default-deny policy. Grants define exact allowed operation types, root directories, and byte limits for autonomous execution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Grant Windows Capability", style = MaterialTheme.typography.titleSmall)

                OutlinedTextField(
                    value = capInput,
                    onValueChange = { capInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Capability Type (e.g. FS_COPY, FS_READ)") },
                )

                OutlinedTextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Allowed Root Path") },
                )

                Button(
                    onClick = {
                        if (capInput.isNotBlank() && pathInput.isNotBlank()) {
                            onGrantCapability(capInput.trim(), pathInput.trim())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Add Capability Grant")
                }
            }
        }

        Text(
            text = "Active Windows Grants (${grants.size})",
            style = MaterialTheme.typography.titleLarge,
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items = grants, key = { it.grantId }) { grant: CapabilityGrant ->
                GrantCard(grant = grant, onRevoke = { onRevokeGrant(grant.grantId) })
            }
        }
    }
}

@Composable
private fun GrantCard(grant: CapabilityGrant, onRevoke: () -> Unit) {
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
                Text(text = grant.capability, style = MaterialTheme.typography.titleSmall)
                Surface(
                    color = if (grant.isRevoked) Color(0xFFB71C1C) else Color(0xFF1B5E20),
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = if (grant.isRevoked) "REVOKED" else grant.approvalMode,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Text(text = "Allowed Root: ${grant.allowedRoots}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Constraints: ${grant.constraintsJson}", style = MaterialTheme.typography.labelSmall)

            if (!grant.isRevoked) {
                OutlinedButton(
                    onClick = onRevoke,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = "Revoke Grant")
                }
            }
        }
    }
}
