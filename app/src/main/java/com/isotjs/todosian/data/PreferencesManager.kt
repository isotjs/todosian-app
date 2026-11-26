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
        if (getFolderUri() != uri) {
            clearDefaultAddTaskFileUri()
            clearDefaultLaunchCategoryUri()
            clearQuickAddCategoryUri()
        }
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
        prefs.edit {
            remove(KEY_FOLDER_URI)
            remove(KEY_DEFAULT_ADD_TASK_FILE_URI)
            remove(KEY_DEFAULT_LAUNCH_CATEGORY_URI)
            remove(KEY_QUICK_ADD_CATEGORY_URI)
        }
    }

    fun getQuickAddCategoryUri(): Uri? =
        prefs.getString(KEY_QUICK_ADD_CATEGORY_URI, null)?.let { raw -> runCatching { raw.toUri() }.getOrNull() }

    fun saveQuickAddCategoryUri(uri: Uri) {
        prefs.edit { putString(KEY_QUICK_ADD_CATEGORY_URI, uri.toString()) }
    }

    fun clearQuickAddCategoryUri() {
        prefs.edit { remove(KEY_QUICK_ADD_CATEGORY_URI) }
    }

    /** The note that the "Add task" widget opens directly, instead of asking for a file. */
    fun getDefaultAddTaskFileUri(): Uri? =
        prefs.getString(KEY_DEFAULT_ADD_TASK_FILE_URI, null)?.let { raw -> runCatching { raw.toUri() }.getOrNull() }

    fun saveDefaultAddTaskFileUri(uri: Uri) {
        prefs.edit { putString(KEY_DEFAULT_ADD_TASK_FILE_URI, uri.toString()) }
    }

    fun clearDefaultAddTaskFileUri() {
        prefs.edit { remove(KEY_DEFAULT_ADD_TASK_FILE_URI) }
    }

    fun getDefaultLaunchCategoryUri(): Uri? =
        prefs.getString(KEY_DEFAULT_LAUNCH_CATEGORY_URI, null)?.let { raw -> runCatching { raw.toUri() }.getOrNull() }

    fun saveDefaultLaunchCategoryUri(uri: Uri) {
        prefs.edit { putString(KEY_DEFAULT_LAUNCH_CATEGORY_URI, uri.toString()) }
    }

    fun clearDefaultLaunchCategoryUri() {
        prefs.edit { remove(KEY_DEFAULT_LAUNCH_CATEGORY_URI) }
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
        private const val KEY_DEFAULT_ADD_TASK_FILE_URI = "default_add_task_file_uri"
        private const val KEY_DEFAULT_LAUNCH_CATEGORY_URI = "default_launch_category_uri"
        private const val KEY_QUICK_ADD_CATEGORY_URI = "quick_add_category_uri"
    }
}
