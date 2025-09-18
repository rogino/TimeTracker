package com.rioogino.timetracker.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rioogino.timetracker.model.Project


@Composable
fun ColoredDot(
    color: Color,
    size: Dp = 12.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(
                width = borderWidth,
                color = borderColor ?: (if (isSystemInDarkTheme()) Color.LightGray else Color.DarkGray),
                shape = CircleShape
            )
            .background(color)
    )
}

@Composable
fun ColoredDot(
    project: Project?,
    size: Dp = 12.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color? = null,
    modifier: Modifier = Modifier
) {
    ColoredDot(
        color = project?.colorCompose ?: Color.Unspecified,
        size = size,
        borderWidth = borderWidth,
        borderColor = borderColor,
        modifier = modifier
            .alpha(if (project == null) 0f else 1f)
    )
}
