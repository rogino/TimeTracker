package nz.ac.uclive.rog19.seng440.assignment1.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import java.lang.reflect.TypeVariable
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class GodModel(
    projects: Map<Long, Project>,
    timeEntries: List<TimeEntry>
) {
    var projects = mutableStateMapOf<Long, Project>()
    var timeEntries = mutableStateListOf<TimeEntry>()

    init {
        this.projects.putAll(projects)
        this.timeEntries.addAll(timeEntries)
    }

    val currentEntry: TimeEntry? get() = timeEntries.first { it.isOngoing }

    constructor(projects: List<Project>, timeEntries: List<TimeEntry>) : this(
        projects = projects.associateBy { it.id },
        timeEntries = timeEntries
    )
}

val mockModel = GodModel(
    listOf(
        Project(1, "Project Name", "#FF0000"),
        Project(3, "SENG440", "#A3B081"),
    ),
    listOf(
        TimeEntry(
            10, "Entry description",
            "2022-07-23T07:54:35+00:00", "2022-07-23T08:10:02Z",
            1, arrayOf("Tag1", "Another Tag")
        ),

        TimeEntry(
            11, "Playing around with Jetpack Compose",
            "2022-08-03T20:30:10+00:00", "2022-08-03T21:30:00Z",
            3, arrayOf("Assignment", "Coding")
        ),

        TimeEntry(
            12, "Trying to figure out why accessing legends causes scene rendering to completely fail",
            "2022-08-04T22:31:51+12:00", "2022-08-04T23:04:45+12:00",
            1, arrayOf()
        ),

        TimeEntry(
            13, "Android DateTime stuff",
            DateTimeFormatter.ISO_DATE_TIME.format(
                ZonedDateTime.ofInstant(
                    Instant.now().minusSeconds((60 * 60 + 12).toLong()),
                    ZoneOffset.UTC
                )
            ),
            null,
            3, arrayOf("Assignment", "Coding")
        )

    )
)