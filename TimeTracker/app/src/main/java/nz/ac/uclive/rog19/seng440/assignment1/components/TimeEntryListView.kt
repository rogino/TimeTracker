package nz.ac.uclive.rog19.seng440.assignment1

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.insets.ui.TopAppBar
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nz.ac.uclive.rog19.seng440.assignment1.model.GodModel
import nz.ac.uclive.rog19.seng440.assignment1.model.Project
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntry
import nz.ac.uclive.rog19.seng440.assignment1.model.mockModel
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class TimeEntryPeriod(
    val name: String,
    val entries: ArrayList<TimeEntry> = ArrayList<TimeEntry>()
) {
    val totalDuration: Duration
        get() {
            return entries.fold(Duration.ZERO) { total, entry: TimeEntry -> total + entry.duration }
        }

    fun totalDuration(now: Instant): Duration {
        return entries.fold(Duration.ZERO) { total, entry: TimeEntry -> total + entry.duration(now) }
    }

}

fun groupEntries(entries: List<TimeEntry>, zoneId: ZoneId, now: Instant): List<TimeEntryPeriod> {
    var groups = ArrayList<TimeEntryPeriod>()
    val nowDate: LocalDate = ZonedDateTime.ofInstant(now, zoneId).toLocalDate()

    var previousDate: LocalDate? = null
    var currentPeriod: TimeEntryPeriod? = null

    entries.forEach { entry ->
        val date = entry.startTime.atZone(zoneId).toLocalDate()

        if (previousDate == null || !date.isEqual(previousDate)) {
            currentPeriod?.let {
                if (it.entries.isNotEmpty()) {
                    groups.add(it)
                }
            }

            var name: String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            if (date.isEqual(nowDate)) {
                name = "Today"
            } else if (date.isEqual(nowDate.minusDays(1))) {
                name = "Yesterday"
            }
            currentPeriod = TimeEntryPeriod(name)
        }

        currentPeriod?.entries?.add(entry)
        previousDate = date
    }

    return groups
}

//https://stackoverflow.com/a/68354402
@Composable
fun OverflowMenu(content: @Composable () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    IconButton(onClick = {
        showMenu = !showMenu
    }) {
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = "More" // stringResource(R.string.more),
        )
    }
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        content()
    }
}

@Composable
fun TimeEntryListPage(
    modifier: Modifier = Modifier,
    model: GodModel,
    lastEntryStopTime: Instant? = null,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: State<Instant> = mutableStateOf(Instant.now()),
    apiRequest: ApiRequest? = null,
    logout: (() -> Unit)? = null,
    editEntry: ((TimeEntry?) -> Unit)? = null
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(text = "Time Entries") },
            actions = {
                OverflowMenu {
                    DropdownMenuItem(onClick = { logout?.invoke() }) {
                        Text(text = "Logout")
                    }
                }
            },
            contentPadding = WindowInsets.statusBars.asPaddingValues()
        )
    }) {
        val howDoIGetThisErrorToGoAway = it

        var isRefreshing by remember { mutableStateOf<Boolean>(false) }
        var coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current

        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    makeRequestShowingToastOnError(context, { isRefreshing = false }) {
                        async {
                            apiRequest?.getTimeEntries(
                                startDate = Instant.now().minusSeconds(60 * 60 * 24 * 7),
                                endDate = Instant.now().plusSeconds(60 * 60 * 24)
                            )?.let { model.addEntries(it) }
                        }
                        async {
                            apiRequest?.getProjects()?.let {
                                model.setProjects(it)
                            }
                        }
                        async {
                            apiRequest?.getTags()?.let {
                                model.tags.clear()
                                model.tags.addAll(it)
                            }
                        }
                    }
                }
            }) {
            TimeEntryListView(
                modifier = modifier,
                entries = model.timeEntries,
                projects = model.projects,
                lastEntryStopTime = lastEntryStopTime,
                zoneId = zoneId,
                now = now,
                editEntry = editEntry
            )
        }
    }
}

