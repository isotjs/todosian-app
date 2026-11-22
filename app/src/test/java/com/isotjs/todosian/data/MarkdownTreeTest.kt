package com.isotjs.todosian.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTreeTest {

    private val tree = mapOf(
        "root" to listOf(
            DocumentEntry("inbox", "Inbox.md", isDirectory = false),
            DocumentEntry("notes", "notes.txt", isDirectory = false),
            DocumentEntry("conflict", "Inbox.sync-conflict-1.md", isDirectory = false),
            DocumentEntry("projects", "Projects", isDirectory = true),
            DocumentEntry("obsidian", ".obsidian", isDirectory = true),
        ),
        "projects" to listOf(
            DocumentEntry("move", "Move.MD", isDirectory = false),
            DocumentEntry("deep", "Deep", isDirectory = true),
        ),
        "deep" to listOf(DocumentEntry("x", "X.md", isDirectory = false)),
        "obsidian" to listOf(DocumentEntry("hidden", "Hidden.md", isDirectory = false)),
    )

    private fun walk(recursive: Boolean) = walkMarkdownTree("root", recursive) { tree[it].orEmpty() }

    @Test
    fun flatScanListsOnlyTopLevelNotes() {
        val result = walk(recursive = false)

        assertEquals(listOf(MarkdownEntry("inbox", "Inbox")), result.files)
        assertEquals(listOf("root"), result.folderIds)
    }

    @Test
    fun recursiveScanUsesRelativePathsAndSkipsHiddenFolders() {
        val result = walk(recursive = true)

        assertEquals(
            listOf(
                MarkdownEntry("inbox", "Inbox"),
                MarkdownEntry("move", "Projects/Move"),
                MarkdownEntry("x", "Projects/Deep/X"),
            ),
            result.files,
        )
        assertEquals(listOf("root", "projects", "deep"), result.folderIds)
    }
}
