package com.baymax.android.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ConnectionStatus {
    data object Unpaired : ConnectionStatus
    data object Disconnected : ConnectionStatus
    data class Discovering(val method: String) : ConnectionStatus
    data class Connecting(val endpoint: String) : ConnectionStatus
    data class PairingPending(val hostName: String, val message: String) : ConnectionStatus
    data class Connected(val endpoint: String, val latencyMs: Long) : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

class ConnectionStateMachine {

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    fun transitionTo(newStatus: ConnectionStatus) {
        _status.value = newStatus
    }

    fun setUnpaired() {
        _status.value = ConnectionStatus.Unpaired
    }

    fun setDisconnected() {
        _status.value = ConnectionStatus.Disconnected
    }

    fun setDiscovering(method: String = "mDNS + Endpoint fallback") {
        _status.value = ConnectionStatus.Discovering(method)
    }

    fun setConnecting(endpoint: String) {
        _status.value = ConnectionStatus.Connecting(endpoint)
    }

    fun setPairingPending(hostName: String, message: String = "Waiting for Windows user confirmation...") {
        _status.value = ConnectionStatus.PairingPending(hostName, message)
    }

    fun setConnected(endpoint: String, latencyMs: Long = 0L) {
        _status.value = ConnectionStatus.Connected(endpoint, latencyMs)
    }

    fun setError(message: String) {
        _status.value = ConnectionStatus.Error(message)
    }
}
