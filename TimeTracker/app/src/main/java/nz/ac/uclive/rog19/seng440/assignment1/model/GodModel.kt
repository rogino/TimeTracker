package nz.ac.uclive.rog19.seng440.assignment1.model

class GodModel(projects: Map<Int, Project> = emptyMap(),
               timeEntries: List<TimeEntry>) {



    var projects: Map<Int, Project> = projects
    var timeEntries: List<TimeEntry> = timeEntries

    val currentEntry: TimeEntry? get() = timeEntries.first { it.isOngoing }

    constructor(projects: List<Project>, timeEntries: List<TimeEntry>) : this(
        projects = projects.associate { Pair(it.id, it) },
        timeEntries = timeEntries
    )
}

val mockModel = GodModel(listOf(
    Project(1, "Project Name", "#FF0000")
),
    listOf(
        TimeEntry(10, "Entry description",
            "2022-07-23T07:54:35+00:00", "2022-07-23T08:10:02Z" ,
            1, arrayOf("Tag1", "Another Tag"))
    )
)