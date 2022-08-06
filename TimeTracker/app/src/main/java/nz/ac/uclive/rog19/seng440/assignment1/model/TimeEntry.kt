package nz.ac.uclive.rog19.seng440.assignment1.model

import android.util.Log
import com.beust.klaxon.Converter
import com.beust.klaxon.Json
import com.beust.klaxon.JsonValue
import nz.ac.uclive.rog19.seng440.assignment1.TAG
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.*

// https://api.track.toggl.com/api/v9/me/time_entries/current
// https://api.track.toggl.com/api/v9/me/time_entries
class TimeEntry(
    id: Long? = null,
    description: String = "",
    startTime: Instant,
    endTime: Instant? = null,
//    duration: Long = -1,
    projectId: Long? = null,
    tagNames: Array<String> = emptyArray()
) {
    var id: Long? = id

    var description: String = description

    @Json(name = "start")
    var startTime: Instant = startTime

    @Json(name = "end")
    var endTime: Instant? = endTime

    /// Duration in seconds
    /// -startTime.epoch for running timers
//    var duration: Long = duration

    val duration: Duration?
        get() {
            return Duration.between(
                startTime,
                if (endTime == null) Calendar.getInstance().toInstant() else endTime
            )
        }

    val isOngoing get() = endTime == null

    @Json(name = "project_id")
    var projectId: Long? = projectId


    /// tags: [String]
    /// see also tag_ids: [Long]
    @Json(name = "tags")
    var tagNames: Array<String> = tagNames

    constructor(
        id: Long? = null,
        description: String = "",
        startTime: String,
        endTime: String? = null,
        projectId: Long? = null,
        tagNames: Array<String> = emptyArray()
    ) : this(
        id = id, description = description,
        startTime = OffsetDateTime.parse(startTime).toInstant(),
        endTime = if (endTime == null) null else OffsetDateTime.parse(endTime).toInstant(),
//        duration = duration,
        projectId = projectId, tagNames = tagNames
    )
}

val DateTimeConverter = object: Converter {
    override fun canConvert(cls: Class<*>) = cls == Instant::class.java
    override fun toJson(value: Any): String {
        if (value is Instant) {
            return value.toString()
        }
        return "null"
    }

    override fun fromJson(jv: JsonValue): Any? {
        jv.string.also {
            try {
                return OffsetDateTime.parse(it).toInstant()
            } catch(err: DateTimeParseException) {
                Log.e(TAG, "Error parsing date time string '$it'", err)
            }
        }
        return null
    }
}

class TimeEntryJsonAdapter {
    fun fromJson(json: TimeEntryJson): TimeEntry {
        val startTime = Instant.parse(json.start.replace("+00:00", "Z"))
        val endTime = if (json.end == null) null else Instant.parse(json.end.replace("+00:00", "Z"))

        return TimeEntry(id = json.id,
        description = json.description,
        startTime = startTime,
        endTime = endTime,
        projectId = json.project_id,
        tagNames = json.tags)
    }
}

class TimeEntryJson(
    val id: Long? = null,
    val description: String = "",
    val start: String,
    val end: String? = null,
    val project_id: Long? = null,
    val tags: Array<String> = emptyArray()
) {}