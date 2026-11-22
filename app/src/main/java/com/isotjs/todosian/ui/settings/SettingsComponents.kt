package com.isotjs.todosian.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.isotjs.todosian.R
import com.isotjs.todosian.data.settings.CategoryLayout
import com.isotjs.todosian.data.settings.CategorySort
import com.isotjs.todosian.data.settings.DailyFocusMode
import com.isotjs.todosian.data.settings.NewTodoFilePosition
import com.isotjs.todosian.data.settings.ThemeMode
import com.isotjs.todosian.data.settings.TodoGrouping
import com.isotjs.todosian.data.settings.TodoSort
import kotlinx.coroutines.launch

/** Accent used by a settings section badge. Alternating accents keep the page scannable. */
internal enum class SettingsAccent {
    PRIMARY,
    SECONDARY,
    TERTIARY,
}

internal data class ChoiceOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
)

internal object SettingsDefaults {
    val LeadingInset: Dp = 56.dp
    val IconSize: Dp = 24.dp
    val BadgeSize: Dp = 40.dp
    val BadgeIconSize: Dp = 22.dp
}

@Composable
internal fun settingsContainerColor(accent: SettingsAccent): Color = when (accent) {
    SettingsAccent.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    SettingsAccent.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
    SettingsAccent.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
internal fun settingsOnContainerColor(accent: SettingsAccent): Color = when (accent) {
    SettingsAccent.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    SettingsAccent.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    SettingsAccent.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
}

/**
 * A settings section: an expressive shape badge header followed by a tonal card.
 * Content rows are expected to be [SettingsSwitchRow], [SettingsNavRow] or [SettingsInfoRow].
 */
@Composable
internal fun SettingsSection(
    title: String,
    icon: ImageVector,
    accent: SettingsAccent,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader(title = title, icon = icon, accent = accent)
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSectionHeader(
    title: String,
    icon: ImageVector,
    accent: SettingsAccent,
    modifier: Modifier = Modifier,
) {
    val container = settingsContainerColor(accent)
    val onContainer = settingsOnContainerColor(accent)
    val badgeShape = when (accent) {
        SettingsAccent.PRIMARY -> MaterialShapes.Clover4Leaf
        SettingsAccent.SECONDARY -> MaterialShapes.Cookie6Sided
        SettingsAccent.TERTIARY -> MaterialShapes.Cookie9Sided
    }.toShape()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(SettingsDefaults.BadgeSize)
                .clip(badgeShape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(SettingsDefaults.BadgeIconSize),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
    }
}

/** Large tinted card used for shortcuts to dedicated settings screens. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsShortcutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: SettingsAccent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = settingsContainerColor(accent)
    val content = settingsOnContainerColor(accent)
    val badgeShape = when (accent) {
        SettingsAccent.PRIMARY -> MaterialShapes.Cookie4Sided
        SettingsAccent.SECONDARY -> MaterialShapes.Clover4Leaf
        SettingsAccent.TERTIARY -> MaterialShapes.Cookie6Sided
    }.toShape()

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = content,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(SettingsDefaults.BadgeSize)
                    .clip(badgeShape)
                    .background(content.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(SettingsDefaults.BadgeIconSize),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        supportingContent = if (subtitle != null) {
            {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
        colors = transparentListItemColors(),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        supportingContent = if (subtitle != null) {
            {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = transparentListItemColors(),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
) {
    ListItem(
        supportingContent = if (subtitle != null) {
            {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        colors = transparentListItemColors(),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun SettingsDivider(startInset: Dp = SettingsDefaults.LeadingInset) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startInset, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** Inline selector for short option lists (theme, category layout). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> SettingsSegmentedRow(
    icon: ImageVector,
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(SettingsDefaults.IconSize),
            )
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val isSelected = option.value == selected
                SegmentedButton(
                    selected = isSelected,
                    onClick = { onSelect(option.value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.weight(1f),
                    icon = if (option.icon != null) {
                        {
                            SegmentedButtonDefaults.Icon(
                                active = isSelected,
                                activeContent = {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                    )
                                },
                                inactiveContent = {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                    )
                                },
                            )
                        }
                    } else {
                        {}
                    },
                ) {
                    Text(
                        text = option.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Bottom sheet replacement for the old single-choice dialogs. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> SettingsChoiceSheet(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            options.forEach { option ->
                val isSelected = option.value == selected
                ListItem(
                    leadingContent = if (option.icon != null) {
                        {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    trailingContent = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    colors = transparentListItemColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                        ) {
                            onSelect(option.value)
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) onDismiss()
                            }
                        },
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
internal fun transparentListItemColors(): ListItemColors =
    ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
internal fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

@Composable
internal fun categoryLayoutLabel(layout: CategoryLayout): String = when (layout) {
    CategoryLayout.GRID -> stringResource(R.string.settings_category_layout_grid)
    CategoryLayout.LIST -> stringResource(R.string.settings_category_layout_list)
}

@Composable
internal fun categorySortLabel(sort: CategorySort): String = when (sort) {
    CategorySort.A_Z -> stringResource(R.string.settings_sort_az)
    CategorySort.MOST_REMAINING -> stringResource(R.string.settings_sort_remaining)
}

@Composable
internal fun todoGroupingLabel(grouping: TodoGrouping): String = when (grouping) {
    TodoGrouping.GROUPED -> stringResource(R.string.settings_grouping_grouped)
    TodoGrouping.FILE_ORDER -> stringResource(R.string.settings_grouping_file_order)
}

@Composable
internal fun todoSortLabel(sort: TodoSort): String = when (sort) {
    TodoSort.FILE_ORDER -> stringResource(R.string.settings_todo_sort_file_order)
    TodoSort.PRIORITY_HIGH_TO_LOW -> stringResource(R.string.settings_todo_sort_priority_desc)
    TodoSort.CREATED_DATE_NEWEST_FIRST -> stringResource(R.string.settings_todo_sort_created_newest)
    TodoSort.DUE_DATE_EARLIEST_FIRST -> stringResource(R.string.settings_todo_sort_due_earliest)
}

@Composable
internal fun newTodoFilePositionLabel(position: NewTodoFilePosition): String = when (position) {
    NewTodoFilePosition.TOP -> stringResource(R.string.settings_new_todo_file_position_top)
    NewTodoFilePosition.BOTTOM -> stringResource(R.string.settings_new_todo_file_position_bottom)
}

@Composable
internal fun dailyFocusModeLabel(mode: DailyFocusMode): String = when (mode) {
    DailyFocusMode.TODAY -> stringResource(R.string.settings_daily_focus_mode_today)
    DailyFocusMode.OVERDUE -> stringResource(R.string.settings_daily_focus_mode_overdue)
    DailyFocusMode.TODAY_AND_OVERDUE -> stringResource(R.string.settings_daily_focus_mode_today_overdue)
}

@Composable
internal fun storageStatusText(state: StorageUiState): String = when {
    state.folderUri == null -> stringResource(R.string.settings_status_not_set)
    state.isChecking -> stringResource(R.string.settings_status_checking)
    !state.hasPersistedPermission -> stringResource(R.string.settings_status_permission_lost)
    state.markdownFileCount == null -> stringResource(R.string.settings_status_unknown)
    state.markdownFileCount == 0 -> stringResource(R.string.settings_status_empty)
    else -> pluralStringResource(
        R.plurals.settings_status_ok,
        state.markdownFileCount,
        state.markdownFileCount,
    )
}
