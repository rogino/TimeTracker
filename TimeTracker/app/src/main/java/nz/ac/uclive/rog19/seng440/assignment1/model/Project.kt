package nz.ac.uclive.rog19.seng440.assignment1.model

import androidx.compose.ui.graphics.Color

// https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/projects
class Project(id: Long, name: String, color: String) {
    val id: Long = id
    val name: String = name

    /// Hex color code, with '#'
    val color: String = color

    /// Compose color for the project
    val colorCompose: Color get() = Color(android.graphics.Color.parseColor(color))
}