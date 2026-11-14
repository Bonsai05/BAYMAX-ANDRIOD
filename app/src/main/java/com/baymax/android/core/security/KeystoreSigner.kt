package com.baymax.android.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import java.util.UUID

class KeystoreSigner {

    companion object {
        private const val KEY_ALIAS = "baymax_android_device_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }

    private var fallbackKeyPair: KeyPair? = null

    fun getOrCreateDeviceKeyPair(): Pair<PublicKey, String> {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC,
                    KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build()

                keyPairGenerator.initialize(spec)
                keyPairGenerator.generateKeyPair()
            }

            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            val publicKey = entry.certificate.publicKey
            val publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.encoded)

            publicKey to publicKeyBase64
        } catch (_: Exception) {
            // Fallback for Host JVM Unit Tests where AndroidKeyStore provider is unavailable
            val pair = fallbackKeyPair ?: run {
                val kpg = KeyPairGenerator.getInstance("EC")
                kpg.initialize(256)
                val generated = kpg.generateKeyPair()
                fallbackKeyPair = generated
                generated
            }
            val pubKeyBase64 = Base64.getEncoder().encodeToString(pair.public.encoded)
            pair.public to pubKeyBase64
        }
    }

    private fun getPrivateKey(): PrivateKey {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            entry.privateKey
        } catch (_: Exception) {
            getOrCreateDeviceKeyPair()
            fallbackKeyPair!!.private
        }
    }

    /**
     * Exact signature format from communication.md Section 3:
     * BAYMAX-PAIRING-REGISTRATION-V1
     * {lowercase invitation UUID}
     * {invitation secret}
     * {device name}
     * {base64 public key}
     */
    fun signRegistration(
        invitationId: String,
        secret: String,
        deviceName: String,
        publicKeyBase64: String
    ): String {
        getOrCreateDeviceKeyPair()
        val canonicalPayload = "BAYMAX-PAIRING-REGISTRATION-V1\n" +
                "${invitationId.lowercase()}\n" +
                "$secret\n" +
                "$deviceName\n" +
                publicKeyBase64

        val signatureBytes = Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(getPrivateKey())
            update(canonicalPayload.toByteArray(Charsets.UTF_8))
            sign()
        }
        return Base64.getEncoder().encodeToString(signatureBytes)
    }

    /**
     * Exact post-pairing signature format from communication.md:
     * BAYMAX-MOBILE-REQUEST-V1
     * {uppercase HTTP method}
     * {canonical path and query}
     * {lowercase device UUID}
     * {Unix timestamp seconds}
     * {unique nonce}
     * {lowercase hex SHA-256 of exact request body bytes}
     */
    fun signMobileRequest(
        method: String,
        canonicalPath: String,
        deviceUuid: String,
        timestampSeconds: Long,
        nonce: String,
        bodyBytes: ByteArray
    ): String {
        getOrCreateDeviceKeyPair()
        val bodySha256Hex = computeSha256Hex(bodyBytes)
        val canonicalPayload = "BAYMAX-MOBILE-REQUEST-V1\n" +
                "${method.uppercase()}\n" +
                "$canonicalPath\n" +
                "${deviceUuid.lowercase()}\n" +
                "$timestampSeconds\n" +
                "$nonce\n" +
                bodySha256Hex

        val signatureBytes = Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(getPrivateKey())
            update(canonicalPayload.toByteArray(Charsets.UTF_8))
            sign()
        }
        return Base64.getEncoder().encodeToString(signatureBytes)
    }

    fun signApproval(
        planId: String,
        planHash: String,
        decision: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis() / 1000L,
        nonce: String = UUID.randomUUID().toString()
    ): String {
        getOrCreateDeviceKeyPair()
        val bodyJson = "{\"decisionId\":\"$nonce\",\"decision\":\"$decision\",\"planHash\":\"$planHash\",\"decidedAt\":\"$timestamp\"}"
        return signMobileRequest(
            method = "POST",
            canonicalPath = "/mobile/requests/$planId/decision",
            deviceUuid = deviceId,
            timestampSeconds = timestamp,
            nonce = nonce,
            bodyBytes = bodyJson.toByteArray(Charsets.UTF_8)
        )
    }

    private fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
