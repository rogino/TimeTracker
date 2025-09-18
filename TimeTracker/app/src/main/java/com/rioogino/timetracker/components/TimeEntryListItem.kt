package com.rioogino.timetracker.components // Changed package

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme // M3 Import
import androidx.compose.material3.Text // M3 Import
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember // Added remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// import androidx.compose.ui.graphics.Color // No longer needed for hardcoded gray
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
// import androidx.compose.ui.unit.sp // Replaced by M3 Typography
import com.rioogino.timetracker.R // Added R import
import com.rioogino.timetracker.durationFormatter // Added durationFormatter import
import com.rioogino.timetracker.model.Project
import com.rioogino.timetracker.model.TimeEntry
import com.rioogino.timetracker.model.mockModel
import com.rioogino.timetracker.ui.theme.AppTheme
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle // Added import

@Composable
fun TimeEntryListItem(
    modifier: Modifier = Modifier,
    timeEntry: TimeEntry,
    projects: Map<Long, Project>,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: State<Instant> = remember { mutableStateOf(Instant.now()) } // ensure remember
) {
    val project = projects[timeEntry.projectId]
    val zonedStart = ZonedDateTime.ofInstant(timeEntry.startTime, zoneId)

    val timeFormatter = remember(zoneId) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zoneId) }

    var timeText = zonedStart.format(timeFormatter)
    timeEntry.endTime?.let { endTime ->
        val zonedEnd = ZonedDateTime.ofInstant(endTime, zoneId)
        timeText += " - ${zonedEnd.format(timeFormatter)}"
    } ?: run {
        timeText = stringResource(R.string.time_entry_started_time, timeText)
    }

    val currentEndTime = timeEntry.endTime ?: now.value
    val durationText = durationFormatter(
        duration = Duration.between(timeEntry.startTime, currentEndTime),
        numComponents = if (timeEntry.isOngoing) 10 else 2
    )

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
                style = MaterialTheme.typography.bodyLarge, // M3 Typography
                fontStyle = if (empty) FontStyle.Italic else FontStyle.Normal,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, // M3 Color
                modifier = Modifier.weight(1f)
            )
            Text(
                text = durationText,
                style = MaterialTheme.typography.bodyMedium, // M3 Typography
                color = MaterialTheme.colorScheme.onSurfaceVariant, // M3 Color
                maxLines = 1,
                overflow = TextOverflow.Clip, // Ensure clipping
                modifier = Modifier
                    .padding(start = 8.dp) // Increased padding for separation
            )
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically, // Align items vertically
            modifier = Modifier.fillMaxWidth()
        ) {
            if (project != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f) // Allow project name to take space
                ) {
                    ColoredDot(
                        color = project.colorCompose, // Assuming colorCompose is Color
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.bodySmall, // M3 Typography
                        color = MaterialTheme.colorScheme.onSurface, // M3 Color
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false) // Allow shrinking but not aggressive expansion
                    )
                }
            } else {
                Spacer(Modifier.weight(1f)) // If no project, still take up space to align timeText to the right
            }

            val tags = timeEntry.tagNames // Store in local val for stable smart cast
            if (!tags.isNullOrEmpty()) { // Safe check for null or empty
                 Text(
                    text = tags.joinToString(", "), // 'tags' is smart-cast to non-null List<String> here
                    style = MaterialTheme.typography.bodySmall, // M3 Typography
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // M3 Color
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f) // Allow tags to take space
                        .padding(horizontal = 8.dp) // Padding for tags
                )
            } else if (project == null) { // If no tags (null or empty) AND no project
                 Spacer(Modifier.weight(1f))
            }

            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall, // M3 Typography
                color = MaterialTheme.colorScheme.onSurfaceVariant, // M3 Color
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier // Removed .width(IntrinsicSize.Min)
            )
        }
    }
}

@Preview(showBackground = true, name = "TimeEntryListItem Light")
@Composable
fun TimeEntryListItem_Preview_Light() {
    AppTheme(useDarkTheme = false) {
        Column(Modifier.padding(8.dp)) {
            TimeEntryListItem(
                timeEntry = mockModel.timeEntries.find { it.id == 12L }!!.copy(description = "This is a decently long description to test wrapping and ellipsis if it happens, hopefully not."),
                projects = mockModel.projects
            )
            Spacer(Modifier.height(8.dp))
            TimeEntryListItem(
                timeEntry = mockModel.timeEntries.find { it.id == 13L }!!.copy(tagNames = mutableListOf("meeting", "planning", "verylongtagname")),                
                projects = mockModel.projects
            )
            Spacer(Modifier.height(8.dp))
            mockModel.currentEntry?.also { timeEntry ->
                TimeEntryListItem(
                    timeEntry = timeEntry.copy(description = "Ongoing task with no specific description provided initially.", tagNames = null), // Added a case with null tagNames for preview
                    projects = mockModel.projects
                )
            }
            Spacer(Modifier.height(8.dp))
             TimeEntryListItem(
                timeEntry = TimeEntry(id=1, description = "Item with no project and no tags", startTime = Instant.now().minusSeconds(3600), endTime = Instant.now().minusSeconds(1800), tagNames = mutableListOf(), projectId = null),
                projects = mockModel.projects
            )
             Spacer(Modifier.height(8.dp))
             TimeEntryListItem(
                timeEntry = TimeEntry(id=2, description = "Item with null tags", startTime = Instant.now().minusSeconds(7200), endTime = Instant.now().minusSeconds(5400), tagNames = null, projectId = mockModel.projects.keys.firstOrNull()),
                projects = mockModel.projects
            )
        }
    }
}

@Preview(showBackground = true, name = "TimeEntryListItem Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TimeEntryListItem_Preview_Dark() {
    AppTheme(useDarkTheme = true) {
        Column(Modifier.padding(8.dp)) {
            TimeEntryListItem(
                timeEntry = mockModel.timeEntries.find { it.id == 12L }!!.copy(description = "This is a decently long description to test wrapping and ellipsis if it happens, hopefully not."),
                projects = mockModel.projects
            )
            Spacer(Modifier.height(8.dp))
             TimeEntryListItem(
                timeEntry = mockModel.timeEntries.find { it.id == 13L }!!.copy(tagNames = mutableListOf("meeting", "planning", "verylongtagname")),                
                projects = mockModel.projects
            )
            Spacer(Modifier.height(8.dp))
            mockModel.currentEntry?.also { timeEntry ->
                TimeEntryListItem(
                    timeEntry = timeEntry.copy(description = "Ongoing task with no specific description provided initially.", tagNames = null), // Added a case with null tagNames for preview
                    projects = mockModel.projects
                )
            }
            Spacer(Modifier.height(8.dp))
             TimeEntryListItem(
                timeEntry = TimeEntry(id=1, description = "Item with no project and no tags", startTime = Instant.now().minusSeconds(3600), endTime = Instant.now().minusSeconds(1800), tagNames = mutableListOf(), projectId = null),
                projects = mockModel.projects
            )
            Spacer(Modifier.height(8.dp))
            TimeEntryListItem(
                timeEntry = TimeEntry(id=2, description = "Item with null tags", startTime = Instant.now().minusSeconds(7200), endTime = Instant.now().minusSeconds(5400), tagNames = null, projectId = mockModel.projects.keys.firstOrNull()),
                projects = mockModel.projects
            )
        }
    }
}
