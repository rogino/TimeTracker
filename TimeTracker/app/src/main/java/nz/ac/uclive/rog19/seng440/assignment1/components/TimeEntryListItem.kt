package nz.ac.uclive.rog19.seng440.assignment1

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nz.ac.uclive.rog19.seng440.assignment1.components.ColoredDot
import nz.ac.uclive.rog19.seng440.assignment1.model.Project
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntry
import nz.ac.uclive.rog19.seng440.assignment1.model.mockModel
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun TimeEntryListItem(
    modifier: Modifier = Modifier,
    timeEntry: TimeEntry,
    projects: Map<Long, Project>,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    // only read if end time is null
    now: State<Instant> = mutableStateOf(Instant.now())
) {
    val project = projects[timeEntry.projectId]
    val zonedStart = ZonedDateTime.ofInstant(timeEntry.startTime, zoneId)

    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    var timeText = zonedStart.format(timeFormatter)
    timeEntry.endTime?.let { endTime ->
        val zonedEnd = ZonedDateTime.ofInstant(endTime, zoneId)
        if (!(zonedStart.hour <= 12).xor(zonedEnd.hour <= 12) &&
            zonedStart.toLocalDate().isEqual(zonedEnd.toLocalDate())
        ) {
            // Don't repeat am/pm twice
            timeText = zonedStart.format(DateTimeFormatter.ofPattern("hh:mm"))
        }
        timeText += " - ${zonedEnd.format(timeFormatter)}"
    } ?: run {
        timeText = stringResource(R.string.time_entry_started_time, timeText)
    }

    var endTime = timeEntry.endTime ?: now.value
    var durationText = endTime?.let {
        durationFormatter(
            duration = Duration.between(timeEntry.startTime, it),
            // show all components if ongoing
            numComponents = if (timeEntry.isOngoing) 10 else 2
        )
    }

    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .then(modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            val empty = timeEntry.description.trim().isEmpty()
            Text(
                text = if (empty) stringResource(R.string.no_description) else timeEntry.description,
                fontSize = 18.sp,
                fontStyle = if (empty) FontStyle.Italic else FontStyle.Normal,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (empty) Color.DarkGray else Color.Unspecified,
                modifier = Modifier.weight(1f)
            )
            durationText?.also {
                Text(
                    text = durationText,
                    maxLines = 1,
                    modifier = Modifier.width(IntrinsicSize.Max)
                        .padding(start = 6.dp)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            project?.let { project ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColoredDot(
                        color = project.colorCompose,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = project.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.weight(1f)
            ) {
                timeEntry.tagNames?.let {
                    Text(
                        text = it.joinToString { it },
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Text(
                text = timeText,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .width(IntrinsicSize.Max)
            )
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
