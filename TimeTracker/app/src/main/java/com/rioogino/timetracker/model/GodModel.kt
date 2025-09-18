package com.rioogino.timetracker.model

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import com.rioogino.timetracker.ApiRequest
import com.rioogino.timetracker.QuotaUpdateListener
import com.rioogino.timetracker.makeConcurrentRequests
import com.rioogino.timetracker.minusDays
import com.rioogino.timetracker.plusDays
import kotlinx.coroutines.CoroutineScope
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun getTagsFromEntries(entries: Collection<TimeEntry>): List<String> {
    var tags = mutableSetOf<String>()
    entries.forEach { it.tagNames?.let { tags.addAll(it) } }

    return tags.toList().sorted()
}

class GodModel(
    projects: Map<Long, Project>,
    timeEntries: List<TimeEntry>,
    tags: List<String>? = null
) : ViewModel(), QuotaUpdateListener { // Implement QuotaUpdateListener
    var projects = mutableStateMapOf<Long, Project>()
    var tags = mutableStateListOf<String>()

    var timeEntries = mutableStateListOf<TimeEntry>()

    private var entriesMap: MutableMap<Long?, TimeEntry>

    var lastUpdated by mutableStateOf<Instant?>(null)

    // --- API Quota State ---
    var apiQuotaRemaining by mutableStateOf<Int?>(null)
        private set
    var apiQuotaResetTimestamp by mutableStateOf<Instant?>(null) // Changed from apiQuotaResetsInSecs (Long?) to Instant?
        private set
    // --- End API Quota State ---

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

    override fun onQuotaUpdated(remaining: Int?, resetTimestamp: Instant?) { // Signature updated
        apiQuotaRemaining = remaining
        apiQuotaResetTimestamp = resetTimestamp // Assignment updated
        Log.d(TAG, "GodModel Quota Updated: Remaining=$apiQuotaRemaining, ResetTimestamp=$apiQuotaResetTimestamp")
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

        var entriesList = entriesMap.values.toMutableList()
        entriesList.sortByDescending { it.startTime }

        timeEntries.clear()
        timeEntries.addAll(entriesList)
    }


    fun refreshEverything(
        coroutineScope: CoroutineScope,
        apiRequest: ApiRequest,
        onEnd: (Throwable?) -> Unit
    ) {
        apiRequest.quotaListener = this 

        makeConcurrentRequests(
            coroutineScope = coroutineScope, {
                lastUpdated = Instant.now()
                onEnd(it)
            },
            {
                apiRequest.getTimeEntries(
                    startDate = Instant.now().minusDays(7),
                    endDate = Instant.now().plusDays(1)
                )?.let { addEntries(it) }
            },
            {
                apiRequest.getProjects()?.let {
                    setProjects(it)
                }
            },
            {
                apiRequest.getStringTags()?.let {
                    tags.clear()
                    tags.addAll(it)
                }
            }
        )
    }

    private fun lastEntryStopTime(currentlyEditedEntryId: Long?, withinLastNDays: Long = 1): Instant? {
        val mostRecent = timeEntries.firstOrNull()
        var lastEntryStopTime: Instant? = null

        if (currentlyEditedEntryId == null && mostRecent != null) {
            lastEntryStopTime = mostRecent.endTime
        } else if (currentlyEditedEntryId == mostRecent?.id && timeEntries.count() >= 2) {
            lastEntryStopTime = timeEntries[1].endTime
        }
        if (lastEntryStopTime?.isBefore(Instant.now().minusDays(withinLastNDays)) == true) {
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

    fun setEntries(entries: Collection<TimeEntry>) {
        timeEntries.clear()
        timeEntries.addAll(entries)
    }

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
    
    companion object {
        private const val TAG = "GodModel"
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
