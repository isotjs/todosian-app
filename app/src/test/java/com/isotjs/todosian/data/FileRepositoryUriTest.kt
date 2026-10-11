package com.isotjs.todosian.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileRepositoryUriTest {

    private fun check(
        folderAuthority: String? = "com.android.externalstorage.documents",
        candidateScheme: String? = "content",
        candidateAuthority: String? = "com.android.externalstorage.documents",
        candidateDocumentId: String? = "primary:Notes/todo.md",
    ) = isDocumentWithinFolder(
        folderAuthority = folderAuthority,
        candidateScheme = candidateScheme,
        candidateAuthority = candidateAuthority,
        candidateDocumentId = candidateDocumentId,
    )

    @Test
    fun `document from the same provider is accepted`() {
        assertTrue(check())
    }

    @Test
    fun `document from a different provider is rejected`() {
        assertFalse(check(candidateAuthority = "com.example.attacker.documents"))
    }

    @Test
    fun `non-content scheme is rejected`() {
        assertFalse(check(candidateScheme = "file"))
    }

    @Test
    fun `missing or blank folder authority is rejected`() {
        assertFalse(check(folderAuthority = null))
        assertFalse(check(folderAuthority = ""))
    }

    @Test
    fun `missing candidate authority is rejected`() {
        assertFalse(check(candidateAuthority = null))
    }

    @Test
    fun `non-document uri without a document id is rejected`() {
        assertFalse(check(candidateDocumentId = null))
        assertFalse(check(candidateDocumentId = ""))
    }
}
