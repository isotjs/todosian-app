package com.isotjs.todosian.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.isotjs.todosian.data.PreferencesManager
import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.data.settings.SharedPrefsAppSettingsRepository
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class DueReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = SharedPrefsAppSettingsRepository(applicationContext).settings.first()
        if (!settings.enableTasksPluginSupport) {
            DueReminderNotifier.cancel(applicationContext)
            return Result.success()
        }

        if (!canPostNotifications(applicationContext)) {
            return Result.success()
        }

        val folderUri = PreferencesManager(applicationContext).getFolderUri()
            ?: return Result.success()

        val dueSoon = runCatching { loadDueSoonTodos(folderUri) }
            .getOrElse { return Result.retry() }

        if (dueSoon.isEmpty()) {
            DueReminderNotifier.cancel(applicationContext)
            clearSignatureState()
            return Result.success()
        }

        val signature = dueSoon.joinToString(separator = "|") {
            "${it.fileName}#${it.lineIndex}#${it.dueDate}#${it.text}"
        }
        if (alreadySentToday(signature)) {
            return Result.success()
        }

        val first = dueSoon.first()
        DueReminderNotifier.show(
            context = applicationContext,
            payload = DueReminderNotifier.Payload(
                totalCount = dueSoon.size,
                nextDueDate = first.dueDate,
                topTodoText = first.text,
            ),
        )
        saveSentToday(signature)
        return Result.success()
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return true

        val state = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        return state == PackageManager.PERMISSION_GRANTED
    }

    private fun loadDueSoonTodos(folderUri: Uri): List<DueTodo> {
        val folder = DocumentFile.fromTreeUri(applicationContext, folderUri) ?: return emptyList()
        val today = LocalDate.now()

        return folder.listFiles()
            .asSequence()
            .filter { it.isFile }
            .filter {
                val name = it.name.orEmpty()
                name.endsWith(".md", ignoreCase = true) && !name.contains("sync-conflict", ignoreCase = true)
            }
            .flatMap { file ->
                parseDueTodosFromFile(file, today).asSequence()
            }
            .sortedWith(compareBy<DueTodo> { it.dueDate }.thenBy { it.text.lowercase() })
            .toList()
    }

    private fun parseDueTodosFromFile(file: DocumentFile, today: LocalDate): List<DueTodo> {
        val lines = applicationContext.contentResolver.openInputStream(file.uri)?.use { input ->
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

    private fun alreadySentToday(signature: String): Boolean {
        val prefs = statePrefs()
        val today = LocalDate.now().toString()
        val lastDay = prefs.getString(KEY_LAST_DAY, null)
        val lastSignature = prefs.getString(KEY_LAST_SIGNATURE, null)
        return lastDay == today && lastSignature == signature
    }

    private fun saveSentToday(signature: String) {
        statePrefs().edit()
            .putString(KEY_LAST_DAY, LocalDate.now().toString())
            .putString(KEY_LAST_SIGNATURE, signature)
            .apply()
    }

    private fun clearSignatureState() {
        statePrefs().edit()
            .remove(KEY_LAST_DAY)
            .remove(KEY_LAST_SIGNATURE)
            .apply()
    }

    private fun statePrefs() =
        applicationContext.getSharedPreferences(STATE_PREFS_NAME, Context.MODE_PRIVATE)

    private data class DueTodo(
        val fileName: String,
        val lineIndex: Int,
        val text: String,
        val dueDate: LocalDate,
    )

    private companion object {
        private const val STATE_PREFS_NAME = "todosian_due_reminder_state"
        private const val KEY_LAST_DAY = "last_day"
        private const val KEY_LAST_SIGNATURE = "last_signature"
    }
}
