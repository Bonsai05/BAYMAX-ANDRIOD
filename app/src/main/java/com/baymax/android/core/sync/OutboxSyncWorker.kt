
package com.baymax.android.core.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.baymax.android.core.storage.BaymaxDatabase
import com.baymax.android.core.storage.EventRepository
import com.baymax.android.core.storage.PayloadCipher

class OutboxSyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {

        // Get the registered sync transport.
        val transport = SyncTransportRegistry.transport
            ?: return Result.retry()

        // Read the database name passed by OutboxScheduler.
        val databaseName = inputData.getString(KEY_DATABASE_NAME)
            ?: BaymaxDatabase.DEFAULT_DATABASE_NAME

        // Open the local BAYMAX database.
        val database = BaymaxDatabase(
            applicationContext,
            databaseName
        )

        return try {

            // Create repository using the existing database.
            val repository = EventRepository(
                database,
                PayloadCipher()
            )

            // Process pending events.
            when (
                OutboxSyncProcessor(
                    repository,
                    transport
                ).process()
            ) {

                OutboxProcessResult.COMPLETED -> {
                    Result.success()
                }

                OutboxProcessResult.RETRY_SCHEDULED -> {
                    Result.retry()
                }

                OutboxProcessResult.MORE_PENDING -> {
                    Result.retry()
                }

                OutboxProcessResult.PERMANENT_FAILURE -> {
                    Result.failure()
                }
            }

        } catch (exception: Exception) {

            // Ask WorkManager to retry unexpected failures.
            Result.retry()

        } finally {

            // Always close the database.
            database.close()
        }
    }

    companion object {
        const val KEY_DATABASE_NAME = "database_name"
    }
}
