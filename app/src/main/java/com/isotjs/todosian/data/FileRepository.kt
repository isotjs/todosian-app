package com.isotjs.todosian.data

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.isotjs.todosian.data.model.Category
import com.isotjs.todosian.utils.MarkdownParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

interface FileRepository {
    fun getFolderUri(): Uri?

    fun clearFolderUri()

    suspend fun persistFolderUri(uri: Uri): Result<Unit>

    suspend fun getCategories(): Result<List<Category>>

    suspend fun readLines(uri: Uri): Result<List<String>>

    suspend fun getDisplayName(uri: Uri): Result<String>

    suspend fun getFolderDisplayName(folderUri: Uri): Result<String>

    suspend fun writeLines(uri: Uri, lines: List<String>): Result<Unit>

    suspend fun createCategory(folderUri: Uri, name: String): Result<Uri>

    suspend fun renameCategory(categoryUri: Uri, newName: String): Result<Unit>

    suspend fun deleteCategory(categoryUri: Uri): Result<Unit>

    fun hasPersistedReadWritePermission(uri: Uri): Boolean

    suspend fun countMarkdownFiles(folderUri: Uri): Result<Int>

    fun observeTreeChanges(treeUri: Uri): Flow<Unit>

    fun observeDocumentChanges(documentUri: Uri): Flow<Unit>

    fun observeMarkdownFilesChanges(folderUri: Uri): Flow<Unit>
}

