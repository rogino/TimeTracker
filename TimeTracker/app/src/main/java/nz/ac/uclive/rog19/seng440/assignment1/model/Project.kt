package nz.ac.uclive.rog19.seng440.assignment1.model

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