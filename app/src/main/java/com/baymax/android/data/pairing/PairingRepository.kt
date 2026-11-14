package com.baymax.android.data.pairing

import android.util.Log
import com.baymax.android.core.network.MobileApiClient
import com.baymax.android.core.network.QrPairingPayload
import com.baymax.android.core.storage.BaymaxDatabase
import com.baymax.android.core.storage.StoredPairedDevice
import java.util.UUID

data class PairedDevice(
    val deviceId: String = UUID.randomUUID().toString(),
    val deviceName: String,
    val platform: String = "Windows",
    val publicKey: String,
    val protocolVersion: String = "v2.0",
    val pairedAt: Long = System.currentTimeMillis(),
    val revokedAt: Long? = null,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val endpoint: String
) {
    val isRevoked: Boolean
        get() = revokedAt != null
}

class PairingRepository(
    private val database: BaymaxDatabase,
    private val apiClient: MobileApiClient? = null
) {

    companion object {
        private const val TAG = "BAYMAX_PAIRING"
    }

    fun registerAndPollWithPayload(
        payload: QrPairingPayload,
        deviceName: String,
        onStatusUpdate: (String) -> Unit,
        onSuccess: (PairedDevice) -> Unit,
        onFailure: (String) -> Unit
    ) {
        Log.d(TAG, "Initiating registration to ${payload.endpoint} for token: ${payload.token.take(8)}...")
        onStatusUpdate("Pairing request sent. Waiting for Windows confirmation...")

        val client = apiClient
        if (client == null) {
            // Local mode fallback if no API client
            val dev = pairDevice(payload.nodeId, payload.publicKey, payload.endpoint)
            onSuccess(dev)
            return
        }

        val invId = if (payload.invitationId.isNotBlank()) payload.invitationId else payload.nodeId
        val sec = if (payload.secret.isNotBlank()) payload.secret else payload.token

        // 1. Send live POST /mobile/pairing/register
        val registerResult = client.registerDevice(
            endpoint = payload.endpoint,
            invitationId = invId,
            secret = sec,
            deviceName = deviceName
        )

        registerResult.onSuccess { regResponse ->
            Log.d(TAG, "Registration submitted successfully! Request ID: ${regResponse.requestId}. Polling Windows every 2 seconds...")
            onStatusUpdate("Waiting for Windows user to press 'Confirm pairing' on PC...")

            // 2. Poll GET /mobile/pairing/requests/{requestId} until Windows confirms
            var isPaired = false
            var attempts = 0
            val maxAttempts = 150 // 5 minutes max polling (150 * 2s)

            while (!isPaired && attempts < maxAttempts) {
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) {
                    break
                }
                attempts++

                val pollResult = client.pollPairingStatus(
                    endpoint = payload.endpoint,
                    requestId = regResponse.requestId,
                    pollingSecret = regResponse.pollingSecret
                )

                pollResult.onSuccess { poll ->
                    Log.d(TAG, "Poll #$attempts status: ${poll.status}")
                    when (poll.status) {
                        "PAIRED" -> {
                            isPaired = true
                            val assignedDeviceId = poll.deviceId ?: ("win-pc-" + UUID.randomUUID().toString().take(8))
                            val pairedDev = pairDevice(
                                deviceName = payload.nodeId,
                                publicKey = payload.publicKey,
                                endpoint = payload.endpoint,
                                deviceId = assignedDeviceId
                            )
                            Log.d(TAG, "Windows confirmed pairing! Device saved as ${pairedDev.deviceName} (${pairedDev.deviceId}).")
                            onStatusUpdate("Paired with Windows host ${pairedDev.deviceName}.")
                            onSuccess(pairedDev)
                            return
                        }
                        "REJECTED" -> {
                            Log.w(TAG, "Pairing request was rejected on Windows PC.")
                            onFailure("Pairing request was rejected on Windows PC.")
                            return
                        }
                        "EXPIRED" -> {
                            Log.w(TAG, "Pairing invitation expired on Windows PC.")
                            onFailure("Pairing invitation expired. Please scan a new QR code.")
                            return
                        }
                        else -> {
                            onStatusUpdate("Waiting for Windows confirmation (attempt $attempts)...")
                        }
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Poll attempt $attempts error: ${err.message}")
                }
            }

            if (!isPaired) {
                onFailure("Pairing timeout. Please scan a fresh QR code from Windows PC.")
            }

        }.onFailure { err ->
            Log.e(TAG, "Registration attempt failed: ${err.message}")
            onFailure("Could not connect to Windows PC at ${payload.endpoint}. Ensure phone and PC are on the same Wi-Fi.")
        }
    }

    fun pairDevice(
        deviceName: String,
        publicKey: String,
        endpoint: String,
        deviceId: String = "win-pc-" + UUID.randomUUID().toString().take(8)
    ): PairedDevice {
        val dev = PairedDevice(
            deviceId = deviceId,
            deviceName = deviceName,
            publicKey = publicKey,
            endpoint = endpoint
        )
        database.savePairedDevice(
            StoredPairedDevice(
                deviceId = dev.deviceId,
                deviceName = dev.deviceName,
                platform = dev.platform,
                publicKey = dev.publicKey,
                protocolVersion = dev.protocolVersion,
                pairedAt = dev.pairedAt,
                revokedAt = dev.revokedAt,
                lastSeenAt = dev.lastSeenAt,
                endpoint = dev.endpoint
            )
        )
        return dev
    }

    fun getPairedDevices(): List<PairedDevice> {
        return database.getAllPairedDevices().map { stored ->
            PairedDevice(
                deviceId = stored.deviceId,
                deviceName = stored.deviceName,
                platform = stored.platform,
                publicKey = stored.publicKey,
                protocolVersion = stored.protocolVersion,
                pairedAt = stored.pairedAt,
                revokedAt = stored.revokedAt,
                lastSeenAt = stored.lastSeenAt,
                endpoint = stored.endpoint
            )
        }
    }

    fun revokeDevice(deviceId: String): Boolean {
        val devices = getPairedDevices()
        val dev = devices.firstOrNull { it.deviceId == deviceId } ?: return false
        val revoked = dev.copy(revokedAt = System.currentTimeMillis())
        database.savePairedDevice(
            StoredPairedDevice(
                deviceId = revoked.deviceId,
                deviceName = revoked.deviceName,
                platform = revoked.platform,
                publicKey = revoked.publicKey,
                protocolVersion = revoked.protocolVersion,
                pairedAt = revoked.pairedAt,
                revokedAt = revoked.revokedAt,
                lastSeenAt = revoked.lastSeenAt,
                endpoint = revoked.endpoint
            )
        )
        Log.d(TAG, "Device $deviceId has been revoked.")
        return true
    }
}