class SafFileRepository(
    private val appContext: Context,
    private val preferencesManager: PreferencesManager,
) : FileRepository {

    override fun getFolderUri(): Uri? = preferencesManager.getFolderUri()

    override fun clearFolderUri() {
        preferencesManager.clearFolderUri()
    }

    override suspend fun persistFolderUri(uri: Uri): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                appContext.contentResolver.takePersistableUriPermission(uri, flags)
                preferencesManager.saveFolderUri(uri)
            }
        }
    }

    override suspend fun getCategories(): Result<List<Category>> {
        val folderUri = getFolderUri() ?: return Result.success(emptyList())
        return withContext(Dispatchers.IO) {
            runCatching {
                val folder = DocumentFile.fromTreeUri(appContext, folderUri)
                    ?: throw IllegalStateException("Invalid folder URI")

                folder.listFiles()
                    .asSequence()
                    .filter { it.isFile }
                    .filter { file ->
                        val name = file.name.orEmpty()
                        name.endsWith(".md", ignoreCase = true) && !name.contains("sync-conflict", ignoreCase = true)
                    }
                    .map { file ->
                        val name = file.name ?: ""
                        val displayName = name.removeSuffix(".md")
                        val lines = readLinesInternal(file.uri)
                        val todos = MarkdownParser.parse(lines)
                        val doneCount = todos.count { it.isDone }
                        Category(
                            fileName = name,
                            displayName = displayName,
                            uri = file.uri,
                            todoCount = todos.size,
                            doneCount = doneCount,
                        )
                    }
                    .sortedBy { it.displayName.lowercase() }
                    .toList()
            }
        }
    }

    override suspend fun readLines(uri: Uri): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            runCatching { readLinesInternal(uri) }
        }
    }

    override suspend fun getDisplayName(uri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = DocumentFile.fromSingleUri(appContext, uri)
                    ?: throw IllegalStateException("Invalid file URI")
                file.name ?: ""
            }
        }
    }

    override suspend fun getFolderDisplayName(folderUri: Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val folder = DocumentFile.fromTreeUri(appContext, folderUri)
                    ?: throw IllegalStateException("Invalid folder URI")
                folder.name
                    ?: folderUri.lastPathSegment
                    ?: folderUri.toString()
            }
        }
    }

    override suspend fun writeLines(uri: Uri, lines: List<String>): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.openOutputStream(uri, "rwt")?.use { out ->
                    out.bufferedWriter().use { writer ->
                        lines.forEachIndexed { index, line ->
                            if (index > 0) writer.newLine()
                            writer.write(line)
                        }
                    }
                } ?: throw IllegalStateException("Unable to open output stream")
            }
        }
    }

    override suspend fun createCategory(folderUri: Uri, name: String): Result<Uri> {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return Result.failure(IllegalArgumentException("Empty name"))

        return withContext(Dispatchers.IO) {
            runCatching {
                val folder = DocumentFile.fromTreeUri(appContext, folderUri)
                    ?: throw IllegalStateException("Invalid folder URI")

                val fileName = if (cleaned.endsWith(".md", ignoreCase = true)) cleaned else "$cleaned.md"
                val created = folder.createFile("text/markdown", fileName)
                    ?: throw IllegalStateException("Unable to create file")

                created.uri
            }
        }
    }

    override suspend fun renameCategory(categoryUri: Uri, newName: String): Result<Unit> {
        val cleaned = newName.trim()
        if (cleaned.isEmpty()) return Result.failure(IllegalArgumentException("Empty name"))

        return withContext(Dispatchers.IO) {
            runCatching {
                val fileName = cleaned
                    .removeSuffix(".md")
                    .removeSuffix(".MD")
                    .removeSuffix(".Md")
                    .removeSuffix(".mD")
                    .trim()
                    .let { base -> if (base.endsWith(".md", ignoreCase = true)) base else "$base.md" }

                val renamed = DocumentsContract.renameDocument(appContext.contentResolver, categoryUri, fileName)
                if (renamed == null) throw IllegalStateException("Unable to rename document")
            }
        }
    }

    override suspend fun deleteCategory(categoryUri: Uri): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = DocumentFile.fromSingleUri(appContext, categoryUri)
                    ?: throw IllegalStateException("Invalid file URI")
                val ok = file.delete()
                if (!ok) throw IllegalStateException("Unable to delete file")
            }
        }
    }

    override fun hasPersistedReadWritePermission(uri: Uri): Boolean {
        val perms = appContext.contentResolver.persistedUriPermissions
        val perm = perms.firstOrNull { it.uri == uri } ?: return false
        return perm.isReadPermission && perm.isWritePermission
    }

    override suspend fun countMarkdownFiles(folderUri: Uri): Result<Int> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val folder = DocumentFile.fromTreeUri(appContext, folderUri)
                    ?: throw IllegalStateException("Invalid folder URI")

                folder.listFiles().count { file ->
                    if (!file.isFile) return@count false
                    val name = file.name.orEmpty()
                    name.endsWith(".md", ignoreCase = true) && !name.contains("sync-conflict", ignoreCase = true)
                }
            }
        }
    }

    override fun observeTreeChanges(treeUri: Uri): Flow<Unit> {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        return observeUrisChanges(
            uris = listOf(treeUri, childrenUri),
            notifyDescendants = true,
        )
    }

    override fun observeDocumentChanges(documentUri: Uri): Flow<Unit> {
        return observeUrisChanges(
            uris = listOf(documentUri),
            notifyDescendants = false,
        )
    }

    override fun observeMarkdownFilesChanges(folderUri: Uri): Flow<Unit> {
        val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeDocId)

        return callbackFlow {
            val resolver = appContext.contentResolver

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            var updateJob = scope.launch { }
            updateJob.cancel()

            var observedUris: List<Pair<Uri, Boolean>> = emptyList()

            lateinit var observer: ContentObserver

            fun currentMarkdownUris(): List<Pair<Uri, Boolean>> {
                val folder = DocumentFile.fromTreeUri(appContext, folderUri) ?: return emptyList()
                val fileUris = folder.listFiles()
                    .asSequence()
                    .filter { it.isFile }
                    .filter {
                        val name = it.name.orEmpty()
                        name.endsWith(".md", ignoreCase = true) && !name.contains("sync-conflict", ignoreCase = true)
                    }
                    .map { it.uri }
                    .toList()

                val uris = mutableListOf<Pair<Uri, Boolean>>()
                // structural changes
                uris.add(folderUri to true)
                uris.add(childrenUri to true)
                // content changes
                fileUris.forEach { uris.add(it to false) }
                return uris
            }

            fun scheduleUpdate() {
                updateJob.cancel()
                updateJob = scope.launch {
                    delay(200)
                    val next = runCatching { currentMarkdownUris() }.getOrElse { emptyList() }
                    if (next == observedUris) return@launch

                    runCatching { resolver.unregisterContentObserver(observer) }
                    observedUris = next
                    runCatching {
                        observedUris.forEach { (uri, notifyDescendants) ->
                            resolver.registerContentObserver(uri, notifyDescendants, observer)
                        }
                    }.onFailure { t ->
                        close(t)
                    }
                }
            }

            observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, changedUri: Uri?) {
                    trySend(Unit)
                    val changed = changedUri?.toString().orEmpty()
                    val shouldUpdate = changedUri == null ||
                        changedUri == folderUri ||
                        changedUri == childrenUri ||
                        (changed.isNotEmpty() && changed.startsWith(childrenUri.toString()))
                    if (shouldUpdate) scheduleUpdate()
                }
            }

            // initial register
            observedUris = listOf(
                folderUri to true,
                childrenUri to true,
            )
            runCatching {
                observedUris.forEach { (uri, notifyDescendants) ->
                    resolver.registerContentObserver(uri, notifyDescendants, observer)
                }
            }.onFailure { t ->
                close(t)
                return@callbackFlow
            }

            scheduleUpdate()

            awaitClose {
                updateJob.cancel()
                scope.cancel()
                runCatching { resolver.unregisterContentObserver(observer) }
            }
        }.conflate()
    }

    private fun observeUrisChanges(
        uris: List<Uri>,
        notifyDescendants: Boolean,
    ): Flow<Unit> {
        return callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }

                override fun onChange(selfChange: Boolean, changedUri: Uri?) {
                    trySend(Unit)
                }
            }

            val resolver = appContext.contentResolver
            runCatching {
                uris.forEach { uri ->
                    resolver.registerContentObserver(uri, notifyDescendants, observer)
                }
            }.onFailure { t ->
                close(t)
                return@callbackFlow
            }

            awaitClose {
                runCatching { resolver.unregisterContentObserver(observer) }
            }
        }.conflate()
    }

    private fun readLinesInternal(uri: Uri): List<String> {
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            return input.bufferedReader().readLines()
        }
        throw IllegalStateException("Unable to open input stream")
    }
}
