package com.baymax.android.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.baymax.android.core.storage.BaymaxDatabase
import com.baymax.android.core.storage.EventRepository
import com.baymax.android.core.storage.PayloadCipher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OutboxWorkManagerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var database: BaymaxDatabase
    private lateinit var repository: EventRepository

    private lateinit var databaseName: String
    private lateinit var uniqueWorkName: String

    private var releaseWorker = CountDownLatch(1)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workManager = WorkManager.getInstance(context)

        val testId = UUID.randomUUID().toString()
        databaseName = "baymax_work_test_$testId.db"
        uniqueWorkName = "baymax-outbox-test-$testId"

        database = BaymaxDatabase(context, databaseName)
        repository = EventRepository(database, PayloadCipher())
    }

    @After
    fun tearDown() {
        releaseWorker.countDown()
        SyncTransportRegistry.transport = null

        workManager.cancelUniqueWork(uniqueWorkName).result.get(
            10,
            TimeUnit.SECONDS
        )

        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun workerAcknowledgementMarksEventAsSynced() {
        repository.createEvent("WorkManager success test")

        SyncTransportRegistry.transport = object : SyncTransport {
            override fun send(event: com.baymax.android.core.storage.EventItem): SyncResult {
                return SyncResult.Acknowledged
            }
        }

        OutboxScheduler.enqueue(
            context = context,
            databaseName = databaseName,
            uniqueWorkName = uniqueWorkName
        )

        val workId = getWorkId()
        val info = awaitWorkFinished(workId)

        assertNotNull(info)
        assertEquals(WorkInfo.State.SUCCEEDED, info!!.state)

        val events = repository.getEvents()
        assertTrue(events.isNotEmpty())
        assertEquals("SYNCED", events.first().syncStatus.toString())
    }

    @Test
    fun workerRetryKeepsEventPending() {
        repository.createEvent("WorkManager retry test")

        SyncTransportRegistry.transport = object : SyncTransport {
            override fun send(event: com.baymax.android.core.storage.EventItem): SyncResult {
                return SyncResult.RetryableFailure("Test network failure")
            }
        }

        OutboxScheduler.enqueue(
            context = context,
            databaseName = databaseName,
            uniqueWorkName = uniqueWorkName
        )

        val workId = getWorkId()
        val info = awaitRetryScheduled(workId)

        assertNotNull(info)
        assertEquals(WorkInfo.State.ENQUEUED, info!!.state)
        assertTrue(info.runAttemptCount >= 1)

        val events = repository.getEvents()
        assertTrue(events.isNotEmpty())
        assertEquals("PENDING", events.first().syncStatus.toString())
    }

    @Test
    fun duplicateEnqueueUsesOneUniqueWorkRequest() {
        repository.createEvent("Unique work test")

        val workerStarted = CountDownLatch(1)

        SyncTransportRegistry.transport = object : SyncTransport {
            override fun send(event: com.baymax.android.core.storage.EventItem): SyncResult {
                workerStarted.countDown()

                // Keep the first worker active while the second enqueue occurs.
                releaseWorker.await(10, TimeUnit.SECONDS)

                return SyncResult.Acknowledged
            }
        }

        OutboxScheduler.enqueue(
            context = context,
            databaseName = databaseName,
            uniqueWorkName = uniqueWorkName
        )

        assertTrue(
            "Worker did not start. Check emulator network connectivity.",
            workerStarted.await(20, TimeUnit.SECONDS)
        )

        // This should not create a second active request because KEEP is used.
        OutboxScheduler.enqueue(
            context = context,
            databaseName = databaseName,
            uniqueWorkName = uniqueWorkName
        )

        val workInfos = workManager
            .getWorkInfosForUniqueWork(uniqueWorkName)
            .get(10, TimeUnit.SECONDS)

        assertEquals(1, workInfos.size)

        releaseWorker.countDown()
    }

    private fun getWorkId(): UUID {
        val infos = workManager
            .getWorkInfosForUniqueWork(uniqueWorkName)
            .get(10, TimeUnit.SECONDS)

        assertTrue("No WorkManager request was found", infos.isNotEmpty())

        return infos.first().id
    }

    private fun awaitWorkFinished(
        workId: UUID,
        timeoutMs: Long = 30_000
    ): WorkInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val info = workManager.getWorkInfoById(workId)
                .get(5, TimeUnit.SECONDS)

            if (info != null && info.state.isFinished) {
                return info
            }

            Thread.sleep(200)
        }

        return workManager.getWorkInfoById(workId)
            .get(5, TimeUnit.SECONDS)
    }

    private fun awaitRetryScheduled(
        workId: UUID,
        timeoutMs: Long = 30_000
    ): WorkInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val info = workManager.getWorkInfoById(workId)
                .get(5, TimeUnit.SECONDS)

            if (
                info != null &&
                info.state == WorkInfo.State.ENQUEUED &&
                info.runAttemptCount >= 1
            ) {
                return info
            }

            Thread.sleep(200)
        }

        return workManager.getWorkInfoById(workId)
            .get(5, TimeUnit.SECONDS)
    }
}