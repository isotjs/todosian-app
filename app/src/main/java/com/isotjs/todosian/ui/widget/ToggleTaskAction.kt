package com.isotjs.todosian.ui.widget

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.isotjs.todosian.TodosianApplication
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Keep
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val categoryUriString = parameters[PARAM_CATEGORY_URI] ?: return
        val lineIndex = parameters[PARAM_LINE_INDEX] ?: return
        val expectedText = parameters[PARAM_TODO_TEXT] ?: return

        Log.d(TAG, "Action received: uri=$categoryUriString line=$lineIndex expectedText='$expectedText'")

        val app = context.applicationContext as? TodosianApplication
        val fileRepository = app?.fileRepository
        val settingsRepository = app?.appSettingsRepository
        if (fileRepository == null || settingsRepository == null) {
            Log.w(TAG, "Unable to access application repositories")
            return
        }

        val settings = settingsRepository.settings.first()
        Log.d(TAG, "enableTasksPluginSupport=${settings.enableTasksPluginSupport}")

        withContext(Dispatchers.IO) {
            val categoryUri = categoryUriString.toUri()
            
            val updateResult = fileRepository.updateLines(categoryUri) { lines ->
                var targetIndex = lineIndex
                val candidate = lines.getOrNull(targetIndex)
                val candidateText = candidate?.let {
                    MarkdownParser.parse(listOf(it)).firstOrNull()?.text
                }

                if (candidateText != expectedText) {
                    val foundIndex = lines.indexOfFirst { line ->
                        MarkdownParser.parse(listOf(line)).firstOrNull()?.text == expectedText
                    }
                    if (foundIndex != -1) {
                        targetIndex = foundIndex
                    } else {
                        Log.w(
                            TAG,
                            "Todo '$expectedText' not found in $categoryUri, aborting toggle.",
                        )
                        return@updateLines null
                    }
                }

                val currentTodo = MarkdownParser.parse(listOf(lines[targetIndex])).firstOrNull()
                val isCheckedKey = ActionParameters.Key<Boolean>("androidx.glance.action.ActionParameters.ToggleableStateKey")
                val intendedState = parameters[isCheckedKey]
                if (intendedState != null && currentTodo != null && currentTodo.isDone == intendedState) {
                    Log.d(TAG, "Line is already in intended state ($intendedState). Ignoring toggle.")
                    return@updateLines null
                }

                val updatedLines = MarkdownParser.tryToggleLine(
                    lines = lines,
                    lineIndex = targetIndex,
                    enableTasksPlugin = settings.enableTasksPluginSupport,
                )
                if (updatedLines == null) {
                    Log.w(TAG, "Unable to toggle line $targetIndex in $categoryUri")
                }
                updatedLines
            }

            if (updateResult.isFailure) {
                Log.w(TAG, "Failed to write toggled line to $categoryUri", updateResult.exceptionOrNull())
                return@withContext
            }

            Log.d(TAG, "Toggled line successfully. Triggering widget update immediately.")
            WidgetUpdater.updateAllWidgets(context)
        }
    }

    companion object {
        private const val TAG = "ToggleTaskAction"
        val PARAM_CATEGORY_URI = ActionParameters.Key<String>("category_uri")
        val PARAM_LINE_INDEX = ActionParameters.Key<Int>("line_index")
        val PARAM_TODO_TEXT = ActionParameters.Key<String>("todo_text")
    }
}
