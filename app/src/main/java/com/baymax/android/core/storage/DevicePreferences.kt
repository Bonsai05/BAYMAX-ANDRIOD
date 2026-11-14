package com.baymax.android.core.storage

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

data class NodeConfig(
    val nodeId: String,
    val nodeName: String,
    val coreEndpoint: String,
    val peerPublicKey: String,
    val isPaired: Boolean,
    val trustStatus: String,
    val lastSyncTime: Long
)

class DevicePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getOrCreateNodeId(): String {
        val existing = prefs.getString(KEY_NODE_ID, null)
        if (!existing.isNullOrEmpty()) {
            return existing
        }
        val newId = "android-node-" + UUID.randomUUID().toString().take(8)
        prefs.edit().putString(KEY_NODE_ID, newId).apply()
        return newId
    }

    fun getNodeConfig(): NodeConfig {
        return NodeConfig(
            nodeId = getOrCreateNodeId(),
            nodeName = prefs.getString(KEY_NODE_NAME, "BAYMAX Android Node") ?: "BAYMAX Android Node",
            coreEndpoint = prefs.getString(KEY_CORE_ENDPOINT, "192.168.1.100:50051") ?: "192.168.1.100:50051",
            peerPublicKey = prefs.getString(KEY_PEER_PUBKEY, "") ?: "",
            isPaired = prefs.getBoolean(KEY_IS_PAIRED, false),
            trustStatus = prefs.getString(KEY_TRUST_STATUS, "UNPAIRED") ?: "UNPAIRED",
            lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        )
    }

    fun updateConfig(
        nodeId: String? = null,
        coreEndpoint: String? = null,
        peerPublicKey: String? = null,
        isPaired: Boolean? = null,
        trustStatus: String? = null,
        lastSyncTime: Long? = null
    ) {
        val editor = prefs.edit()
        nodeId?.let { editor.putString(KEY_NODE_ID, it) }
        coreEndpoint?.let { editor.putString(KEY_CORE_ENDPOINT, it) }
        peerPublicKey?.let { editor.putString(KEY_PEER_PUBKEY, it) }
        isPaired?.let { editor.putBoolean(KEY_IS_PAIRED, it) }
        trustStatus?.let { editor.putString(KEY_TRUST_STATUS, it) }
        lastSyncTime?.let { editor.putLong(KEY_LAST_SYNC_TIME, it) }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "baymax_device_prefs"
        private const val KEY_NODE_ID = "node_id"
        private const val KEY_NODE_NAME = "node_name"
        private const val KEY_CORE_ENDPOINT = "core_endpoint"
        private const val KEY_PEER_PUBKEY = "peer_pubkey"
        private const val KEY_IS_PAIRED = "is_paired"
        private const val KEY_TRUST_STATUS = "trust_status"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    }
}