suspend fun <T> makeRequestShowingToastOnError(
    context: Context,
    onEnd: (() -> Unit)?,
    apiCall: (suspend () -> T)
): T? {
    val result = withContext(Dispatchers.IO) {
        try {
            Result.success(apiCall())
        } catch (exception: Throwable) {
            Result.failure(exception)
        } finally {
            onEnd?.invoke()
        }
    }
        .onFailure {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    it.message ?: it.toString(),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    return result.getOrNull()
}

@Composable
fun TimeEntryListView(
    modifier: Modifier = Modifier,
    entries: SnapshotStateList<TimeEntry>,
    projects: SnapshotStateMap<Long, Project>,
    lastEntryStopTime: Instant? = null,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: State<Instant> = mutableStateOf(Instant.now()),
    editEntry: ((TimeEntry?) -> Unit)? = null
) {
    LazyColumn(modifier = modifier) {
        // TODO somehow cache, or send in LocalDate as a state object: don't read now.value
        // (and don't use Instant.now()) in order to prevent unnecessary redraws
        groupEntries(entries, zoneId, Instant.now()).forEachIndexed { i, group ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (i == 0) 0.dp else 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = group.name,
                        fontSize = 24.sp,
                        modifier = Modifier.alignByBaseline()
                    )
                    GroupDurationText(
                        group = group,
                        now = now,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }

            // should only ever be one entry without an ID
            items(items = group.entries, key = { it.id ?: -1000 }) { entry ->
                Divider()
                Box {
                    var dropdownOpen by remember(entry.id ?: -1000) { mutableStateOf(false) }
                    TimeEntryListItem(
                        timeEntry = entry,
                        projects = projects,
                        zoneId = zoneId,
                        now = now,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { editEntry?.invoke(entry) },
                                onLongPress = { dropdownOpen = true }
                            )
                        }
                    )
                    TimeEntryListItemDropdownMenu(
                        entry = entry,
                        lastEntryStopTime = lastEntryStopTime,
                        expanded = dropdownOpen,
                        dismiss = { dropdownOpen = false },
                        editEntry = editEntry
                    )
                }
            }
        }

        item {
            Spacer(
                modifier = Modifier.padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding()
                )
            )
        }
    }
}

@Composable
fun TimeEntryListItemDropdownMenu(
    entry: TimeEntry,
    lastEntryStopTime: Instant?,
    expanded: Boolean,
    dismiss: () -> Unit,
    editEntry: ((TimeEntry?) -> Unit)?
) {
    fun editWithStartTime(startTime: Instant) {
        var copy = entry.copy()
        copy.id = null
        copy.startTime = startTime
        copy.endTime = null
        editEntry?.invoke(copy)
    }

    DropdownMenu(expanded = expanded, onDismissRequest = dismiss) {
        DropdownMenuItem(onClick = { editWithStartTime(startTime = Instant.now()) }) {
            Text("Start")
        }
        lastEntryStopTime?.let {
            DropdownMenuItem(onClick = { editWithStartTime(startTime = it) }) {
                Text("Start at last stop time")
            }
        }
        if (entry.isOngoing) {
            DropdownMenuItem(onClick = { Log.d(TAG, "TODO") }) {
                Text("Stop entry")
            }
        }
    }
}

@Composable
fun GroupDurationText(
    group: TimeEntryPeriod,
    now: State<Instant> = mutableStateOf(Instant.now()),
    modifier: Modifier = Modifier
) {
    val (totalDuration, numComponents) = if (group.entries.first().isOngoing) {
        Pair(group.totalDuration(now.value), 10)
    } else {
        Pair(group.totalDuration, 2)
    }
    Text(
        text = "Total ${durationFormatter(totalDuration, numComponents = numComponents)}",
        modifier = modifier
    )
}


@Preview(showBackground = true)
@Composable
fun TimeEntryListView_Preview() {
    TimeTrackerTheme {
        TimeEntryListView(
            entries = mockModel.timeEntries,
            projects = mockModel.projects,
        )

    }
}