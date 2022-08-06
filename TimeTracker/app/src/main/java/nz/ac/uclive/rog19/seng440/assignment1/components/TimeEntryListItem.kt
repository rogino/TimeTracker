package nz.ac.uclive.rog19.seng440.assignment1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nz.ac.uclive.rog19.seng440.assignment1.model.Project
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntry
import nz.ac.uclive.rog19.seng440.assignment1.model.mockModel
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TimeEntryListItem(
    modifier: Modifier = Modifier,
    timeEntry: TimeEntry,
    projects: Map<Long, Project>,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: Instant? = Instant.now()
) {
    val project = projects[timeEntry.projectId]
    val zonedStart = ZonedDateTime.ofInstant(timeEntry.startTime, zoneId)

    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    var timeText = zonedStart.format(timeFormatter)
    timeEntry.endTime?.also { endTime ->
        val zonedEnd = ZonedDateTime.ofInstant(endTime, zoneId)
        timeText += " to ${zonedEnd.format(timeFormatter)}"
    }

    var endTime = timeEntry.endTime ?: now
    var durationText = endTime?.let {
        durationFormatter(
            duration = Duration.between(timeEntry.startTime, it),
            // show all components if ongoing
            numComponents = if (timeEntry.isOngoing) 10 else 2
        )
    }

    Column(modifier = Modifier
        .padding(vertical = 4.dp)
        .then(modifier)) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = timeEntry.description,
            modifier = Modifier.weight(1f))
            durationText?.also {
//            Text(zonedStart.format(DateTimeFormatter.ISO_LOCAL_DATE))
                Text(text = durationText, modifier = Modifier.width(IntrinsicSize.Max))
            }
        }

        Row {
            project?.also { project ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = if (isSystemInDarkTheme()) Color.LightGray else Color.DarkGray,
                                shape = CircleShape
                            )
                            .background(project.colorCompose)
                    )
                    Text(text = project.name)
                }
            } ?: run { }
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
        Column {
            TimeEntryListItem(
                timeEntry = mockModel.timeEntries.find { it.id == 12L }!!,
                projects = mockModel.projects
            )

            mockModel.currentEntry?.also { timeEntry ->
                TimeEntryListItem(timeEntry = timeEntry, projects = mockModel.projects)
            }
        }
    }
}
