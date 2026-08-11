package com.isotjs.todosian.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.isotjs.todosian.data.settings.SharedPrefsAppSettingsRepository
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime

object DueReminderScheduler {
    const val ACTION_DUE_REMINDER = "com.isotjs.todosian.action.DUE_REMINDER"

    private const val REQUEST_CODE = 7001

    fun sync(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        if (enabled) {
            schedule(context, hour, minute)
        } else {
            cancel(context)
            DueReminderNotifier.cancel(context)
        }
    }

    /**
     * Re-arms the next alarm from the currently stored settings.Called by the receiver after an alarm fires and on boot.
     */
    suspend fun reschedule(context: Context) {
        val settings = SharedPrefsAppSettingsRepository(context).settings.first()
        if (settings.enableTasksPluginSupport) {
            schedule(context, settings.reminderTimeHour, settings.reminderTimeMinute)
        }
    }

    fun schedule(context: Context, hour: Int, minute: Int) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextTriggerMillis(hour, minute, ZonedDateTime.now()),
            pendingIntent(appContext),
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent(appContext))
    }

    /** First occurrence of [hour]:[minute] at or after [now]; tomorrow if it already passed. */
    fun nextTriggerMillis(hour: Int, minute: Int, now: ZonedDateTime): Long {
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DueReminderReceiver::class.java).apply {
            action = ACTION_DUE_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
