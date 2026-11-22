package com.isotjs.todosian.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsedTodoCodecTest {

    @Test
    fun encode_decode_round_trips_ids_with_control_characters() {
        val ids = setOf(
            "1\u001FBuy milk\u001F\u001F\u001F\u001F\u001F",
            "2\u001FRead book\u001F2026-11-01",
            "plain-id",
        )

        assertEquals(ids, CollapsedTodoCodec.decode(CollapsedTodoCodec.encode(ids)))
    }

    @Test
    fun encode_empty_set_produces_empty_string() {
        assertEquals("", CollapsedTodoCodec.encode(emptySet()))
        assertEquals(emptySet<String>(), CollapsedTodoCodec.decode(""))
    }

    @Test
    fun decode_ignores_empty_and_invalid_entries() {
        val encoded = CollapsedTodoCodec.encode(setOf("valid"))

        assertEquals(setOf("valid"), CollapsedTodoCodec.decode("$encoded\n\nnot base64!"))
    }

    @Test
    fun encoded_output_is_xml_safe() {
        val encoded = CollapsedTodoCodec.encode(setOf("id\u001Fwith\u0000controls"))

        assertNotEquals("id\u001Fwith\u0000controls", encoded)
        assertTrue(
            encoded.all { char ->
                char == '\n' || char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
                    char == '-' || char == '_'
            },
        )
    }
}
