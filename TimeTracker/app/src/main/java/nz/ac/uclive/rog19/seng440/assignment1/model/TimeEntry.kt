package nz.ac.uclive.rog19.seng440.assignment1.model

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.beust.klaxon.Converter
import com.beust.klaxon.Json
import com.beust.klaxon.JsonValue
import nz.ac.uclive.rog19.seng440.assignment1.TAG
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.*

val APP_IDENTIFIER = "nz.ac.uclive.rog19.seng440.assignment1.timetracker"


// https://api.track.toggl.com/api/v9/me/time_entries/current
// https://api.track.toggl.com/api/v9/me/time_entries
data class TimeEntry(
    var id: Long? = null,

    var description: String = "",

    @Json(name = "start")
    var startTime: Instant = Calendar.getInstance().toInstant(),

    @Json(name = "stop")
    var endTime: Instant? = null,

    @Json(name = "project_id")
    var projectId: Long? = null,

    /// see also tag_ids: [Long]
    @Json(name = "tags")
    var tagNames: List<String>? = null,

    @Json(name = "created_with")
    var createdWith: String = APP_IDENTIFIER,

    @Json(name = "wid")
    var workspaceId: Int? = null
) {
    /// Duration in seconds
    /// -startTime.epoch for running timers
//    var duration: Long = duration

    @Json(name = "duration")
    val durationSeconds: Long?
        get() {
            if (isOngoing) {
                return -startTime.epochSecond
            }
            // Duration necessary for stopping a timer
            return duration?.seconds
        }

    @Json(ignored = true)
    val duration: Duration?
        get() {
            return Duration.between(
                startTime,
                if (endTime == null) Calendar.getInstance().toInstant() else endTime
            )
        }

    @Json(ignored = true)
    val isOngoing
        get() = endTime == null

    constructor(
        id: Long? = null,
        description: String = "",
        startTime: String,
        endTime: String? = null,
        projectId: Long? = null,
        tagNames: List<String> = emptyList(),
        workspaceId: Int? = null
    ) : this(
        id = id, description = description,
        startTime = OffsetDateTime.parse(startTime).toInstant(),
        endTime = if (endTime == null) null else OffsetDateTime.parse(endTime).toInstant(),
        projectId = projectId, tagNames = tagNames,
        workspaceId = workspaceId
    )

    fun toObservable(): TimeEntryObservable {
        return TimeEntryObservable(
            id = id,
            description = description,
            startTime = startTime!!,
            endTime = endTime,
            projectId = projectId,
            tagNames = tagNames?.toTypedArray() ?: emptyArray(),
            workspaceId = workspaceId
        )
    }
}

class TimeEntryObservable(
    id: Long? = null,
    description: String = "",
    startTime: Instant? = null,
    endTime: Instant? = null,
    projectId: Long? = null,
    tagNames: Array<String> = emptyArray(),
    workspaceId: Int? = null
) {
    var id: Long? by mutableStateOf(id)
    var description: String by mutableStateOf(description)
    var startTime: Instant? by mutableStateOf(startTime)
    var endTime: Instant? by mutableStateOf(endTime)
    var projectId: Long? by mutableStateOf(projectId)
    val tagNames: SnapshotStateList<String> = mutableStateListOf(*tagNames)
    var workspaceId: Int? by mutableStateOf(workspaceId)

    fun toTimeEntry(): TimeEntry? {
        if (startTime == null) {
            return null
        }
        return TimeEntry(
            id = id,
            description = description,
            startTime = startTime!!,
            endTime = endTime,
            projectId = projectId,
            tagNames = tagNames.toList().sorted(),
            workspaceId = workspaceId
        )
    }
}

//data class TimeEntryDto(
//    var id: Long? = null,
//
//    var description: String = "",
//
//    @Json(name = "start")
//    var startTime: Instant = Calendar.getInstance().toInstant(),
//
//    @Json(name = "stop")
//    var endTime: Instant? = null,
//
//    @Json(name = "project_id")
//    var projectId: Long? = null,
//
//    @Json(name = "tag_ids")
//    var tagIds: Array<String>? = null,
//
//    @Json(name = "tags")
//    var tagNames: Array<String>? = null,
//
//    @Json(name = "created_with")
//    var createdWith: String = APP_IDENTIFIER,
//
//    @Json(name = "wid")
//    var workspaceId: Int? = null
//) {
//}

val DateTimeConverter = object : Converter {
    override fun canConvert(cls: Class<*>) = cls == Instant::class.java
    override fun toJson(value: Any): String {
        if (value is Instant) {
            return "\"${value.toString()}\""
        }
        return "null"
    }

    override fun fromJson(jv: JsonValue): Any? {
        jv.string?.also {
            try {
                if (it != null && it.last() == 'Z') {
                    return Instant.parse(it)
                }
                return OffsetDateTime.parse(it).toInstant()
            } catch (err: DateTimeParseException) {
                Log.e(TAG, "JSON parse issue: error parsing date time string '$it'", err)
            }
        }
        return null
    }
}
