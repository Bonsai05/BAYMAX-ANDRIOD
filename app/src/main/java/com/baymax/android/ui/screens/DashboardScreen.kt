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
import com.baymax.android.core.network.ConnectionStatus
import com.baymax.android.core.network.QrPairingPayload
import com.baymax.android.core.storage.NodeConfig

@Composable
fun DashboardScreen(
    nodeConfig: NodeConfig,
    connectionStatus: ConnectionStatus,
    onPairWithQr: (QrPairingPayload) -> Unit,
    onUpdateEndpoint: (String) -> Unit,
) {
    var qrInput by remember { mutableStateOf("") }
    var endpointInput by remember { mutableStateOf(nodeConfig.coreEndpoint) }
    var qrError by remember { mutableStateOf<String?>(null) }
    var showCameraScanner by remember { mutableStateOf(false) }

    if (showCameraScanner) {
        QrCameraScannerDialog(
            onDismiss = { showCameraScanner = false },
            onQrScanned = { scannedRaw ->
                qrInput = scannedRaw
                QrPairingPayload.parse(scannedRaw)
                    .onSuccess { payload ->
                        onPairWithQr(payload)
                        qrError = null
                    }
                    .onFailure { ex ->
                        qrError = ex.message ?: "Invalid QR Code payload"
                    }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = nodeConfig.nodeName,
                        style = MaterialTheme.typography.titleLarge,
                    )

                    val trustColor = when (nodeConfig.trustStatus) {
                        "TRUSTED" -> Color(0xFF1B5E20)
                        "PAIRED" -> Color(0xFF0288D1)
                        "PENDING_CONFIRMATION" -> Color(0xFFE65100)
                        else -> Color(0xFFD32F2F)
                    }

                    Surface(
                        color = trustColor,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = nodeConfig.trustStatus,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                Text(
                    text = "Node ID: ${nodeConfig.nodeId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "P2P Network & Device Pairing Status",
                    style = MaterialTheme.typography.titleMedium,
                )

                val (statusText, statusBg) = when (connectionStatus) {
                    is ConnectionStatus.Connected -> "PAIRED & CONNECTED (${connectionStatus.endpoint})" to Color(0xFF1B5E20)
                    is ConnectionStatus.PairingPending -> "PAIRING SENT: Waiting for Windows Confirmation (${connectionStatus.hostName})" to Color(0xFFE65100)
                    is ConnectionStatus.Connecting -> "CONNECTING..." to Color(0xFF0288D1)
                    is ConnectionStatus.Discovering -> "DISCOVERING (${connectionStatus.method})" to Color(0xFFF57C00)
                    is ConnectionStatus.Unpaired -> "UNPAIRED (Scan QR)" to Color(0xFFD32F2F)
                    is ConnectionStatus.Error -> "ERROR (${connectionStatus.message})" to Color(0xFFD32F2F)
                    else -> "DISCONNECTED" to Color.Gray
                }

                Surface(
                    color = statusBg,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Text(text = "Interface: wg-baymax0 (10.0.0.2/32)")
                Text(text = "Peer Key: ${if (nodeConfig.peerPublicKey.isBlank()) "Not Paired" else nodeConfig.peerPublicKey.take(16) + "..."}")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Windows Core Endpoint (mDNS / Manual)",
                    style = MaterialTheme.typography.titleMedium,
                )

                OutlinedTextField(
                    value = endpointInput,
                    onValueChange = { endpointInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Core Host:Port") },
                    placeholder = { Text(text = "192.168.1.100:50051") },
                )

                Button(
                    onClick = { onUpdateEndpoint(endpointInput) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Save Endpoint")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Pair with Windows Core",
                    style = MaterialTheme.typography.titleMedium,
                )

                Button(
                    onClick = { showCameraScanner = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "📷 Scan QR Code with Camera")
                }

                Text(
                    text = "Or paste the QR pairing URI/JSON from Windows Core",
                    style = MaterialTheme.typography.bodySmall,
                )

                OutlinedTextField(
                    value = qrInput,
                    onValueChange = {
                        qrInput = it
                        qrError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Pairing URI / QR Content") },
                    placeholder = { Text(text = "{\"endpoint\":\"https://...\",\"invitationId\":\"...\"}") },
                    isError = qrError != null,
                )

                qrError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            QrPairingPayload.parse(qrInput)
                                .onSuccess { payload ->
                                    onPairWithQr(payload)
                                    qrInput = ""
                                    qrError = null
                                }
                                .onFailure { ex ->
                                    qrError = ex.message ?: "Invalid QR payload"
                                }
                        },
                        enabled = qrInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Verify & Pair")
                    }

                    OutlinedButton(
                        onClick = {
                            val demoPayload = QrPairingPayload(
                                nodeId = "BONSAI",
                                publicKey = "43ff84c1df9e06c6aac70c114c550ccb2c5245266823e85b19b02d6896c7ee3e",
                                endpoint = endpointInput,
                                token = "demo-secret-token",
                                expiresAt = System.currentTimeMillis() + 600_000L,
                            )
                            onPairWithQr(demoPayload)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Demo Pair")
                    }
                }
            }
        }
    }
}
