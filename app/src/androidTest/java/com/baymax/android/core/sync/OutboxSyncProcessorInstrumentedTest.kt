
package com.baymax.android.core.sync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baymax.android.core.storage.BaymaxDatabase
import com.baymax.android.core.storage.EventItem
import com.baymax.android.core.storage.EventRepository
import com.baymax.android.core.storage.PayloadCipher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxSyncProcessorInstrumentedTest {

    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: BaymaxDatabase
    private lateinit var repository: EventRepository
    private lateinit var cipher: PayloadCipher

    @Before
    fun setUp() {
        context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        databaseName = "outbox_test_${System.nanoTime()}.db"

        cipher = PayloadCipher()
        database = BaymaxDatabase(context, databaseName)
        repository = EventRepository(database, cipher)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private class FakeTransport(
        private val response: (EventItem) -> SyncResult
    ) : SyncTransport {

        val receivedIds = mutableListOf<String>()

        override fun send(event: EventItem): SyncResult {
            receivedIds.add(event.id)
            return response(event)
        }
    }

    @Test
    fun failedDeliveryKeepsEventPendingAndRecordsRetry() {

        val event = repository.createEvent("Retry test")

        val transport = FakeTransport {
            SyncResult.RetryableFailure("Windows Core unavailable")
        }

        val processor = OutboxSyncProcessor(
            repository,
            transport
        )

        val result = processor.process()

        val stored = repository.getEvents()
            .single { it.id == event.id }

        assertEquals(
            OutboxProcessResult.RETRY_SCHEDULED,
            result
        )

        assertEquals("PENDING", stored.syncStatus)
        assertEquals(1, stored.attempts)
        assertEquals(
            "Windows Core unavailable",
            stored.lastError
        )

        assertTrue(stored.nextRetryAt > System.currentTimeMillis())
    }

    @Test
    fun acknowledgementChangesStatusToSynced() {

        val event = repository.createEvent("Acknowledgement test")

        val transport = FakeTransport {
            SyncResult.Acknowledged
        }

        val processor = OutboxSyncProcessor(
            repository,
            transport
        )

        val result = processor.process()

        val stored = repository.getEvents()
            .single { it.id == event.id }

        assertEquals(
            OutboxProcessResult.COMPLETED,
            result
        )

        assertEquals("SYNCED", stored.syncStatus)
    }

    @Test
    fun failedAttemptAndRetryUseSameEventId() {

        val event = repository.createEvent("Idempotency test")

        var attempt = 0

        val transport = FakeTransport {
            attempt++

            if (attempt == 1) {
                SyncResult.RetryableFailure("Temporary failure")
            } else {
                SyncResult.Acknowledged
            }
        }

        val processor = OutboxSyncProcessor(
            repository,
            transport
        )

        processor.process(now = System.currentTimeMillis())

        // Move beyond the saved retry deadline.
        val retryTime = System.currentTimeMillis() + 120_000

        val result = processor.process(now = retryTime)

        assertEquals(
            OutboxProcessResult.COMPLETED,
            result
        )

        assertEquals(2, transport.receivedIds.size)

        assertEquals(
            transport.receivedIds[0],
            transport.receivedIds[1]
        )

        assertEquals(event.id, transport.receivedIds[0])

        assertEquals(
            "SYNCED",
            repository.getEvents()
                .single { it.id == event.id }
                .syncStatus
        )
    }

    @Test
    fun pendingEventSurvivesDatabaseReopenAndCanBeRecovered() {

        val event = repository.createEvent("Recovery test")

        // Close the original database.
        database.close()

        // Reopen the same database.
        database = BaymaxDatabase(context, databaseName)

        repository = EventRepository(database, cipher)

        val restored = repository.getEvents()
            .single { it.id == event.id }

        assertEquals("Recovery test", restored.text)
        assertEquals("PENDING", restored.syncStatus)

        val transport = FakeTransport {
            SyncResult.Acknowledged
        }

        val processor = OutboxSyncProcessor(
            repository,
            transport
        )

        val result = processor.process()

        assertEquals(
            OutboxProcessResult.COMPLETED,
            result
        )

        assertEquals(
            "SYNCED",
            repository.getEvents()
                .single { it.id == event.id }
                .syncStatus
        )
    }

    @Test
    fun noAcknowledgementNeverMarksEventSynced() {

        val event = repository.createEvent("No acknowledgement test")

        val transport = FakeTransport {
            SyncResult.RetryableFailure("No durable acknowledgement")
        }

        val processor = OutboxSyncProcessor(
            repository,
            transport
        )

        processor.process()

        val stored = repository.getEvents()
            .single { it.id == event.id }

        assertEquals("PENDING", stored.syncStatus)
    }
}
