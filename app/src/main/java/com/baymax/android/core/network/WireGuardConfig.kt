package com.baymax.android.core.network

data class WireGuardPeer(
    val publicKey: String,
    val endpoint: String,
    val allowedIps: List<String>,
    val presharedKey: String? = null
)

data class WireGuardConfig(
    val interfaceName: String = "wg-baymax0",
    val localPrivateKey: String = "",
    val localAddress: String = "10.0.0.2/32",
    val listenPort: Int = 51820,
    val peer: WireGuardPeer? = null,
    val isTunnelActive: Boolean = false
) {
    val isConfigured: Boolean
        get() = localPrivateKey.isNotBlank() && peer != null && peer.publicKey.isNotBlank()
}
