package com.isotjs.todosian.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.isotjs.todosian.MainActivity
import com.isotjs.todosian.R
import java.time.LocalDate

object DueReminderNotifier {
    private const val CHANNEL_ID = "todosian_due_reminders"
    private const val CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_DEFAULT
    private const val NOTIFICATION_ID = 5001

    data class Payload(
        val totalCount: Int,
        val nextDueDate: LocalDate,
        val topTodoText: String,
    )

    @SuppressLint("MissingPermission")
    fun show(context: Context, payload: Payload) {
        createChannelIfNeeded(context)

        val appContext = context.applicationContext
        if (!canPostNotifications(appContext)) return

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = appContext.resources.getQuantityString(
            R.plurals.notification_due_title,
            payload.totalCount,
            payload.totalCount,
        )

        val body = if (payload.totalCount == 1) {
            appContext.getString(
                R.string.notification_due_body_single,
                payload.topTodoText,
                dueLabel(appContext, payload.nextDueDate),
            )
        } else {
            appContext.getString(
                R.string.notification_due_body_multi,
                dueLabel(appContext, payload.nextDueDate),
                payload.totalCount,
            )
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        }.getOrElse { throwable ->
            if (throwable !is SecurityException) throw throwable
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private fun dueLabel(context: Context, dueDate: LocalDate): String {
        val today = LocalDate.now()
        val days = dueDate.toEpochDay() - today.toEpochDay()
        return when {
            days < 0L -> context.getString(R.string.notification_due_when_overdue)
            days == 0L -> context.getString(R.string.notification_due_when_today)
            days == 1L -> context.getString(R.string.notification_due_when_tomorrow)
            else -> context.getString(R.string.notification_due_when_in_days, days)
        }
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.notification_due_channel_name),
            CHANNEL_IMPORTANCE,
        ).apply {
            description = appContext.getString(R.string.notification_due_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
