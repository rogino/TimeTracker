package com.rioogino.timetracker.model

import androidx.compose.ui.graphics.Color
import com.beust.klaxon.Json

// https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/projects
data class Project(
    val id: Long,
    val name: String,

    /// Hex color code, with '#'
    val color: String
) {
    /// Compose color for the project
    @Json(ignored = true)
    val colorCompose: Color get() = Color(android.graphics.Color.parseColor(color))
}
