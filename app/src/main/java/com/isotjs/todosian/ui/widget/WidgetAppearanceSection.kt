package com.isotjs.todosian.ui.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.isotjs.todosian.R
import kotlin.math.roundToInt

/**
 * Shared appearance editor for the widget configuration screens: a background theme selector and
 * an opacity slider. Text/icon contrast follows the selected theme, so it stays readable.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WidgetAppearanceSection(
    theme: WidgetBackgroundTheme,
    opacityPercent: Int,
    onThemeChange: (WidgetBackgroundTheme) -> Unit,
    onOpacityChange: (Int) -> Unit,
    onOpacityChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeOptions = listOf(
        WidgetBackgroundTheme.SYSTEM to stringResource(R.string.settings_theme_system),
        WidgetBackgroundTheme.LIGHT to stringResource(R.string.settings_theme_light),
        WidgetBackgroundTheme.DARK to stringResource(R.string.settings_theme_dark),
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.widget_configure_background_theme),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            themeOptions.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == theme,
                    onClick = { onThemeChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.widget_configure_background_opacity),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = opacityPercent.toFloat(),
                onValueChange = { onOpacityChange(it.roundToInt()) },
                onValueChangeFinished = onOpacityChangeFinished,
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.widget_configure_opacity_value, opacityPercent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp),
            )
        }
    }
}
