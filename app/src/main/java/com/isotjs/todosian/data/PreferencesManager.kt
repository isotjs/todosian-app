package com.isotjs.todosian.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

import com.isotjs.todosian.BuildConfig

class PreferencesManager(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFolderUri(): Uri? {
        val raw = prefs.getString(KEY_FOLDER_URI, null) ?: return null
        return runCatching { raw.toUri() }.getOrNull()
    }

    fun saveFolderUri(uri: Uri) {
        if (prefs.getInt(KEY_LAST_SEEN_VERSION_CODE, 0) == 0) {
            prefs.edit {
                putString(KEY_FOLDER_URI, uri.toString())
                putInt(KEY_LAST_SEEN_VERSION_CODE, BuildConfig.VERSION_CODE)
            }
        } else {
            prefs.edit {
                putString(KEY_FOLDER_URI, uri.toString())
            }
        }
    }

    fun clearFolderUri() {
        prefs.edit { remove(KEY_FOLDER_URI) }
    }

    fun getLastSeenVersionCode(): Int {
        return prefs.getInt(KEY_LAST_SEEN_VERSION_CODE, 0)
    }

    fun saveLastSeenVersionCode(versionCode: Int) {
        prefs.edit { putInt(KEY_LAST_SEEN_VERSION_CODE, versionCode) }
    }

    private companion object {
        private const val PREFS_NAME = "todosian_prefs"
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"
    }
}
