package com.baymax.android.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.baymax.android.core.storage.BaymaxDatabase
import java.util.concurrent.TimeUnit

object OutboxScheduler {

    private const val UNIQUE_WORK_NAME = "baymax-outbox-sync"

    fun enqueue(
        context: Context,
        databaseName: String = BaymaxDatabase.DEFAULT_DATABASE_NAME,
        uniqueWorkName: String = UNIQUE_WORK_NAME
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString(
                OutboxSyncWorker.KEY_DATABASE_NAME,
                databaseName
            )
            .build()

        val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP,
                request
            )
    }
}