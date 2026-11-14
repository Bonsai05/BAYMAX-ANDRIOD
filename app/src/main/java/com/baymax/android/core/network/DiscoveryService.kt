package com.baymax.android.core.network

data class DiscoveredCoreNode(
    val serviceName: String,
    val host: String,
    val port: Int,
    val discoveryType: DiscoveryType
) {
    val endpoint: String
        get() = "$host:$port"
}

enum class DiscoveryType {
    MDNS,
    MANUAL_FALLBACK,
    SAVED_PREFERENCE
}

class DiscoveryService {

    fun resolveEndpoint(
        manualEndpoint: String?,
        mdnsDiscovered: DiscoveredCoreNode?
    ): DiscoveredCoreNode? {
        if (!manualEndpoint.isNullOrBlank()) {
            val parts = manualEndpoint.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
            return DiscoveredCoreNode(
                serviceName = "BAYMAX Core (Manual)",
                host = host,
                port = port,
                discoveryType = DiscoveryType.MANUAL_FALLBACK
            )
        }

        return mdnsDiscovered
    }
}
