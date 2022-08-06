package nz.ac.uclive.rog19.seng440.assignment1.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class GodModel(
    projects: MutableMap<Long, Project>,
    timeEntries: MutableList<TimeEntry>
) {
    var projects: MutableMap<Long, Project> = projects
    var timeEntries: MutableList<TimeEntry> = timeEntries

    val currentEntry: TimeEntry? get() = timeEntries.first { it.isOngoing }

    constructor(projects: List<Project>, timeEntries: List<TimeEntry>) : this(
        projects = projects.associateBy { it.id }.toMutableMap(),
        timeEntries = timeEntries.toMutableList()
    )
}

val mockModel = GodModel(
    mutableListOf(
        Project(1, "Project Name", "#FF0000"),
        Project(3, "SENG440", "#A3B081"),
    ),
    mutableListOf(
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
            12, "Android DateTime stuff",
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