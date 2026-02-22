package com.isotjs.todosian.data

import android.content.Context
import android.net.Uri

class PreferencesManager(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFolderUri(): Uri? {
        val raw = prefs.getString(KEY_FOLDER_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun saveFolderUri(uri: Uri) {
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
    }

    fun clearFolderUri() {
        prefs.edit().remove(KEY_FOLDER_URI).apply()
    }

    private companion object {
        private const val PREFS_NAME = "todosian_prefs"
        private const val KEY_FOLDER_URI = "folder_uri"
    }
}
