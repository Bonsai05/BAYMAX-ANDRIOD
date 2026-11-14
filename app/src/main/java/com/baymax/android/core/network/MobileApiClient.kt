package com.baymax.android.core.network

import android.util.Log
import com.baymax.android.core.security.KeystoreSigner
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class PairingRegisterResponse(
    val requestId: String,
    val pollingSecret: String,
    val expiresAt: String
)

data class PairingPollResponse(
    val status: String, // PENDING, PAIRED, REJECTED, EXPIRED
    val deviceId: String?
)

data class MobileTaskResponse(
    val taskId: String,
    val status: String
)

class MobileApiClient(private val keystoreSigner: KeystoreSigner) {

    companion object {
        private const val TAG = "BAYMAX_HTTP"
    }

    private val sslContext: SSLContext by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
    }

    /**
     * Sends POST /mobile/pairing/register to Windows Host over LAN HTTPS.
     */
    fun registerDevice(
        endpoint: String,
        invitationId: String,
        secret: String,
        deviceName: String
    ): Result<PairingRegisterResponse> {
        return try {
            val (pubKey, pubKeyBase64) = keystoreSigner.getOrCreateDeviceKeyPair()
            val registrationSignature = keystoreSigner.signRegistration(
                invitationId = invitationId,
                secret = secret,
                deviceName = deviceName,
                publicKeyBase64 = pubKeyBase64
            )

            val bodyJson = JSONObject().apply {
                put("invitationId", invitationId)
                put("secret", secret)
                put("deviceName", deviceName)
                put("publicKey", pubKeyBase64)
                put("signature", registrationSignature)
            }.toString()

            val baseUrl = sanitizeEndpoint(endpoint)
            val fullUrl = "$baseUrl/mobile/pairing/register"
            Log.d(TAG, "Posting pairing registration to $fullUrl...")

            val (code, responseBody) = sendHttpRequest(
                urlStr = fullUrl,
                method = "POST",
                body = bodyJson
            )

            Log.d(TAG, "POST /mobile/pairing/register response code: $code, body: $responseBody")

            if (code == 200 || code == 201) {
                val json = JSONObject(responseBody)
                Result.success(
                    PairingRegisterResponse(
                        requestId = json.optString("requestId", UUID.randomUUID().toString()),
                        pollingSecret = json.optString("pollingSecret", secret),
                        expiresAt = json.optString("expiresAt", "")
                    )
                )
            } else {
                Result.failure(RuntimeException("Pairing registration failed with HTTP $code: $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during pairing registration: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Polls GET /mobile/pairing/requests/{requestId} using X-Baymax-Pairing-Poll-Secret header.
     */
    fun pollPairingStatus(
        endpoint: String,
        requestId: String,
        pollingSecret: String
    ): Result<PairingPollResponse> {
        return try {
            val baseUrl = sanitizeEndpoint(endpoint)
            val fullUrl = "$baseUrl/mobile/pairing/requests/$requestId"

            val headers = mapOf("X-Baymax-Pairing-Poll-Secret" to pollingSecret)
            val (code, responseBody) = sendHttpRequest(
                urlStr = fullUrl,
                method = "GET",
                headers = headers
            )

            Log.d(TAG, "GET /mobile/pairing/requests/$requestId -> HTTP $code, body: $responseBody")

            if (code == 200) {
                val json = JSONObject(responseBody)
                Result.success(
                    PairingPollResponse(
                        status = json.optString("status", "PENDING"),
                        deviceId = if (json.isNull("deviceId")) null else json.optString("deviceId")
                    )
                )
            } else {
                Result.failure(RuntimeException("Poll failed with HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during pairing status poll: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Submits a signed mobile task to POST /mobile/tasks as specified in communication.md.
     */
    fun submitSignedTask(
        endpoint: String,
        deviceId: String,
        userInstruction: String = "W7 harmless mobile connectivity test",
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<MobileTaskResponse> {
        return try {
            val baseUrl = sanitizeEndpoint(endpoint)
            val canonicalPath = "/mobile/tasks"
            val fullUrl = "$baseUrl$canonicalPath"

            val bodyJson = JSONObject().apply {
                put("userInstruction", userInstruction)
                put("idempotencyKey", idempotencyKey)
            }.toString()

            val timestampSeconds = System.currentTimeMillis() / 1000L
            val nonce = UUID.randomUUID().toString()
            val signature = keystoreSigner.signMobileRequest(
                method = "POST",
                canonicalPath = canonicalPath,
                deviceUuid = deviceId,
                timestampSeconds = timestampSeconds,
                nonce = nonce,
                bodyBytes = bodyJson.toByteArray(Charsets.UTF_8)
            )

            val headers = mapOf(
                "X-Baymax-Device-Id" to deviceId,
                "X-Baymax-Timestamp" to timestampSeconds.toString(),
                "X-Baymax-Nonce" to nonce,
                "X-Baymax-Signature" to signature
            )

            val (code, responseBody) = sendHttpRequest(
                urlStr = fullUrl,
                method = "POST",
                headers = headers,
                body = bodyJson
            )

            Log.d(TAG, "POST /mobile/tasks -> HTTP $code, body: $responseBody")

            if (code == 200 || code == 201 || code == 202) {
                val json = JSONObject(responseBody)
                val taskId = json.optString("taskId", idempotencyKey)
                val status = json.optString("status", "Queued")
                Result.success(MobileTaskResponse(taskId, status))
            } else {
                Result.failure(RuntimeException("Task submission failed with HTTP $code: $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during signed task submission: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Polls GET /mobile/tasks/{taskId} using a fresh timestamp, nonce, and signature for every request.
     */
    fun pollTaskStatus(
        endpoint: String,
        deviceId: String,
        taskId: String
    ): Result<MobileTaskResponse> {
        return try {
            val baseUrl = sanitizeEndpoint(endpoint)
            val canonicalPath = "/mobile/tasks/$taskId"
            val fullUrl = "$baseUrl$canonicalPath"

            val timestampSeconds = System.currentTimeMillis() / 1000L
            val nonce = UUID.randomUUID().toString()
            val emptyBody = ByteArray(0)

            val signature = keystoreSigner.signMobileRequest(
                method = "GET",
                canonicalPath = canonicalPath,
                deviceUuid = deviceId,
                timestampSeconds = timestampSeconds,
                nonce = nonce,
                bodyBytes = emptyBody
            )

            val headers = mapOf(
                "X-Baymax-Device-Id" to deviceId,
                "X-Baymax-Timestamp" to timestampSeconds.toString(),
                "X-Baymax-Nonce" to nonce,
                "X-Baymax-Signature" to signature
            )

            val (code, responseBody) = sendHttpRequest(
                urlStr = fullUrl,
                method = "GET",
                headers = headers
            )

            Log.d(TAG, "GET /mobile/tasks/$taskId -> HTTP $code, body: $responseBody")

            if (code == 200) {
                val json = JSONObject(responseBody)
                val status = json.optString("status", "Running")
                Result.success(MobileTaskResponse(taskId, status))
            } else {
                Result.failure(RuntimeException("Poll task failed with HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error during task poll: ${e.message}")
            Result.failure(e)
        }
    }

    private fun sendHttpRequest(
        urlStr: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): Pair<Int, String> {
        val url = URL(urlStr)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            if (this is HttpsURLConnection) {
                sslSocketFactory = sslContext.socketFactory
                hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            }
            requestMethod = method
            connectTimeout = 5000
            readTimeout = 5000
            doInput = true

            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")

            body?.let {
                doOutput = true
                outputStream.use { os ->
                    os.write(it.toByteArray(Charsets.UTF_8))
                }
            }
        }

        val responseCode = connection.responseCode
        val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseText = inputStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""

        connection.disconnect()
        return responseCode to responseText
    }

    private fun sanitizeEndpoint(rawEndpoint: String): String {
        var ep = rawEndpoint.trim()
            .replace("http://", "")
            .replace("https://", "")
            .replace("/mobile", "")

        if (!ep.startsWith("https://")) {
            ep = "https://$ep"
        }
        return ep
    }
}
