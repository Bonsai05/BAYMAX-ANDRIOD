package com.baymax.android.core.capabilities

enum class RiskLevel {
    LOW,        // Read status / local sync (Auto-approved)
    MEDIUM,     // Notifications / basic alerts (Auto or Token)
    HIGH,       // Database payload export / file read (User approval required)
    CRITICAL    // Remote command / adapter execution (Explicit confirmation + Token required)
}

data class Capability(
    val id: String,
    val name: String,
    val description: String,
    val riskLevel: RiskLevel,
    val requiresUserApproval: Boolean,
    val isEnabled: Boolean = true
)

object CapabilityRegistry {

    val REGISTERED_CAPABILITIES: List<Capability> = listOf(
        Capability(
            id = "cap.device_info",
            name = "Device Status & Battery",
            description = "Reads node battery status, storage availability, and device metadata.",
            riskLevel = RiskLevel.LOW,
            requiresUserApproval = false
        ),
        Capability(
            id = "cap.event_sync",
            name = "Outbox Event Sync",
            description = "Synchronizes encrypted outbox events with Windows Core.",
            riskLevel = RiskLevel.LOW,
            requiresUserApproval = false
        ),
        Capability(
            id = "cap.notifications",
            name = "Post System Notifications",
            description = "Displays alerts and task reminders on Android UI.",
            riskLevel = RiskLevel.MEDIUM,
            requiresUserApproval = false
        ),
        Capability(
            id = "cap.storage_export",
            name = "Export Local Database Payload",
            description = "Exports encrypted local event database for backup or diagnostic review.",
            riskLevel = RiskLevel.HIGH,
            requiresUserApproval = true
        ),
        Capability(
            id = "cap.tasker_exec",
            name = "Execute Automation Adapter",
            description = "Triggers local Tasker or MacroDroid automation scripts on device.",
            riskLevel = RiskLevel.CRITICAL,
            requiresUserApproval = true
        )
    )

    fun getCapability(id: String): Capability? {
        return REGISTERED_CAPABILITIES.firstOrNull { it.id == id }
    }
}
