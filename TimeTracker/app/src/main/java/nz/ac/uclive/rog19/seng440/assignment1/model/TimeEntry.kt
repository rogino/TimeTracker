package nz.ac.uclive.rog19.seng440.assignment1.model

import java.time.Duration
import java.time.Instant
import java.util.*

// https://api.track.toggl.com/api/v9/me/time_entries/current
// https://api.track.toggl.com/api/v9/me/time_entries
class TimeEntry(
    id: Int? = null,
    description: String = "",
    startTime: Instant,
    endTime: Instant? = null,
//    duration: Int = -1,
    projectId: Int? = null,
    tagNames: Array<String> = emptyArray()
) {
    var id: Int? = id

    var description: String = description

    /// start: String
    var startTime: Instant = startTime

    /// end: String
    var endTime: Instant? = endTime

    /// Duration in seconds
    /// -startTime.epoch for running timers
//    var duration: Int = duration

    val duration: Duration?
        get() {
            return Duration.between(
                startTime,
                if (endTime == null) Calendar.getInstance().toInstant() else endTime
            )
        }

    val isOngoing get() = endTime == null

    /// project_id: Int
    var projectId: Int? = projectId


    /// tags: [String]
    /// see also tag_ids: [Int]
    var tagNames: Array<String> = tagNames

    constructor(
        id: Int? = null,
        description: String = "",
        startTime: String,
        endTime: String? = null,
//        duration: Int = -1,
        projectId: Int? = null,
        tagNames: Array<String> = emptyArray()
    ) : this(
        id = id, description = description,
        startTime = Instant.parse(startTime.replace("+00:00", "Z")),
        endTime = if (endTime == null) null else Instant.parse(endTime.replace("+00:00", "Z")),
//        duration = duration,
        projectId = projectId, tagNames = tagNames
    )
}