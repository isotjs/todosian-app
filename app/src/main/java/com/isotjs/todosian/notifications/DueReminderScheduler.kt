package com.isotjs.todosian.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DueReminderScheduler {
    private const val PERIODIC_WORK_NAME = "due_reminder_periodic"
    private const val IMMEDIATE_WORK_NAME = "due_reminder_immediate"

    fun sync(context: Context, enabled: Boolean) {
        if (enabled) {
            enqueue(context)
        } else {
            cancel(context)
            DueReminderNotifier.cancel(context)
        }
    }

    fun enqueue(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)

        val periodicRequest = PeriodicWorkRequestBuilder<DueReminderWorker>(6, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest,
        )

        val immediateRequest = OneTimeWorkRequestBuilder<DueReminderWorker>().build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            immediateRequest,
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }
}
