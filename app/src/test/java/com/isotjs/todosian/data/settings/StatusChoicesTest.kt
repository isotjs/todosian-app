package com.isotjs.todosian.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusChoicesTest {

    @Test
    fun alwaysStartsWithOpenAndDone() {
        assertEquals(listOf(' ', 'x'), AppSettings().statusChoices())
    }

    @Test
    fun addsEachCustomCharacterOnce() {
        assertEquals(
            listOf(' ', 'x', '/', 'w', 's', '-'),
            AppSettings(customStatuses = "/ w s - x ] w").statusChoices(),
        )
    }
}
