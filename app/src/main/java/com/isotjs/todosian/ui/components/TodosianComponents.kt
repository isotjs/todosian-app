package com.isotjs.todosian.ui.components

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object TodosianDimens {
    val ScreenHorizontalPadding: Dp = 16.dp
    val ProgressHeight: Dp = 6.dp
}

object TodosianMotion {
    /** M3 Expressive motion token SlowSpatial: spring(damping = 0.8, stiffness = 200). YES I AM TOO LAZY TO UPDATE THE M3 */ 
    fun <T> slowSpatialSpec(): SpringSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 200f,
    )

    /** M3 Expressive motion token DefaultEffects: spring(damping = 1.0, stiffness = 1600). YES I AM TOO LAZY TO UPDATE THE M3 */ 
    fun <T> defaultEffectsSpec(): SpringSpec<T> = spring(
        dampingRatio = 1f,
        stiffness = 1600f,
    )
}

@Composable
fun TodosianSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun TodosianLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(TodosianDimens.ProgressHeight / 2)
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .height(TodosianDimens.ProgressHeight)
            .clip(shape),
    )
}
