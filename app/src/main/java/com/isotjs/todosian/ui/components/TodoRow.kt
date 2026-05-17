package com.isotjs.todosian.ui.components

import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.isotjs.todosian.R
import com.isotjs.todosian.data.model.TasksPriority
import com.isotjs.todosian.data.model.Todo
import com.isotjs.todosian.utils.MarkdownParser
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoRow(
    todo: Todo,
    enableTasksPluginSupport: Boolean,
    useEmojisInUi: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onAddSubtask: () -> Unit,
    onRequestDelete: () -> Unit,
    onRequestMove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    allowMove: Boolean = onRequestMove != null,
    showSubtaskButton: Boolean = true,
) {
    val indentPadding = (todo.indentLevel * 12).coerceAtMost(48).dp
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onRequestDelete()
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (todo.indentLevel == 0) {
                        onRequestMove?.invoke()
                    }
                    false
                }
                else -> false
            }
        },
    )

    SwipeToDismissBox(
        modifier = modifier.padding(start = indentPadding),
        state = dismissState,
        enableDismissFromStartToEnd = allowMove && todo.indentLevel == 0,
        backgroundContent = {
            val backgroundColor = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
            val icon = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> Icons.Filled.Delete
                SwipeToDismissBoxValue.StartToEnd -> Icons.AutoMirrored.Outlined.ArrowForward
                else -> Icons.Filled.Delete
            }
            val contentColor = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onErrorContainer
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val alignment = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = backgroundColor,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = alignment,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                        stringResource(R.string.category_move_title)
                    } else {
                        stringResource(R.string.cd_delete)
                    },
                    tint = contentColor,
                )
            }
        },
        content = {
            val rowShape = RoundedCornerShape(16.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = rowShape,
                    )
                    .clip(rowShape)
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Checkbox(
                    checked = todo.isDone,
                    onCheckedChange = { onToggle() },
                )
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    val targetColor = if (todo.isDone) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    val textColor by animateColorAsState(
                        targetValue = targetColor,
                        animationSpec = tween(300),
                        label = "todo-text-color",
                    )
                    val priorityColor = priorityColorFor(todo.priority)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (priorityColor != null) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color = priorityColor, shape = CircleShape),
                            )
                        }
                        Text(
                            text = todo.text,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None,
                            color = textColor,
                        )
                    }

                    if (enableTasksPluginSupport) {
                        val chips = buildTasksMetaChips(todo = todo, useEmojisInUi = useEmojisInUi)
                        if (chips.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                chips.forEach { chip ->
                                    val chipColors = chip.color?.let { color ->
                                        AssistChipDefaults.assistChipColors(
                                            containerColor = color,
                                            labelColor = MaterialTheme.colorScheme.onSurface,
                                            leadingIconContentColor = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    androidx.compose.material3.AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        colors = chipColors ?: AssistChipDefaults.assistChipColors(),
                                        leadingIcon = chip.icon?.let { icon ->
                                            {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = chip.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (showSubtaskButton && todo.indentLevel < 2) {
                    IconButton(onClick = onAddSubtask) {
                        Icon(
                            imageVector = Icons.Outlined.SubdirectoryArrowRight,
                            contentDescription = stringResource(R.string.cd_add_subtask),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun TasksMetaEditor(
    mode: TodoSheetMode?,
    meta: MarkdownParser.TasksMeta,
    onMetaChange: (MarkdownParser.TasksMeta) -> Unit,
    useEmojisInUi: Boolean,
    modifier: Modifier = Modifier,
) {
    val isAdd = mode is TodoSheetMode.Add

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.category_tasks_metadata_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        DateMetaRow(
            label = if (useEmojisInUi) {
                stringResource(R.string.category_tasks_created)
            } else {
                stringResource(R.string.category_tasks_created_label)
            },
            icon = if (useEmojisInUi) null else Icons.Outlined.AddCircle,
            value = meta.createdDate,
            allowClear = !isAdd,
            onPick = { picked -> onMetaChange(meta.copy(createdDate = picked)) },
            onClear = { onMetaChange(meta.copy(createdDate = null)) },
        )

        DateMetaRow(
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_due) else stringResource(R.string.category_tasks_due_label),
            icon = if (useEmojisInUi) null else Icons.Outlined.Event,
            value = meta.dueDate,
            allowClear = true,
            onPick = { picked -> onMetaChange(meta.copy(dueDate = picked)) },
            onClear = { onMetaChange(meta.copy(dueDate = null)) },
        )

        DateMetaRow(
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_start) else stringResource(R.string.category_tasks_start_label),
            icon = if (useEmojisInUi) null else Icons.Outlined.FlightTakeoff,
            value = meta.startDate,
            allowClear = true,
            onPick = { picked -> onMetaChange(meta.copy(startDate = picked)) },
            onClear = { onMetaChange(meta.copy(startDate = null)) },
        )

        DateMetaRow(
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_scheduled) else stringResource(R.string.category_tasks_scheduled_label),
            icon = if (useEmojisInUi) null else Icons.Outlined.Schedule,
            value = meta.scheduledDate,
            allowClear = true,
            onPick = { picked -> onMetaChange(meta.copy(scheduledDate = picked)) },
            onClear = { onMetaChange(meta.copy(scheduledDate = null)) },
        )

        DateMetaRow(
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_done) else stringResource(R.string.category_tasks_done_label),
            icon = if (useEmojisInUi) null else Icons.Outlined.CheckCircle,
            value = meta.completionDate,
            allowClear = true,
            onPick = { picked -> onMetaChange(meta.copy(completionDate = picked)) },
            onClear = { onMetaChange(meta.copy(completionDate = null)) },
        )

        PriorityMetaRow(
            value = meta.priority,
            onChange = { onMetaChange(meta.copy(priority = it)) },
            useEmojisInUi = useEmojisInUi,
        )

        OutlinedTextField(
            value = meta.recurrence.orEmpty(),
            onValueChange = { value ->
                onMetaChange(meta.copy(recurrence = value.ifBlank { null }))
            },
            singleLine = true,
            label = {
                Text(
                    text = if (useEmojisInUi) {
                        stringResource(R.string.category_tasks_recurrence)
                    } else {
                        stringResource(R.string.category_tasks_recurrence_label)
                    },
                )
            },
            modifier = modifier.fillMaxWidth(),
        )
    }
}

sealed interface TodoSheetMode {
    data object Add : TodoSheetMode
    data class Edit(val todo: Todo) : TodoSheetMode
    data object AddSubtask : TodoSheetMode
}

@Composable
private fun DateMetaRow(
    label: String,
    icon: ImageVector?,
    value: String?,
    allowClear: Boolean,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TextButton(
            onClick = {
                val initial = runCatching { value?.let(LocalDate::parse) }.getOrNull() ?: LocalDate.now()
                showDatePicker(
                    context = context,
                    initial = initial,
                    onPicked = onPick,
                )
            },
        ) {
            Text(text = value ?: stringResource(R.string.action_set))
        }

        if (allowClear && value != null) {
            TextButton(onClick = onClear) {
                Text(text = stringResource(R.string.action_clear))
            }
        }
    }
}

@Composable
private fun PriorityMetaRow(
    value: TasksPriority?,
    onChange: (TasksPriority?) -> Unit,
    useEmojisInUi: Boolean,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
    ) {
        val lowestColor = colorResource(R.color.priority_lowest)
        val lowColor = colorResource(R.color.priority_low)
        val mediumColor = colorResource(R.color.priority_medium)
        val highColor = colorResource(R.color.priority_high)
        val highestColor = colorResource(R.color.priority_highest)

        Text(
            text = stringResource(R.string.category_tasks_priority),
            style = MaterialTheme.typography.bodyMedium,
        )

        PriorityChip(
            selected = value == null,
            label = stringResource(R.string.category_tasks_priority_none),
            onClick = { onChange(null) },
            icon = null,
        )
        PriorityChip(
            selected = value == TasksPriority.LOWEST,
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_priority_lowest) else stringResource(R.string.category_tasks_priority_lowest_label),
            onClick = { onChange(TasksPriority.LOWEST) },
            icon = if (useEmojisInUi) null else Icons.Outlined.Star,
            iconTint = lowestColor,
        )
        PriorityChip(
            selected = value == TasksPriority.LOW,
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_priority_low) else stringResource(R.string.category_tasks_priority_low_label),
            onClick = { onChange(TasksPriority.LOW) },
            icon = if (useEmojisInUi) null else Icons.Outlined.Star,
            iconTint = lowColor,
        )
        PriorityChip(
            selected = value == TasksPriority.MEDIUM,
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_priority_medium) else stringResource(R.string.category_tasks_priority_medium_label),
            onClick = { onChange(TasksPriority.MEDIUM) },
            icon = if (useEmojisInUi) null else Icons.Outlined.Star,
            iconTint = mediumColor,
        )
        PriorityChip(
            selected = value == TasksPriority.HIGH,
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_priority_high) else stringResource(R.string.category_tasks_priority_high_label),
            onClick = { onChange(TasksPriority.HIGH) },
            icon = if (useEmojisInUi) null else Icons.Outlined.Star,
            iconTint = highColor,
        )
        PriorityChip(
            selected = value == TasksPriority.HIGHEST,
            label = if (useEmojisInUi) stringResource(R.string.category_tasks_priority_highest) else stringResource(R.string.category_tasks_priority_highest_label),
            onClick = { onChange(TasksPriority.HIGHEST) },
            icon = if (useEmojisInUi) null else Icons.Outlined.Star,
            iconTint = highestColor,
        )
    }
}

