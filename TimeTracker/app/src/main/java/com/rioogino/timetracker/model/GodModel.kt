package com.rioogino.timetracker.model

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import com.rioogino.timetracker.*
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

    var lastUpdated by mutableStateOf<Instant?>(null)

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

    val currentEntry: TimeEntry?
        get() {
            val first = timeEntries.firstOrNull()
            if (first?.isOngoing == true) {
                return first
            }
            return null
        }

    val mostRecentEntry: TimeEntry? get() = timeEntries.firstOrNull()

    /// Saved value of the currenly edited entry
    var currentlyEditedEntrySaveState: TimeEntry? by mutableStateOf(null)
    var currentlyEditedEntry = TimeEntryObservable()

    fun addOrUpdate(timeEntry: TimeEntry) {
        val index = timeEntries.indexOfFirst { it.id == timeEntry.id }
        if (index == -1) {
            timeEntries.add(timeEntry)
        } else {
            timeEntries[index] = timeEntry
        }
        timeEntries.sortByDescending { it.startTime }

        entriesMap[timeEntry.id] = timeEntry

        if (timeEntry.id != null && currentlyEditedEntrySaveState?.id == timeEntry.id) {
            val currentTimestamp = currentEntry?.lastUpdated
            val newTimestamp = timeEntry.lastUpdated
            if (currentTimestamp != null && newTimestamp != null) {
                if (currentTimestamp.isBefore(newTimestamp)) {
                    currentlyEditedEntrySaveState = timeEntry
                    currentlyEditedEntry.copyPropertiesFromEntry(timeEntry)
                }
            }
        }
    }

    fun deleteEntry(id: Long) {
        entriesMap.remove(id)
        val entry = timeEntries.find { it.id == id }
        entry?.let {
            timeEntries.remove(entry)
        }
    }

    fun addEntries(entries: List<TimeEntry>) {
        entries.forEach {
            entriesMap[it.id] = it
            if (it.id != null && it.id == currentlyEditedEntrySaveState?.id) {
                val currentTimestamp = currentEntry?.lastUpdated
                val newTimestamp = it.lastUpdated
                if (currentTimestamp != null && newTimestamp != null) {
                    if (currentTimestamp.isBefore(newTimestamp)) {
                        currentlyEditedEntrySaveState = it
                        currentlyEditedEntry.copyPropertiesFromEntry(it)
                    }
                }
            }
        }

        var entries = entriesMap.values.toMutableList()
        entries.sortByDescending { it.startTime }

        timeEntries.clear()
        timeEntries.addAll(entries)
    }


    fun refreshEverything(
        coroutineScope: CoroutineScope,
        apiRequest: ApiRequest,
        onEnd: (Throwable?) -> Unit
    ) {
        makeConcurrentRequests(
            coroutineScope = coroutineScope, {
                lastUpdated = Instant.now()
                onEnd(it)
            },
            {
                apiRequest?.getTimeEntries(
                    startDate = Instant.now().minusDays(7),
                    endDate = Instant.now().plusDays(1)
                )?.let { addEntries(it) }
            },
            {
                apiRequest?.getProjects()?.let {
                    setProjects(it)
                }
            },
            {
                apiRequest?.getStringTags()?.let {
                    tags.clear()

                    tags.addAll(it)
                }
            }
        )
    }

    /// Returns that most recent stopped entry if one exists within the given time period
    /// - Parameter currentlyEditedEntryId: if this is the most recent entry, it will return the
    /// stop time for the second most recent entry
    private fun lastEntryStopTime(currentlyEditedEntryId: Long?, withinLastNDays: Long = 1): Instant? {
        val mostRecent = timeEntries.firstOrNull()
        var lastEntryStopTime: Instant? = null

        if (currentlyEditedEntryId == null && mostRecent != null) {
            lastEntryStopTime = mostRecent.endTime
        } else if (currentlyEditedEntryId == mostRecent?.id && timeEntries.count() >= 2) {
            lastEntryStopTime = timeEntries[1].endTime
        }
        if (lastEntryStopTime?.isBefore(Instant.now().minusDays(withinLastNDays)) == true) {
            // Max age of 1 day
            lastEntryStopTime = null
        }

        return lastEntryStopTime
    }

    fun lastEntryStopTime(withinLastNDays: Long = 1): Instant? {
        return lastEntryStopTime(currentlyEditedEntrySaveState?.id, withinLastNDays)
    }

    fun setTags(tags: Collection<String>) {
        this.tags.clear()
        this.tags.addAll(tags)
    }

    fun setProjects(projects: Collection<Project>) {
        this.projects.clear()
        this.projects.putAll(projects.associateBy { it.id })
    }

    /// Must be in order already
    fun setEntries(entries: Collection<TimeEntry>) {
        timeEntries.clear()
        timeEntries.addAll(entries)
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
            "2022-07-23T07:54:35+00:00", "2022-07-23T08:10:02Z", null,
            1, listOf("Tag1", "Another Tag")
        ),

        TimeEntry(
            11, "Playing around with Jetpack Compose",
            "2022-08-03T20:30:10+00:00", "2022-08-03T21:30:00Z", null,
            3, listOf("Assignment", "Coding")
        ),

        TimeEntry(
            12,
            "Trying to figure out why accessing legends causes scene rendering to completely fail",
            "2022-08-04T22:31:51+12:00",
            "2022-08-04T23:04:45+12:00",
            null,
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
            null,
            3, listOf("Assignment", "Coding")
        )
    ).sortedByDescending { it.startTime }
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
