package nz.ac.uclive.rog19.seng440.assignment1

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import nz.ac.uclive.rog19.seng440.assignment1.model.Project
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntry
import nz.ac.uclive.rog19.seng440.assignment1.model.mockModel
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun TimeEntryListView(
    modifier: Modifier = Modifier,
    entries: SnapshotStateList<TimeEntry>,
    projects: SnapshotStateMap<Long, Project>,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: State<Instant> = mutableStateOf(Instant.now())
) {
    LazyColumn(modifier = modifier) {
        var previousDate: ZonedDateTime? = null
        itemsIndexed(items = entries) { index, entry ->
            if (index != 0) {
                Divider()
            }
            val date = entry.startTime.atZone(zoneId)
            if (previousDate == null || (date.dayOfYear != previousDate!!.dayOfYear || date.year != previousDate!!.year)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = date.format(DateTimeFormatter.ISO_LOCAL_DATE).toString(),
                        fontSize = 20.sp
                    )
                }
            }
            previousDate = date

            // Passing null for `now` means only ongoing view gets re-rendered when date changes
            TimeEntryListItem(
                timeEntry = entry,
                projects = projects,
                zoneId = zoneId,
                now = if (entry.isOngoing) now.value else null
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TimeEntryListView_Preview() {
    TimeTrackerTheme {
        TimeEntryListView(
            entries = mockModel.timeEntries,
            projects = mockModel.projects
        )

    }
}