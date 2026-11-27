package com.isotjs.todosian.ui.widget

import androidx.datastore.preferences.core.emptyPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAppearanceTest {

    @Test
    fun `round trip preserves theme and opacity`() {
        val appearance = WidgetAppearance(WidgetBackgroundTheme.DARK, 60)
        val prefs = emptyPreferences().toMutablePreferences()

        appearance.writeTo(prefs)

        assertEquals(appearance, WidgetAppearance.from(prefs))
    }

    @Test
    fun `missing keys default to system and full opacity`() {
        assertEquals(
            WidgetAppearance(WidgetBackgroundTheme.SYSTEM, 100),
            WidgetAppearance.from(emptyPreferences()),
        )
    }

    @Test
    fun `invalid theme value falls back to system`() {
        val prefs = emptyPreferences().toMutablePreferences()
        prefs[KEY_WIDGET_BACKGROUND_THEME] = "BOGUS"

        assertEquals(WidgetBackgroundTheme.SYSTEM, WidgetAppearance.from(prefs).theme)
    }

    @Test
    fun `opacity clamps to the 0 to 100 range`() {
        assertEquals(0f, WidgetAppearance(opacityPercent = -5).backgroundAlpha)
        assertEquals(1f, WidgetAppearance(opacityPercent = 100).backgroundAlpha)
        assertEquals(1f, WidgetAppearance(opacityPercent = 140).backgroundAlpha)
        assertEquals(0.6f, WidgetAppearance(opacityPercent = 60).backgroundAlpha, 0.001f)
    }

    @Test
    fun `writing a new theme overwrites the previous value`() {
        val prefs = emptyPreferences().toMutablePreferences()
        WidgetAppearance(WidgetBackgroundTheme.LIGHT, 40).writeTo(prefs)

        WidgetAppearance(WidgetBackgroundTheme.DARK, 40).writeTo(prefs)

        assertEquals(WidgetBackgroundTheme.DARK, WidgetAppearance.from(prefs).theme)
    }
}
