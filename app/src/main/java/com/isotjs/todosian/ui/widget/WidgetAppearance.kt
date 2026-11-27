package com.isotjs.todosian.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.color.colorProviders
import androidx.glance.unit.ColorProvider

internal val KEY_WIDGET_BACKGROUND_THEME = stringPreferencesKey("widget_background_theme")
internal val KEY_WIDGET_BACKGROUND_OPACITY = intPreferencesKey("widget_background_opacity")

/** Background color mode for a widget instance. */
enum class WidgetBackgroundTheme {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Per-widget appearance: a background color mode and an opacity (0–100%). Stored in the widget's
 * Glance state alongside its content snapshot, so each instance can look different.
 */
internal data class WidgetAppearance(
    val theme: WidgetBackgroundTheme = WidgetBackgroundTheme.SYSTEM,
    val opacityPercent: Int = 100,
) {
    /** Opacity as an alpha value (1f = fully opaque). */
    val backgroundAlpha: Float
        get() = opacityPercent.coerceIn(0, 100) / 100f

    fun writeTo(prefs: MutablePreferences) {
        prefs[KEY_WIDGET_BACKGROUND_THEME] = theme.name
        prefs[KEY_WIDGET_BACKGROUND_OPACITY] = opacityPercent
    }

    companion object {
        fun from(prefs: Preferences): WidgetAppearance = WidgetAppearance(
            theme = prefs[KEY_WIDGET_BACKGROUND_THEME]
                ?.let { raw -> runCatching { WidgetBackgroundTheme.valueOf(raw) }.getOrNull() }
                ?: WidgetBackgroundTheme.SYSTEM,
            opacityPercent = prefs[KEY_WIDGET_BACKGROUND_OPACITY] ?: 100,
        )
    }
}

/**
 * Resolves the color providers for [theme]. SYSTEM follows the device theme (Glance's default);
 * LIGHT and DARK are forced so the widget keeps its chosen look regardless of the device setting.
 */
@Composable
internal fun widgetColorProviders(theme: WidgetBackgroundTheme): ColorProviders = when (theme) {
    WidgetBackgroundTheme.SYSTEM -> GlanceTheme.colors
    WidgetBackgroundTheme.LIGHT -> LightWidgetColors
    WidgetBackgroundTheme.DARK -> DarkWidgetColors
}

/** A color provider that returns the same color in both light and dark mode (forces a scheme). */
private fun fixed(color: Color): ColorProvider = ColorProvider(color)

// Material 3 baseline light scheme; the surface reads as white, matching the current widget.
private val LightWidgetColors: ColorProviders = colorProviders(
    primary = fixed(Color(0xFF6750A4)),
    onPrimary = fixed(Color(0xFFFFFFFF)),
    primaryContainer = fixed(Color(0xFFEADDFF)),
    onPrimaryContainer = fixed(Color(0xFF21005D)),
    secondary = fixed(Color(0xFF625B71)),
    onSecondary = fixed(Color(0xFFFFFFFF)),
    secondaryContainer = fixed(Color(0xFFE8DEF8)),
    onSecondaryContainer = fixed(Color(0xFF1D192B)),
    tertiary = fixed(Color(0xFF7D5260)),
    onTertiary = fixed(Color(0xFFFFFFFF)),
    tertiaryContainer = fixed(Color(0xFFFFD8E4)),
    onTertiaryContainer = fixed(Color(0xFF31111D)),
    error = fixed(Color(0xFFB3261E)),
    errorContainer = fixed(Color(0xFFF9DEDC)),
    onError = fixed(Color(0xFFFFFFFF)),
    onErrorContainer = fixed(Color(0xFF410E0B)),
    background = fixed(Color(0xFFFFFBFE)),
    onBackground = fixed(Color(0xFF1C1B1F)),
    surface = fixed(Color(0xFFFFFBFE)),
    onSurface = fixed(Color(0xFF1C1B1F)),
    surfaceVariant = fixed(Color(0xFFE7E0EC)),
    onSurfaceVariant = fixed(Color(0xFF49454F)),
    outline = fixed(Color(0xFF79747E)),
    inverseOnSurface = fixed(Color(0xFFF4EFF4)),
    inverseSurface = fixed(Color(0xFF313033)),
    inversePrimary = fixed(Color(0xFFD0BCFF)),
)

// Dark scheme with a black background (the issue asks for "Dark (Black)") and light foregrounds.
private val DarkWidgetColors: ColorProviders = colorProviders(
    primary = fixed(Color(0xFFD0BCFF)),
    onPrimary = fixed(Color(0xFF381E72)),
    primaryContainer = fixed(Color(0xFF4F378B)),
    onPrimaryContainer = fixed(Color(0xFFEADDFF)),
    secondary = fixed(Color(0xFFCCC2DC)),
    onSecondary = fixed(Color(0xFF332D41)),
    secondaryContainer = fixed(Color(0xFF4A4458)),
    onSecondaryContainer = fixed(Color(0xFFE8DEF8)),
    tertiary = fixed(Color(0xFFEFB8C8)),
    onTertiary = fixed(Color(0xFF492532)),
    tertiaryContainer = fixed(Color(0xFF633B48)),
    onTertiaryContainer = fixed(Color(0xFFFFD8E4)),
    error = fixed(Color(0xFFF2B8B5)),
    errorContainer = fixed(Color(0xFF8C1D18)),
    onError = fixed(Color(0xFF601410)),
    onErrorContainer = fixed(Color(0xFFF9DEDC)),
    background = fixed(Color(0xFF000000)),
    onBackground = fixed(Color(0xFFFFFFFF)),
    surface = fixed(Color(0xFF000000)),
    onSurface = fixed(Color(0xFFFFFFFF)),
    surfaceVariant = fixed(Color(0xFF1F1F1F)),
    onSurfaceVariant = fixed(Color(0xFFCAC4D0)),
    outline = fixed(Color(0xFF8F8F8F)),
    inverseOnSurface = fixed(Color(0xFF000000)),
    inverseSurface = fixed(Color(0xFFFFFFFF)),
    inversePrimary = fixed(Color(0xFF6750A4)),
)
