package com.rioogino.timetracker.model

import com.beust.klaxon.Json

data class Me(
    val id: Long,
    @Json(name = "api_token")
    val apiToken: String,
    val email: String,
    @Json(name = "fullname")
    val fullName: String,
    @Json(name = "default_workspace_id")
    val defaultWorkspaceId: Int
)
