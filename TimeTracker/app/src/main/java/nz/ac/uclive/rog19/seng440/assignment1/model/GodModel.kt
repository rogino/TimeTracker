package nz.ac.uclive.rog19.seng440.assignment1.model

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import nz.ac.uclive.rog19.seng440.assignment1.TAG
import java.sql.Time
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun getTagsFromEntries(entries: Collection<TimeEntry>): List<String> {
    var tags = mutableSetOf<String>()
    entries.forEach { it.tagNames?.let { tags.addAll(it) } }

    return tags.toList().sorted()
}

// TODO https://dev.to/zachklipp/implementing-snapshot-aware-data-structures-3pi8
class GodModel(
    /// Map of project IDs to projects
    projects: Map<Long, Project>,
    /// Time entries sorted by start time, newest first
    timeEntries: List<TimeEntry>,
    tags: List<String>? = null
) : ViewModel() {
    var projects = mutableStateMapOf<Long, Project>()
    var tags = mutableStateListOf<String>()

    /// Should not be modified directly: use methods to do so
    var timeEntries = mutableStateListOf<TimeEntry>()

    private var entriesMap: MutableMap<Long?, TimeEntry>

    init {
        this.projects.putAll(projects)
        this.timeEntries.addAll(timeEntries)
        this.entriesMap = this.timeEntries.associateBy { it.id }.toMutableMap()

        if (tags == null) {
            this.tags = getTagsFromEntries(timeEntries).toMutableStateList()
        } else {
            this.tags.addAll(tags)
        }
    }

    val currentEntry: TimeEntry? get() = timeEntries.first { it.isOngoing }

    fun addOrUpdate(timeEntry: TimeEntry) {
        val index = timeEntries.indexOfFirst { it.id == timeEntry.id }
        if (index == -1) {
            timeEntries.add(timeEntry)
        } else {
            timeEntries[index] = timeEntry
        }
        timeEntries.sortByDescending { it.startTime }

        entriesMap[timeEntry.id] = timeEntry
    }

    fun addEntries(entries: List<TimeEntry>) {
        entries.forEach {
            entriesMap[it.id] = it
        }

        timeEntries.clear()
        timeEntries.addAll(entriesMap.values)
        timeEntries.sortByDescending { it.startTime }
    }


    fun setProjects(projects: List<Project>) {
        this.projects.clear()
        this.projects.putAll(projects.associateBy { it.id })
    }

    /// - Parameter timeEntries: time entries sorted by start time, newest first
//    fun addContiguousEntries(timeEntries: List<TimeEntry>) {
//        if (timeEntries.isEmpty()) return;
//        val startIndex =
//            this.timeEntries.indexOfFirst { timeEntries.first().startTime <= it.startTime }
//        val endIndex = this.timeEntries.indexOfLast { it.startTime <= timeEntries.last().startTime }
//
//        Log.d(TAG, "startElements = 0 to ${startIndex}")
//        Log.d(TAG, "endElements = ${endIndex} to ${this.timeEntries.count() - 1} (if endIndex != -1)")
//        val endElements = this.timeEntries.slice(endIndex + 1 until this.timeEntries.count())
//
//        if (this.timeEntries.isNotEmpty() && startIndex != -1) {
//            Log.d(TAG, "remove time entries in range = ${if (startIndex == -1) 0 else startIndex}-${if (endIndex == -1) this.timeEntries.count() - 1 else endIndex}")
//            this.timeEntries.removeRange(
//                if (startIndex == -1) 0 else startIndex,
//                if (endIndex == -1) this.timeEntries.count() else endIndex + 1
//            )
//        }
//        Log.d(TAG, "Array now has ${this.timeEntries.count()} items")
//        this.timeEntries.addAll(timeEntries)
//        endElements?.let { this.timeEntries.addAll(it) }
//    }

    constructor(projects: List<Project>, timeEntries: List<TimeEntry>) : this(
        projects = projects.associateBy { it.id },
        timeEntries = timeEntries
    )

    constructor() : this(
        projects = emptyMap(),
        timeEntries = emptyList()
    )


    fun debugPrintEntries() {
        timeEntries.forEach {
            Log.d(TAG, it.description)
        }
    }
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
            1, listOf("Tag1", "Another Tag")
        ),

        TimeEntry(
            11, "Playing around with Jetpack Compose",
            "2022-08-03T20:30:10+00:00", "2022-08-03T21:30:00Z",
            3, listOf("Assignment", "Coding")
        ),

        TimeEntry(
            12,
            "Trying to figure out why accessing legends causes scene rendering to completely fail",
            "2022-08-04T22:31:51+12:00",
            "2022-08-04T23:04:45+12:00",
            1,
            emptyList()
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
            3, listOf("Assignment", "Coding")
        )

    )
)


//fun testAdd() {
//    val t = Instant.now()
//
//    val testInitial = listOf<TimeEntry>(
//        TimeEntry(startTime = t, description = "0"),
//        TimeEntry(startTime = t.plusSeconds(1), description = "1"),
//        TimeEntry(startTime = t.plusSeconds(2), description = "2"),
//    )
//    val testModel = GodModel(emptyList(), emptyList())
//    testModel.addContiguousEntries(testInitial)
//    testModel.debugPrintEntries()
//
//
//    Log.d(TAG, "Re-adding same entries")
//    testModel.addContiguousEntries(testInitial)
//    testModel.debugPrintEntries()
//
//    Log.d(TAG, "Add later overlap")
//    testModel.addContiguousEntries(
//        listOf(
//            TimeEntry(startTime = t.plusSeconds(2), description = "2"),
//            TimeEntry(startTime = t.plusSeconds(3), description = "3"),
//            TimeEntry(startTime = t.plusSeconds(4), description = "4"),
//        )
//    )
//    testModel.debugPrintEntries()
//
//    Log.d(TAG, "Add later, no overlap")
//    testModel.addContiguousEntries(
//        listOf(
//            TimeEntry(startTime = t.plusSeconds(6), description = "6"),
//            TimeEntry(startTime = t.plusSeconds(7), description = "7"),
//            TimeEntry(startTime = t.plusSeconds(8), description = "8"),
//        )
//    )
//    testModel.debugPrintEntries()
//
//    Log.d(TAG, "Add prior, overlap")
//    testModel.addContiguousEntries(
//        listOf(
//            TimeEntry(startTime = t.plusSeconds(-1), description = "-1"),
//            TimeEntry(startTime = t.plusSeconds(0), description = "0"),
//            TimeEntry(startTime = t.plusSeconds(1), description = "1"),
//        )
//    )
//    testModel.debugPrintEntries()
//
//    Log.d(TAG, "Add prior, no overlap")
//    testModel.addContiguousEntries(
//        listOf(
//            TimeEntry(startTime = t.plusSeconds(-6), description = "-6"),
//            TimeEntry(startTime = t.plusSeconds(-5), description = "-5"),
//            TimeEntry(startTime = t.plusSeconds(-4), description = "-4"),
//        )
//    )
//    testModel.debugPrintEntries()
//}
//

