package com.isotjs.todosian.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import java.util.Base64

/**
 * Persists the set of collapsed todo ids per category so the collapsed state survives app
 * restarts. Todo ids may contain control characters, so they are Base64 encoded before being
 * written to SharedPreferences.
 */
interface CollapsedTodoRepository {
    fun getCollapsedTodoIds(categoryUri: Uri): Set<String>

    fun setCollapsedTodoIds(categoryUri: Uri, ids: Set<String>)
}

class SharedPrefsCollapsedTodoRepository(
    context: Context,
) : CollapsedTodoRepository {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getCollapsedTodoIds(categoryUri: Uri): Set<String> {
        val raw = prefs.getString(keyFor(categoryUri), null) ?: return emptySet()
        return CollapsedTodoCodec.decode(raw)
    }

    override fun setCollapsedTodoIds(categoryUri: Uri, ids: Set<String>) {
        val key = keyFor(categoryUri)
        if (ids.isEmpty()) {
            prefs.edit { remove(key) }
            return
        }

        prefs.edit { putString(key, CollapsedTodoCodec.encode(ids)) }
    }

    private fun keyFor(categoryUri: Uri): String = "$KEY_PREFIX${categoryUri}"

    private companion object {
        private const val PREFS_NAME = "todosian_collapsed_todos"
        private const val KEY_PREFIX = "collapsed:"
    }
}

internal object CollapsedTodoCodec {
    fun encode(ids: Set<String>): String =
        ids.joinToString(separator = "\n") { id ->
            Base64.getUrlEncoder().withoutPadding().encodeToString(id.toByteArray(Charsets.UTF_8))
        }

    fun decode(raw: String): Set<String> = raw.lineSequence()
        .filter { it.isNotEmpty() }
        .mapNotNull(::decodeId)
        .toSet()

    private fun decodeId(encoded: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
    }.getOrNull()
}
