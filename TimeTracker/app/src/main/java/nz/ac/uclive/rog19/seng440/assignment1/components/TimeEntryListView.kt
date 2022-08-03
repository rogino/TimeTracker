package nz.ac.uclive.rog19.seng440.assignment1

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import nz.ac.uclive.rog19.seng440.assignment1.model.*
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Composable
fun TimeEntryListView(
    modifier: Modifier = Modifier,
    entries: List<TimeEntry>,
    projects: Map<Int, Project>,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: Instant = Instant.now()) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(items = entries) { index, entry ->
            if (index != 0) {
                Divider()
            }
            // Passing null for now means views don't to be re-rendered? No idea if this is actually the case
            TimeEntryListItem(timeEntry = entry, projects = projects, zoneId = zoneId, now = if (entry.isOngoing) now else null)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TimeEntryListView_Preview() {
    TimeTrackerTheme {
        TimeEntryListView(entries = mockModel.timeEntries, projects = mockModel.projects)

    }
}