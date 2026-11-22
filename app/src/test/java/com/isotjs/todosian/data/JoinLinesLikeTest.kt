package com.isotjs.todosian.data

import org.junit.Assert.assertEquals
import org.junit.Test

class JoinLinesLikeTest {

    private fun roundTrip(original: String): String =
        joinLinesLike(original, original.reader().readLines())

    @Test
    fun unchangedLinesRoundTripByteForByte() {
        listOf(
            "",
            "\n",
            "a",
            "a\n",
            "a\n\n",
            "- [ ] one\n- [x] two\n",
            "- [ ] one\r\n- [x] two\r\n",
            "- [ ] one\r\n- [x] two",
            "# Title 🎉\r\n\r\n- [ ] Ünïcode 📅 2026-01-01\r\n",
        ).forEach { original ->
            assertEquals(original, roundTrip(original))
        }
    }

    @Test
    fun editedLinesKeepCrlfAndFinalNewline() {
        val original = "# Todo\r\n- [ ] a\r\n"
        assertEquals(
            "# Todo\r\n- [x] a\r\n- [ ] b\r\n",
            joinLinesLike(original, listOf("# Todo", "- [x] a", "- [ ] b")),
        )
    }

    @Test
    fun newEmptyFileGetsLfWithoutFinalNewline() {
        assertEquals("- [ ] a", joinLinesLike("", listOf("- [ ] a")))
    }
}
