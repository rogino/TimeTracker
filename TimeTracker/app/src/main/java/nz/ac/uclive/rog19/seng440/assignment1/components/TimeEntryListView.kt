package nz.ac.uclive.rog19.seng440.assignment1

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.insets.ui.TopAppBar
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.*
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

fun groupEntries(
    entries: List<TimeEntry>,
    zoneId: ZoneId,
    now: Instant,
    context: Context? = null
): List<TimeEntryPeriod> {
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
                name = context?.getString(R.string.today) ?: "TODAY"
            } else if (date.isEqual(nowDate.minusDays(1))) {
                name = context?.getString(R.string.yesterday) ?: "YESTERDAY"
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
            contentDescription = stringResource(R.string.more)
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
            title = { Text(text = stringResource(R.string.time_entries)) },
            actions = {
                OverflowMenu {
                    DropdownMenuItem(onClick = { logout?.invoke() }) {
                        Text(text = stringResource(R.string.logout))
                    }
                }
            },
            contentPadding = WindowInsets.statusBars.asPaddingValues()
        )
    }) {
        val howDoIGetThisErrorToGoAway = it

        var isRefreshing by remember { mutableStateOf(false) }
        var currentlyUpdatingEntry by remember { mutableStateOf(false) }
        var coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current

        Box(modifier = Modifier.fillMaxSize()) {
            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
                onRefresh = {
                    isRefreshing = true
                    makeRequestsShowingToastOnError(
                        coroutineScope,
                        context,
                        { isRefreshing = false },
                        {
                            apiRequest?.getTimeEntries(
                                startDate = Instant.now()
                                    .minusSeconds(60 * 60 * 24 * 7),
                                endDate = Instant.now().plusSeconds(60 * 60 * 24)
                            )?.let { model.addEntries(it) }
                        },
                        {
                            apiRequest?.getProjects()?.let {
                                model.setProjects(it)
                            }
                        },
                        {
                            apiRequest?.getStringTags()?.let {
                                model.tags.clear()
                                model.tags.addAll(it)
                            }
                        }
                    )
                }) {
                if (model.timeEntries.isNotEmpty()) {
                    TimeEntryListView(
                        modifier = modifier,
                        entries = model.timeEntries,
                        projects = model.projects,
                        entryCurrentlyOngoing = model.currentEntry != null,
                        lastEntryStopTime = lastEntryStopTime,
                        zoneId = zoneId,
                        now = now,
                        editEntry = editEntry,
                        stopEntry = { entry ->
                            currentlyUpdatingEntry = true
                            makeRequestsShowingToastOnError(
                                coroutineScope,
                                context,
                                { currentlyUpdatingEntry = false },
                                {
                                    apiRequest?.updateTimeEntry(entry.copy(endTime = Instant.now()))
                                        ?.let {
                                            model.addOrUpdate(it)
                                        }
                                }
                            )
                        },
                        resumeEntry = { entry ->
                            currentlyUpdatingEntry = true
                            makeRequestsShowingToastOnError(
                                coroutineScope,
                                context,
                                { currentlyUpdatingEntry = false },
                                {
                                    apiRequest?.updateTimeEntryByDeletingAndCreatingBecauseTogglV9ApiSucks(
                                        entry.copy(endTime = null)
                                    )?.let {
                                        model.deleteEntry(entry.id!!)
                                        model.addOrUpdate(it)
                                    }
                                }
                            )
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.no_entries),
                                    style = MaterialTheme.typography.h6
                                )
                            }
                        }
                    }
                }
            }
            if (currentlyUpdatingEntry) {
                Box(modifier = modifier
                    .fillMaxSize()
                    // prevent interaction with lower layer while loading
                    .clickable(
                        indication = null, // disable ripple effect
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { }
                    ),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colors.primary)
                }
            }
        }
    }
}

fun makeRequestsShowingToastOnError(
    coroutineScope: CoroutineScope,
    context: Context,
    onEnd: ((Boolean) -> Unit)?,
    vararg apiCalls: (suspend () -> Unit)
) {
    coroutineScope.launch {
        try {
            supervisorScope {
                withContext(Dispatchers.IO) {
                    apiCalls.map { async { it() } }.awaitAll()
                }
            }
            onEnd?.invoke(true)
        } catch (err: Throwable) {
            onEnd?.invoke(false)
            Log.d(TAG, err.stackTraceToString())
            Toast.makeText(
                context,
                err.message ?: err.toString(),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
fun TimeEntryListView(
    modifier: Modifier = Modifier,
    entries: SnapshotStateList<TimeEntry>,
    projects: SnapshotStateMap<Long, Project>,
    entryCurrentlyOngoing: Boolean,
    lastEntryStopTime: Instant? = null,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: State<Instant> = mutableStateOf(Instant.now()),
    editEntry: ((TimeEntry?) -> Unit)? = null,
    stopEntry: ((TimeEntry) -> Unit)? = null,
    resumeEntry: ((TimeEntry) -> Unit)? = null,
) {
    val context = LocalContext.current
    val firstItemId = entries?.firstOrNull()?.id

    LazyColumn(modifier = modifier) {
        // TODO somehow cache, or send in LocalDate as a state object: don't read now.value
        // (and don't use Instant.now()) in order to prevent unnecessary redraws
        groupEntries(entries, zoneId, Instant.now(), context).forEachIndexed { i, group ->
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
                        entryCurrentlyOngoing = entryCurrentlyOngoing,
                        isMostRecentEntry = entry.id == firstItemId,
                        expanded = dropdownOpen,
                        dismiss = { dropdownOpen = false },
                        editEntry = editEntry,
                        stopEntry = stopEntry,
                        resumeEntry = resumeEntry,
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
    /// The user already hsa an ongoing entry
    entryCurrentlyOngoing: Boolean,
    /// The entry is the most recent entry
    isMostRecentEntry: Boolean,
    dismiss: () -> Unit,
    editEntry: ((TimeEntry?) -> Unit)?,
    stopEntry: ((TimeEntry) -> Unit)?,
    resumeEntry: ((TimeEntry) -> Unit)?
) {
    fun editWithStartTime(startTime: Instant) {
        var copy = entry.copy(id = null, startTime = startTime, endTime = null)
        dismiss()
        editEntry?.invoke(copy)
    }

    DropdownMenu(expanded = expanded, onDismissRequest = dismiss) {
        if (!entryCurrentlyOngoing) {
            DropdownMenuItem(onClick = { editWithStartTime(startTime = Instant.now()) }) {
                Text(stringResource(R.string.start_time_entry_now))
            }
            lastEntryStopTime?.let {
                DropdownMenuItem(onClick = { editWithStartTime(startTime = it) }) {
                    Text(stringResource(R.string.start_time_entry_last_stop_time))
                }
            }
            // If most recent entry ended recently and there is no ongoing entry, allow the user to
            // resume the current entry
            if (isMostRecentEntry && !entry.isOngoing &&
                entry.endTime?.isAfter(Instant.now().minusSeconds(60 * 60)) == true
            ) {
                DropdownMenuItem(onClick = {
                    dismiss()
                    resumeEntry?.invoke(entry)
                }) {
                    Text(stringResource(R.string.continue_time_entry))
                }
            }
        }
        if (entry.isOngoing) {
            DropdownMenuItem(onClick = {
                dismiss()
                stopEntry?.invoke(entry)
            }) {
                Text(stringResource(R.string.stop_time_entry))
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
            entryCurrentlyOngoing = false
        )

    }
}