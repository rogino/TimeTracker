package nz.ac.uclive.rog19.seng440.assignment1

import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import java.util.*

// https://developer.android.com/studio/write/java8-support-table



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimeTrackerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Greeting("Android")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

// https://api.track.toggl.com/api/v9/me/time_entries/current
// https://api.track.toggl.com/api/v9/me/time_entries
class TimeEntry(
    id: Int? = null,
    description: String = "",
    startTime: Instant,
    endTime: Instant? = null,
//    duration: Int = -1,
    projectId: Int? = null,
    tagNames: Array<String> = emptyArray()
) {
    var id: Int? = id

    var description: String = description

    /// start: String
    var startTime: Instant = startTime

    /// end: String
    var endTime: Instant? = endTime

    /// Duration in seconds
    /// -startTime.epoch for running timers
//    var duration: Int = duration

    val duration: Duration? get() {
        return Duration.between(startTime, if (endTime == null) Calendar.getInstance().toInstant() else endTime)
    }

    val isOngoing get() = endTime == null

    /// project_id: Int
    var projectId: Int? = projectId


    /// tags: [String]
    /// see also tag_ids: [Int]
    var tagNames: Array<String> = tagNames

    constructor(
        id: Int? = null,
        description: String = "",
        startTime: String,
        endTime: String? = null,
//        duration: Int = -1,
        projectId: Int? = null,
        tagNames: Array<String> = emptyArray()
    ) : this(
        id = id, description = description,
        startTime = Instant.parse(startTime.replace("+00:00", "Z")),
        endTime = if (endTime == null) null else Instant.parse(endTime.replace("+00:00", "Z")),
//        duration = duration,
        projectId = projectId, tagNames = tagNames
    )
}

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

    fun durationFormatter(duration: Duration): String {
        val components: Array<Pair<Int, String>> = arrayOf(
            Pair(60, "s"),
            Pair(60, "m"),
            Pair(24, "h"),
            Pair(7, "d")
        )

        var bla = false
        var count = 2
        var out = ""
        var remainder = duration.seconds.toInt()
        val unitized = components.map { (divisor, shortSuffix) ->
            val unitValue = remainder % divisor
            remainder = (remainder - unitValue) / divisor
            Pair(unitValue, shortSuffix)
        }

        for((unitValue, shortSuffix) in unitized.reversed()) {
            if (!bla && unitValue == 0) {
                continue
            }
            if (out.isNotEmpty()) {
                out += " "
            }
            out += "$unitValue$shortSuffix"

            count -= 1
            if (count <= 0) {
                break
            }
        }

        if (out.isEmpty()) {
            out = "Zero"
        }
        return out
    }

    var durationText = durationFormatter(Duration.between(timeEntry.startTime, timeEntry.endTime ?: now))

    Column() {
        Row(horizontalArrangement = Arrangement.SpaceAround) {
            Text(text = timeEntry.description)

            Text(zonedStart.format(DateTimeFormatter.ISO_LOCAL_DATE))
        }

        Row {
            project?.also { project ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(project.colorCompose) )
                    Text(text = project.name)
                }
            } ?: run {  }
//            Text(text = timeText)
            Text(text = durationText)
        }
    }

}

// https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/tags


// https://api.track.toggl.com/api/v9/workspaces/{workspace_id}/projects
class Project(id: Int, name: String, color: String) {
    val id: Int = id
    val name: String = name

    /// Hex color code, with '#'
    val color: String = color

    val colorCompose: Color get() = Color(android.graphics.Color.parseColor(color))
}


var mockProjects: Map<Int, Project> = mapOf(1 to Project(1, "Project Name", "#FF0000"))
var mockTimeEntry: TimeEntry = TimeEntry(10, "Entry description",
"2022-07-23T07:54:35+00:00", "2022-07-23T08:10:02Z" , 1,
 arrayOf("Tag1", "Another Tag"))




@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TimeTrackerTheme {
        TimeEntryListItem(timeEntry = mockTimeEntry, projects = mockProjects)
    }
}