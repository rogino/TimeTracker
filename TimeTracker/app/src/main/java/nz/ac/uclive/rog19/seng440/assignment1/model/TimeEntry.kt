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
data class TimeEntry(
    var id: Long? = null,
    var description: String = "",

    @Json(name = "start")
    var startTime: Instant,

    @Json(name = "end")
    var endTime: Instant? = null,

    @Json(name = "project_id")
    var projectId: Long? = null,

    /// see also tag_ids: [Long]
    @Json(name = "tags")
    var tagNames: Array<String> = emptyArray()
) {
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
