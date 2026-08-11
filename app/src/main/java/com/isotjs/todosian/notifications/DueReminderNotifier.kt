package com.isotjs.todosian.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.isotjs.todosian.MainActivity
import com.isotjs.todosian.R
import com.isotjs.todosian.data.PreferencesManager
import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.data.settings.SharedPrefsAppSettingsRepository
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.flow.first
import java.time.LocalDate

object DueReminderNotifier {
    private const val CHANNEL_ID = "todosian_due_reminders"
    private const val CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_DEFAULT
    private const val NOTIFICATION_ID = 5001

    private const val STATE_PREFS_NAME = "todosian_due_reminder_state"
    private const val KEY_LAST_DAY = "last_day"
    private const val KEY_LAST_SIGNATURE = "last_signature"

    data class Payload(
        val totalCount: Int,
        val nextDueDate: LocalDate,
        val topTodoText: String,
    )

    /**
     * Scans the selected folder for due-soon todos and shows a reminder if there is anything new to report. Returns true when a notification was shown.
     */
    suspend fun checkAndNotify(context: Context): Boolean {
        val appContext = context.applicationContext

        val settings = SharedPrefsAppSettingsRepository(appContext).settings.first()
        if (!settings.enableTasksPluginSupport) return false

        if (!canPostNotifications(appContext)) return false

        val folderUri = PreferencesManager(appContext).getFolderUri()
            ?: return false

        val dueSoon = runCatching { loadDueSoonTodos(appContext, folderUri) }
            .getOrElse { return false }

        if (dueSoon.isEmpty()) {
            cancel(appContext)
            clearSignatureState(appContext)
            return false
        }

        val signature = dueSoon.joinToString(separator = "|") {
            "${it.fileName}#${it.lineIndex}#${it.dueDate}#${it.text}"
        }
        if (alreadySentToday(appContext, signature)) return false

        val first = dueSoon.first()
        show(
            context = appContext,
            payload = Payload(
                totalCount = dueSoon.size,
                nextDueDate = first.dueDate,
                topTodoText = first.text,
            ),
        )
        saveSentToday(appContext, signature)
        return true
    }

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
            appContext.resources.getQuantityString(
                R.plurals.notification_due_body_multi,
                payload.totalCount,
                dueLabel(appContext, payload.nextDueDate),
                payload.totalCount,
            )
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
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

    private fun loadDueSoonTodos(context: Context, folderUri: Uri): List<DueTodo> {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        val today = LocalDate.now()

        return folder.listFiles()
            .asSequence()
            .filter { it.isFile }
            .filter {
                val name = it.name.orEmpty()
                name.endsWith(".md", ignoreCase = true) && !name.contains("sync-conflict", ignoreCase = true)
            }
            .flatMap { file ->
                parseDueTodosFromFile(context, file, today).asSequence()
            }
            .sortedWith(compareBy<DueTodo> { it.dueDate }.thenBy { it.text.lowercase() })
            .toList()
    }

    private fun parseDueTodosFromFile(context: Context, file: DocumentFile, today: LocalDate): List<DueTodo> {
        val lines = context.contentResolver.openInputStream(file.uri)?.use { input ->
            input.bufferedReader().readLines()
        } ?: return emptyList()

        return MarkdownParser.parse(lines)
            .asSequence()
            .filter { !it.isDone }
            .mapNotNull { todo -> mapDueTodo(todo, file, today) }
            .toList()
    }

    private fun mapDueTodo(todo: Todo, file: DocumentFile, today: LocalDate): DueTodo? {
        val due = todo.dueDate?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
            ?: return null
        val daysUntil = due.toEpochDay() - today.toEpochDay()
        if (daysUntil > 2L) return null

        return DueTodo(
            fileName = file.name.orEmpty(),
            lineIndex = todo.lineIndex,
            text = todo.text.trim().ifEmpty { "Untitled task" },
            dueDate = due,
        )
    }

    private fun alreadySentToday(context: Context, signature: String): Boolean {
        val prefs = statePrefs(context)
        val today = LocalDate.now().toString()
        val lastDay = prefs.getString(KEY_LAST_DAY, null)
        val lastSignature = prefs.getString(KEY_LAST_SIGNATURE, null)
        return lastDay == today && lastSignature == signature
    }

    private fun saveSentToday(context: Context, signature: String) {
        statePrefs(context).edit {
            putString(KEY_LAST_DAY, LocalDate.now().toString())
            putString(KEY_LAST_SIGNATURE, signature)
        }
    }

    private fun clearSignatureState(context: Context) {
        statePrefs(context).edit {
            remove(KEY_LAST_DAY)
            remove(KEY_LAST_SIGNATURE)
        }
    }

    private fun statePrefs(context: Context) =
        context.getSharedPreferences(STATE_PREFS_NAME, Context.MODE_PRIVATE)

    private data class DueTodo(
        val fileName: String,
        val lineIndex: Int,
        val text: String,
        val dueDate: LocalDate,
    )

    private fun dueLabel(context: Context, dueDate: LocalDate): String {
        val today = LocalDate.now()
        val days = dueDate.toEpochDay() - today.toEpochDay()
        return when {
            days < 0L -> context.getString(R.string.notification_due_when_overdue)
            days == 0L -> context.getString(R.string.notification_due_when_today)
            days == 1L -> context.getString(R.string.notification_due_when_tomorrow)
            else -> context.resources.getQuantityString(
                R.plurals.notification_due_when_in_days,
                days.toInt(),
                days,
            )
        }
    }

    private fun createChannelIfNeeded(context: Context) {
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
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
