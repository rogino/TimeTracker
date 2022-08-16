package nz.ac.uclive.rog19.seng440.assignment1

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.insets.ui.TopAppBar
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
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
    entries: SnapshotStateList<TimeEntry>,
    projects: SnapshotStateMap<Long, Project>,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: State<Instant> = mutableStateOf(Instant.now()),
    apiRequest: ApiRequest? = null,
    setData: ((List<TimeEntry>, List<Project>) -> Unit)? = null,
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
        TimeEntryListView(
            modifier = modifier,
            entries = entries,
            projects = projects,
            zoneId = zoneId,
            now = now,
            apiRequest = apiRequest,
            setData = setData,
            editEntry = editEntry
        )
    }
}

@Composable
fun TimeEntryListView(
    modifier: Modifier = Modifier,
    entries: SnapshotStateList<TimeEntry>,
    projects: SnapshotStateMap<Long, Project>,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: State<Instant> = mutableStateOf(Instant.now()),
    apiRequest: ApiRequest? = null,
    setData: ((List<TimeEntry>, List<Project>) -> Unit)? = null,
    editEntry: ((TimeEntry?) -> Unit)? = null
) {
    var isRefreshing by remember { mutableStateOf<Boolean>(false) }
    var coroutineScope = rememberCoroutineScope()

    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
        onRefresh = {
            coroutineScope.launch(CoroutineExceptionHandler { _, exception ->
                Log.d(TAG, exception.message ?: exception.toString())
//                Toast.makeText(currentCompositionLocalContext,
//                        exception.message ?: exception.toString(),
//                    Toast.LENGTH_SHORT)
//                )
                isRefreshing = false
            }) {
                isRefreshing = true
                val entries = apiRequest?.getTimeEntries(
                    startDate = Instant.now().minusSeconds(60 * 60 * 24 * 7),
                    endDate = Instant.now().plusSeconds(60 * 60 * 24)
                )
                val projects = apiRequest?.getProjects()
                if (entries != null && projects != null) {
                    setData?.invoke(entries, projects)
                }
                isRefreshing = false
            }
        }) {
        LazyColumn(modifier = modifier) {
            // TODO somehow cache, or send in LocalDate as a state object: don't read now.value
            // in order to prevent unnecessary redraws
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
                        Text(
                            text = "Total ${durationFormatter(group.totalDuration)}",
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }

                itemsIndexed(group.entries) { index, entry ->
                    Divider()
                    // Passing null for `now` means only ongoing view gets re-rendered when date changes
                    TimeEntryListItem(
                        timeEntry = entry,
                        projects = projects,
                        zoneId = zoneId,
                        now = now,
                        modifier = Modifier.clickable {
                            editEntry?.invoke(entry)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                )
            }
        }
    }
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