@Composable
private fun PriorityChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: ImageVector?,
    iconTint: Color? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = icon?.let { image ->
            {
                Icon(
                    imageVector = image,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconTint ?: LocalContentColor.current,
                )
            }
        },
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

internal fun showDatePicker(
    context: android.content.Context,
    initial: LocalDate,
    onPicked: (String) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val picked = LocalDate.of(year, month + 1, dayOfMonth)
            onPicked(picked.toString())
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

internal data class TasksMetaChipUi(
    val label: String,
    val icon: ImageVector?,
    val color: Color? = null,
)

internal fun priorityRank(priority: TasksPriority?): Int {
    return when (priority) {
        TasksPriority.HIGHEST -> 5
        TasksPriority.HIGH -> 4
        TasksPriority.MEDIUM -> 3
        TasksPriority.LOW -> 2
        TasksPriority.LOWEST -> 1
        TasksPriority.NONE, null -> 0
    }
}

@Composable
internal fun priorityColorFor(priority: TasksPriority?): Color? {
    return when (priority) {
        TasksPriority.LOWEST -> colorResource(R.color.priority_lowest)
        TasksPriority.LOW -> colorResource(R.color.priority_low)
        TasksPriority.MEDIUM -> colorResource(R.color.priority_medium)
        TasksPriority.HIGH -> colorResource(R.color.priority_high)
        TasksPriority.HIGHEST -> colorResource(R.color.priority_highest)
        TasksPriority.NONE, null -> null
    }
}

@Composable
internal fun buildTasksMetaChips(
    todo: Todo,
    useEmojisInUi: Boolean,
): List<TasksMetaChipUi> {
    val chips = ArrayList<TasksMetaChipUi>(8)

    val prio = todo.priority
    if (prio != null && prio != TasksPriority.NONE) {
        val label = when (prio) {
            TasksPriority.LOWEST -> if (useEmojisInUi) stringResource(R.string.category_tasks_priority_lowest) else stringResource(R.string.category_tasks_priority_lowest_label)
            TasksPriority.LOW -> if (useEmojisInUi) stringResource(R.string.category_tasks_priority_low) else stringResource(R.string.category_tasks_priority_low_label)
            TasksPriority.MEDIUM -> if (useEmojisInUi) stringResource(R.string.category_tasks_priority_medium) else stringResource(R.string.category_tasks_priority_medium_label)
            TasksPriority.HIGH -> if (useEmojisInUi) stringResource(R.string.category_tasks_priority_high) else stringResource(R.string.category_tasks_priority_high_label)
            TasksPriority.HIGHEST -> if (useEmojisInUi) stringResource(R.string.category_tasks_priority_highest) else stringResource(R.string.category_tasks_priority_highest_label)
            TasksPriority.NONE -> ""
        }
        chips.add(
            TasksMetaChipUi(
                label = label,
                icon = if (useEmojisInUi) null else Icons.Outlined.Star,
                color = priorityColorFor(prio),
            ),
        )
    }

    todo.dueDate?.let { value ->
        val label = if (useEmojisInUi) {
            stringResource(R.string.category_tasks_chip_due, value)
        } else {
            stringResource(R.string.category_tasks_chip_due_label, value)
        }
        chips.add(TasksMetaChipUi(label = label, icon = if (useEmojisInUi) null else Icons.Outlined.Event))
    }
    todo.scheduledDate?.let { value ->
        val label = if (useEmojisInUi) {
            stringResource(R.string.category_tasks_chip_scheduled, value)
        } else {
            stringResource(R.string.category_tasks_chip_scheduled_label, value)
        }
        chips.add(TasksMetaChipUi(label = label, icon = if (useEmojisInUi) null else Icons.Outlined.Schedule))
    }
    todo.startDate?.let { value ->
        val label = if (useEmojisInUi) {
            stringResource(R.string.category_tasks_chip_start, value)
        } else {
            stringResource(R.string.category_tasks_chip_start_label, value)
        }
        chips.add(TasksMetaChipUi(label = label, icon = if (useEmojisInUi) null else Icons.Outlined.FlightTakeoff))
    }
    todo.createdDate?.let { value ->
        val label = if (useEmojisInUi) {
            stringResource(R.string.category_tasks_chip_created, value)
        } else {
            stringResource(R.string.category_tasks_chip_created_label, value)
        }
        chips.add(TasksMetaChipUi(label = label, icon = if (useEmojisInUi) null else Icons.Outlined.AddCircle))
    }
    todo.completionDate?.let { value ->
        val label = if (useEmojisInUi) {
            stringResource(R.string.category_tasks_chip_done, value)
        } else {
            stringResource(R.string.category_tasks_chip_done_label, value)
        }
        chips.add(TasksMetaChipUi(label = label, icon = if (useEmojisInUi) null else Icons.Outlined.CheckCircle))
    }

    if (!todo.recurrence.isNullOrBlank()) {
        val label = if (useEmojisInUi) {
            stringResource(R.string.category_tasks_chip_recurrence, todo.recurrence!!)
        } else {
            stringResource(R.string.category_tasks_chip_recurrence_label, todo.recurrence!!)
        }
        chips.add(TasksMetaChipUi(label = label, icon = if (useEmojisInUi) null else Icons.Outlined.Repeat))
    }

    return chips.filter { it.label.isNotBlank() }
}
