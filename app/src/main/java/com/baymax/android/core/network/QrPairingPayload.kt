package com.baymax.android.core.network

import java.net.URLDecoder
import java.net.URLEncoder

data class QrPairingPayload(
    val nodeId: String,
    val publicKey: String,
    val endpoint: String,
    val token: String,
    val expiresAt: Long,
    val invitationId: String = "",
    val secret: String = "",
    val certificateSha256: String = "",
    val hostName: String = ""
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt

    fun toUriString(): String {
        return "baymax://pair?" +
                "node_id=${encode(nodeId)}&" +
                "pub_key=${encode(publicKey)}&" +
                "endpoint=${encode(endpoint)}&" +
                "token=${encode(token)}&" +
                "expires=${expiresAt}"
    }

    companion object {
        private fun encode(str: String) = URLEncoder.encode(str, "UTF-8")
        private fun decode(str: String) = URLDecoder.decode(str, "UTF-8")

        private fun extractJsonField(json: String, key: String): String? {
            val regex = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            return regex.find(json)?.groupValues?.get(1)
        }

        fun parse(rawPayload: String): Result<QrPairingPayload> {
            return try {
                val trimmed = rawPayload.trim()

                // Support Windows Host JSON payload
                if (trimmed.startsWith("{")) {
                    val hostName = extractJsonField(trimmed, "hostName") ?: "BONSAI"
                    var endpoint = extractJsonField(trimmed, "endpoint") ?: "10.72.142.20:5081"
                    endpoint = endpoint.replace("https://", "").replace("http://", "").replace("/mobile", "")
                    val certSha256 = extractJsonField(trimmed, "certificateSha256") ?: ""
                    val secret = extractJsonField(trimmed, "secret") ?: ""
                    val invitationId = extractJsonField(trimmed, "invitationId") ?: ""

                    val payload = QrPairingPayload(
                        nodeId = if (hostName.isNotBlank()) hostName else "windows-pc",
                        publicKey = if (certSha256.isNotBlank()) certSha256 else secret,
                        endpoint = if (endpoint.isNotBlank()) endpoint else "10.72.142.20:5081",
                        token = if (secret.isNotBlank()) secret else invitationId,
                        expiresAt = System.currentTimeMillis() + 600_000L,
                        invitationId = invitationId,
                        secret = secret,
                        certificateSha256 = certSha256,
                        hostName = hostName
                    )
                    return Result.success(payload)
                }

                // Support baymax://pair URI scheme
                if (trimmed.startsWith("baymax://pair")) {
                    val queryIndex = trimmed.indexOf("?")
                    if (queryIndex == -1) {
                        return Result.failure(IllegalArgumentException("Missing query parameters"))
                    }

                    val query = trimmed.substring(queryIndex + 1)
                    val params = query.split("&").mapNotNull { pair ->
                        val parts = pair.split("=")
                        if (parts.size == 2) decode(parts[0]) to decode(parts[1]) else null
                    }.toMap()

                    val nodeId = params["node_id"]
                        ?: return Result.failure(IllegalArgumentException("Missing node_id"))
                    val pubKey = params["pub_key"]
                        ?: return Result.failure(IllegalArgumentException("Missing pub_key"))
                    val endpoint = params["endpoint"]
                        ?: return Result.failure(IllegalArgumentException("Missing endpoint"))
                    val token = params["token"]
                        ?: return Result.failure(IllegalArgumentException("Missing token"))
                    val expiresAt = params["expires"]?.toLongOrNull()
                        ?: (System.currentTimeMillis() + 600_000L)

                    val payload = QrPairingPayload(
                        nodeId = nodeId,
                        publicKey = pubKey,
                        endpoint = endpoint,
                        token = token,
                        expiresAt = expiresAt,
                        invitationId = token,
                        secret = token,
                        certificateSha256 = pubKey,
                        hostName = nodeId
                    )

                    return if (payload.isExpired) {
                        Result.failure(IllegalStateException("Pairing token has expired"))
                    } else {
                        Result.success(payload)
                    }
                }

                Result.failure(IllegalArgumentException("Unrecognized pairing payload format"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
