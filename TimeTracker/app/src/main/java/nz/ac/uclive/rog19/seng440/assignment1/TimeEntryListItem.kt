package nz.ac.uclive.rog19.seng440.assignment1

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nz.ac.uclive.rog19.seng440.assignment1.model.*
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TimeEntryListItem(timeEntry: TimeEntry, projects: Map<Int, Project>,
                      zoneId: ZoneId = Clock.systemDefaultZone().zone,
                      now: Instant = Instant.now()) {
    val project = projects[timeEntry.projectId]
    val zonedStart = ZonedDateTime.ofInstant(timeEntry.startTime, zoneId)

    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    var timeText = zonedStart.format(timeFormatter)
    timeEntry.endTime?.also { endTime ->
        val zonedEnd = ZonedDateTime.ofInstant(endTime, zoneId)
        timeText += " to ${zonedEnd.format(timeFormatter)}"
    }

    var durationText = durationFormatter(Duration.between(timeEntry.startTime, timeEntry.endTime ?: now))

    Column() {
        Row() {
            Text(text = timeEntry.description)
            Spacer(modifier = Modifier.weight(1f))
//            Text(zonedStart.format(DateTimeFormatter.ISO_LOCAL_DATE))
            Text(text = durationText)
        }

        Row {
            project?.also { project ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(project.colorCompose) )
                    Text(text = project.name)
                }
            } ?: run {  }
            Spacer(modifier = Modifier.weight(1f))
            Row() {
                Text(text = timeText)
            }
        }
    }

}

// https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/tags


@Preview(showBackground = true)
@Composable
fun TimeEntryListItem_Preview() {
    TimeTrackerTheme {
        TimeEntryListItem(timeEntry = mockModel.timeEntries.first(), projects = mockModel.projects)
    }
}
