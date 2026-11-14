package com.baymax.android

import com.baymax.android.core.network.QrPairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPairingPayloadUnitTest {

    @Test
    fun validWindowsJsonPayloadParsesSuccessfully() {
        val jsonStr = """
            {"version":1,"endpoint":"https://10.72.142.20:5081/mobile","certificateSha256":"43ff84c1df9e06c6aac70c114c550ccb2c5245266823e85b19b02d6896c7ee3e","invitationId":"864bcc35-11c5-404b-89e9-453a93a88db9","secret":"O6Y2aNoDouUVWwfp7xkLCy30/oaXoLvXwStY\u002B8D2\u002BsM=","hostName":"BONSAI"}
        """.trimIndent()

        val result = QrPairingPayload.parse(jsonStr)

        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals("BONSAI", payload.nodeId)
        assertEquals("10.72.142.20:5081", payload.endpoint)
        assertEquals("43ff84c1df9e06c6aac70c114c550ccb2c5245266823e85b19b02d6896c7ee3e", payload.publicKey)
        assertFalse(payload.isExpired)
    }

    @Test
    fun validQrUriParsesSuccessfully() {
        val uriStr = "baymax://pair?node_id=win-core-1&pub_key=KEY123&endpoint=192.168.1.50:50051&token=TOK999&expires=${System.currentTimeMillis() + 60000}"
        val result = QrPairingPayload.parse(uriStr)

        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals("win-core-1", payload.nodeId)
        assertEquals("KEY123", payload.publicKey)
        assertEquals("192.168.1.50:50051", payload.endpoint)
        assertEquals("TOK999", payload.token)
        assertFalse(payload.isExpired)
    }

    @Test
    fun invalidSchemeFailsValidation() {
        val uriStr = "https://example.com/pair?node_id=1"
        val result = QrPairingPayload.parse(uriStr)

        assertTrue(result.isFailure)
    }
}
