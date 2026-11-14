package com.baymax.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.baymax.android.core.capabilities.ApprovalManager
import com.baymax.android.core.capabilities.ApprovalRequest
import com.baymax.android.core.memory.MemoryItem
import com.baymax.android.core.memory.MemoryKind
import com.baymax.android.core.memory.PersistentMemoryStore
import com.baymax.android.core.memory.TrustTier
import com.baymax.android.core.network.ConnectionStateMachine
import com.baymax.android.core.network.ConnectionStatus
import com.baymax.android.core.network.MobileApiClient
import com.baymax.android.core.network.QrPairingPayload
import com.baymax.android.core.security.KeystoreSigner
import com.baymax.android.core.storage.BaymaxDatabase
import com.baymax.android.core.storage.DevicePreferences
import com.baymax.android.core.storage.EventItem
import com.baymax.android.core.storage.EventRepository
import com.baymax.android.core.storage.NodeConfig
import com.baymax.android.core.storage.PayloadCipher
import com.baymax.android.core.storage.ReadCache
import com.baymax.android.core.sync.GrpcSyncClient
import com.baymax.android.core.sync.OutboxScheduler
import com.baymax.android.core.sync.SyncResult
import com.baymax.android.core.sync.SyncTransport
import com.baymax.android.core.task.TaskCheckpoint
import com.baymax.android.core.task.TaskContextHandoffManager
import com.baymax.android.data.pairing.PairedDevice
import com.baymax.android.data.pairing.PairingRepository
import com.baymax.android.data.permissions.CapabilityGrant
import com.baymax.android.data.permissions.CapabilityRepository
import com.baymax.android.data.plans.ImmutablePlan
import com.baymax.android.data.plans.ImmutablePlanRepository
import com.baymax.android.data.tasks.AgentTask
import com.baymax.android.data.tasks.AgentTaskStatus
import com.baymax.android.data.tasks.TaskRepository
import com.baymax.android.ui.navigation.NavTab
import com.baymax.android.ui.screens.DashboardScreen
import com.baymax.android.ui.screens.EventsScreen
import com.baymax.android.ui.screens.MemoryAndHandoffScreen
import com.baymax.android.ui.screens.TaskComposerScreen
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var database: BaymaxDatabase
    private lateinit var repository: EventRepository
    private lateinit var cipher: PayloadCipher
    private lateinit var devicePrefs: DevicePreferences
    private lateinit var readCache: ReadCache
    private lateinit var connectionState: ConnectionStateMachine
    private lateinit var approvalManager: ApprovalManager
    private lateinit var syncClient: GrpcSyncClient
    private lateinit var memoryStore: PersistentMemoryStore
    private lateinit var handoffManager: TaskContextHandoffManager

    private lateinit var pairingRepo: PairingRepository
    private lateinit var taskRepo: TaskRepository
    private lateinit var capabilityRepo: CapabilityRepository
    private lateinit var keystoreSigner: KeystoreSigner
    private lateinit var mobileApiClient: MobileApiClient
    private lateinit var planRepo: ImmutablePlanRepository

    private var events by mutableStateOf<List<EventItem>>(emptyList())
    private var memories by mutableStateOf<List<MemoryItem>>(emptyList())
    private var taskCheckpoints by mutableStateOf<List<TaskCheckpoint>>(emptyList())
    private var pairedDevices by mutableStateOf<List<PairedDevice>>(emptyList())
    private var agentTasks by mutableStateOf<List<AgentTask>>(emptyList())
    private var capabilityGrants by mutableStateOf<List<CapabilityGrant>>(emptyList())
    private var immutablePlans by mutableStateOf<List<ImmutablePlan>>(emptyList())

    private var nodeConfig by mutableStateOf<NodeConfig>(
        NodeConfig("node-1", "BAYMAX Node", "192.168.1.100:50051", "", false, "UNPAIRED", 0L)
    )
    private var approvalRequests by mutableStateOf<List<ApprovalRequest>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cipher = PayloadCipher()
        database = BaymaxDatabase(applicationContext)
        repository = EventRepository(database, cipher)
        devicePrefs = DevicePreferences(applicationContext)
        readCache = ReadCache()
        connectionState = ConnectionStateMachine()
        approvalManager = ApprovalManager()
        syncClient = GrpcSyncClient(repository)
        memoryStore = PersistentMemoryStore(database)
        handoffManager = TaskContextHandoffManager(database)

        keystoreSigner = KeystoreSigner()
        keystoreSigner.getOrCreateDeviceKeyPair()

        mobileApiClient = MobileApiClient(keystoreSigner)
        pairingRepo = PairingRepository(database, mobileApiClient)
        taskRepo = TaskRepository(database, mobileApiClient).apply {
            onTaskUpdated = { refreshEvents() }
        }
        capabilityRepo = CapabilityRepository(database)
        planRepo = ImmutablePlanRepository(keystoreSigner)

        syncClient.setTransport(object : SyncTransport {
            override fun send(event: EventItem): SyncResult {
                return if (nodeConfig.isPaired) {
                    SyncResult.Acknowledged
                } else {
                    SyncResult.RetryableFailure("Core unreachable or unpaired")
                }
            }
        })

        nodeConfig = devicePrefs.getNodeConfig()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppLayout(
                        events = events,
                        memories = memories,
                        checkpoints = taskCheckpoints,
                        pairedDevices = pairedDevices,
                        agentTasks = agentTasks,
                        nodeConfig = nodeConfig,
                        connectionStatus = connectionState.status.collectAsState().value,
                        onSaveEvent = ::saveEvent,
                        onRefresh = ::refreshEvents,
                        onSyncNow = ::syncOutboxNow,
                        onSubmitTask = ::submitAgentTask,
                        onCancelTask = ::cancelAgentTask,
                        onPairWithQr = ::pairWithQr,
                        onUpdateEndpoint = ::updateEndpoint,
                        onAddMemory = ::addMemory,
                        onTriggerHandoff = ::triggerTaskHandoff,
                        onSimulateTokenExhaustion = ::simulateTokenExhaustion
                    )
                }
            }
        }

        refreshEvents()
    }

    private fun refreshEvents() {
        executor.execute {
            try {
                val loadedEvents = repository.getEvents()
                val loadedMemories = memoryStore.getAllMemories()
                val loadedCheckpoints = handoffManager.getAllCheckpoints()
                val loadedDevices = pairingRepo.getPairedDevices()
                val loadedTasks = taskRepo.getAllTasks()
                val loadedGrants = capabilityRepo.getGrants()
                val loadedPlans = planRepo.getAllPlans()

                readCache.updateEvents(loadedEvents)
                val config = devicePrefs.getNodeConfig()
                val reqs = approvalManager.getAllRequests()

                runOnUiThread {
                    events = loadedEvents
                    memories = loadedMemories
                    taskCheckpoints = loadedCheckpoints
                    pairedDevices = loadedDevices
                    agentTasks = loadedTasks
                    capabilityGrants = loadedGrants
                    immutablePlans = loadedPlans
                    nodeConfig = config
                    approvalRequests = reqs
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun saveEvent(text: String) {
        if (text.isBlank()) return
        executor.execute {
            try {
                val newEvent = repository.createEvent(text)
                readCache.putEvent(newEvent)

                OutboxScheduler.enqueue(applicationContext)

                val updatedEvents = repository.getEvents()

                runOnUiThread {
                    events = updatedEvents
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun syncOutboxNow() {
        executor.execute {
            try {
                OutboxScheduler.enqueue(applicationContext)
                val updatedEvents = repository.getEvents()
                runOnUiThread {
                    events = updatedEvents
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun submitAgentTask(instruction: String, targetDeviceId: String) {
        executor.execute {
            taskRepo.submitTask(
                instruction = instruction,
                originDeviceId = nodeConfig.nodeId,
                targetDeviceId = targetDeviceId,
                endpoint = nodeConfig.coreEndpoint
            )
            refreshEvents()
        }
    }

    private fun cancelAgentTask(taskId: String) {
        executor.execute {
            taskRepo.updateTaskStatus(taskId, AgentTaskStatus.CANCELLED, "Cancelled by user on Android remote")
            refreshEvents()
        }
    }

    private fun addMemory(content: String, kind: MemoryKind, trustTier: TrustTier) {
        executor.execute {
            memoryStore.addMemory(
                content = content,
                kind = kind,
                trustTier = trustTier,
                sourceDevice = nodeConfig.nodeId
            )
            refreshEvents()
        }
    }

    private fun triggerTaskHandoff(taskId: String, targetNodeId: String) {
        executor.execute {
            handoffManager.triggerHandoff(taskId, targetNodeId)
            refreshEvents()
        }
    }

    private fun simulateTokenExhaustion() {
        executor.execute {
            handoffManager.createOrUpdateCheckpoint(
                title = "Stage 12 Cross-Device Continuity Analysis",
                currentStep = "Step 3: Compiling RAG context and model memory handoff pack",
                tokenCount = 3850,
                tokenLimit = 4096,
                conversationHistory = listOf(
                    "User: Analyze stage 12 verification requirement.",
                    "BAYMAX Model: Processed memory admission and SQLite checkpoints.",
                    "BAYMAX Model: Token threshold 90% reached. Generating context pack for handoff."
                ),
                currentNodeId = nodeConfig.nodeId
            )
            refreshEvents()
        }
    }

    private fun pairWithQr(payload: QrPairingPayload) {
        executor.execute {
            connectionState.setPairingPending(payload.nodeId, "Sending registration request to ${payload.endpoint}...")

            pairingRepo.registerAndPollWithPayload(
                payload = payload,
                deviceName = "BAYMAX Android Node (${android.os.Build.MODEL})",
                onStatusUpdate = { msg ->
                    runOnUiThread {
                        connectionState.setPairingPending(payload.nodeId, msg)
                    }
                },
                onSuccess = { dev ->
                    devicePrefs.updateConfig(
                        nodeId = dev.deviceId,
                        coreEndpoint = payload.endpoint,
                        peerPublicKey = payload.publicKey,
                        isPaired = true,
                        trustStatus = "TRUSTED"
                    )
                    runOnUiThread {
                        nodeConfig = devicePrefs.getNodeConfig()
                        connectionState.setConnected(payload.endpoint, 12L)
                    }
                    refreshEvents()
                },
                onFailure = { err ->
                    runOnUiThread {
                        connectionState.setError(err)
                    }
                }
            )
        }
    }

    private fun updateEndpoint(endpoint: String) {
        devicePrefs.updateConfig(coreEndpoint = endpoint)
        nodeConfig = devicePrefs.getNodeConfig()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun MainAppLayout(
    events: List<EventItem>,
    memories: List<MemoryItem>,
    checkpoints: List<TaskCheckpoint>,
    pairedDevices: List<PairedDevice>,
    agentTasks: List<AgentTask>,
    nodeConfig: NodeConfig,
    connectionStatus: ConnectionStatus,
    onSaveEvent: (String) -> Unit,
    onRefresh: () -> Unit,
    onSyncNow: () -> Unit,
    onSubmitTask: (String, String) -> Unit,
    onCancelTask: (String) -> Unit,
    onPairWithQr: (QrPairingPayload) -> Unit,
    onUpdateEndpoint: (String) -> Unit,
    onAddMemory: (String, MemoryKind, TrustTier) -> Unit,
    onTriggerHandoff: (String, String) -> Unit,
    onSimulateTokenExhaustion: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(NavTab.TASKS) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(text = tab.icon) },
                        label = { Text(text = tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavTab.TASKS -> TaskComposerScreen(
                    pairedDevices = pairedDevices,
                    activeTasks = agentTasks,
                    onSubmitTask = onSubmitTask,
                    onCancelTask = onCancelTask
                )

                NavTab.MEMORY_HANDOFF -> MemoryAndHandoffScreen(
                    memories = memories,
                    checkpoints = checkpoints,
                    onAddMemory = onAddMemory,
                    onTriggerHandoff = onTriggerHandoff,
                    onSimulateTokenExhaustion = onSimulateTokenExhaustion
                )

                NavTab.EVENTS -> EventsScreen(
                    events = events,
                    loading = false,
                    error = null,
                    onSaveEvent = onSaveEvent,
                    onRefresh = onRefresh,
                    onSyncNow = onSyncNow
                )

                NavTab.DASHBOARD -> DashboardScreen(
                    nodeConfig = nodeConfig,
                    connectionStatus = connectionStatus,
                    onPairWithQr = onPairWithQr,
                    onUpdateEndpoint = onUpdateEndpoint
                )
            }
        }
    }
}
