package nz.ac.uclive.rog19.seng440.assignment1

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.google.accompanist.insets.ui.TopAppBar
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.*
import nz.ac.uclive.rog19.seng440.assignment1.model.*
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.AppTheme
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class TimeEntryPeriod(
    val name: String,
    val entries: ArrayList<TimeEntry> = ArrayList<TimeEntry>()
) {
    val totalDuration: Duration
        get() {
            return entries.fold(Duration.ZERO) { total, entry: TimeEntry ->
                total + entry.duration
            }
        }

    fun totalDuration(now: Instant): Duration {
        return entries.fold(Duration.ZERO) { total, entry: TimeEntry ->
            total + entry.duration(now)
        }
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
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: State<Instant> = mutableStateOf(Instant.now()),
    apiRequest: ApiRequest? = null,
    context: Context,
    logout: (() -> Unit)? = null,
    goToEditEntryView: (() -> Unit)? = null,
    isDarkMode: Boolean? = null,
    setTheme: ((Boolean?) -> Unit)? = null,
    isRefreshing: MutableState<Boolean> = mutableStateOf(false),
    contentPadding: PaddingValues = PaddingValues()
) {
    // Currently refreshing
    var currentlyUpdatingEntry by remember { mutableStateOf(false) }
    var coroutineScope = rememberCoroutineScope()

    fun editEntry(entry: TimeEntry?) {
        model.currentlyEditedEntrySaveState = entry
        model.currentlyEditedEntry = (entry ?: TimeEntry()).toObservable()
        goToEditEntryView?.invoke()
    }
    fun stopEntry(entry: TimeEntry) {
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
    }
    fun resumeEntry(entry: TimeEntry) {
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

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(text = stringResource(R.string.time_entries)) },
            actions = {
                OverflowMenu {
                    DropdownMenuItem(onClick = { logout?.invoke() }) {
                        Text(text = stringResource(R.string.logout))
                    }
                    if (isDarkMode != null) {
                        DropdownMenuItem(onClick = { setTheme?.invoke(null) }) {
                            Text(text = stringResource(R.string.use_system_theme))
                        }
                    }
                    if (isDarkMode != true) {
                        DropdownMenuItem(onClick = { setTheme?.invoke(true) }) {
                            Text(text = stringResource(R.string.use_dark_theme))
                        }
                    }
                    if (isDarkMode != false) {
                        DropdownMenuItem(onClick = { setTheme?.invoke(false) }) {
                            Text(text = stringResource(R.string.use_light_theme))
                        }
                    }
                }
            },
            contentPadding = WindowInsets.statusBars.asPaddingValues()
        )
    }, floatingActionButton = {
        val width = with(LocalDensity.current) { 100.dp.roundToPx() }
        AnimatedVisibility(visible = model.currentEntry == null,
            enter = slideInHorizontally { width } + fadeIn(),
            exit = slideOutHorizontally { width } + fadeOut()
        ) {
            FloatingActionButton(onClick = {
                model.currentlyEditedEntrySaveState = null
                model.currentlyEditedEntry = TimeEntry(startTime = Instant.now()).toObservable()
                goToEditEntryView?.invoke()
            }) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.create_time_entry)
                )
            }
        }
    }, bottomBar = {
        val height = with(LocalDensity.current) { 100.dp.roundToPx() }
        AnimatedVisibility(visible = model.currentEntry != null,
        enter = slideInVertically { height } + fadeIn(),
        exit = slideOutVertically { height } + fadeOut()
        ) {
            val bottomPadding = max(
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                10.dp
            )
            var dropdownOpen by remember { mutableStateOf(false) }
            Column(modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        model.currentlyEditedEntrySaveState = model.currentEntry
                        model.currentlyEditedEntry = (model.currentEntry ?: TimeEntry()).toObservable()
                        goToEditEntryView?.invoke()
                    },
                    onLongPress = { dropdownOpen = true }
                )
            }
            ) {
                Divider(color = MaterialTheme.colors.secondary, thickness = 2.dp)
                val entry = model.currentEntry ?: TimeEntry()
                TimeEntryListItem(
                    timeEntry = entry,
                    projects = model.projects,
                    now = now,
                    zoneId = zoneId,
                    modifier = Modifier
                        .padding(horizontal = contentPadding.horizontal)
                        .padding(bottom = bottomPadding, top = 6.dp),
                )
                TimeEntryListItemDropdownMenu(
                    entry = entry,
                    lastEntryStopTime = { model.lastEntryStopTime() },
                    entryCurrentlyOngoing = entry.isOngoing,
                    isMostRecentEntry = true,
                    expanded = dropdownOpen,
                    dismiss = { dropdownOpen = false },
                    editEntry = ::editEntry,
                    stopEntry = ::stopEntry,
                    resumeEntry = ::resumeEntry,
                )
            }
        }
    }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = it.calculateBottomPadding())
        ) {
            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing = isRefreshing.value),
                onRefresh = {
                    apiRequest?.let { apiRequest ->
                        isRefreshing.value = true
                        model.refreshEverything(coroutineScope = coroutineScope, apiRequest = apiRequest, onEnd = {
                            isRefreshing.value = false
                            it?.let {
                                showErrorToast(context = context, error = it)
                            }
                        })
                    }
                },
                indicator = { state, trigger ->
                    SwipeRefreshIndicator(
                        state = state,
                        refreshTriggerDistance = trigger,
                        scale = true,
                        contentColor = MaterialTheme.colors.secondary,
                        backgroundColor = MaterialTheme.colors.background,
                    )
                }) {
                if (model.timeEntries.isNotEmpty()) {
                    TimeEntryListView(
                        modifier = modifier,
                        entries = model.timeEntries,
                        projects = model.projects,
                        entryCurrentlyOngoing = model.currentEntry != null,
                        lastEntryStopTime = { model.lastEntryStopTime() },
                        zoneId = zoneId,
                        now = now,
                        contentPadding = contentPadding,
                        editEntry = ::editEntry,
                        stopEntry = ::stopEntry,
                        resumeEntry = ::resumeEntry
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

@Composable
fun TimeEntryListView(
    modifier: Modifier = Modifier,
    entries: SnapshotStateList<TimeEntry>,
    projects: SnapshotStateMap<Long, Project>,
    entryCurrentlyOngoing: Boolean,
    lastEntryStopTime: (() -> Instant?)? = null,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: State<Instant> = mutableStateOf(Instant.now()),
    contentPadding: PaddingValues = PaddingValues(),
    editEntry: ((TimeEntry?) -> Unit)? = null,
    stopEntry: ((TimeEntry) -> Unit)? = null,
    resumeEntry: ((TimeEntry) -> Unit)? = null,
) {
    val context = LocalContext.current
    val firstItemId = entries.firstOrNull()?.id

    LazyColumn(modifier = modifier) {
        // TODO somehow cache, or send in LocalDate as a state object: don't read now.value
        // (and don't use Instant.now()) in order to prevent unnecessary redraws
        groupEntries(entries, zoneId, Instant.now(), context).forEachIndexed { i, group ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (i == 0) contentPadding.top else 14.dp, bottom = 6.dp)
                        .padding(horizontal = contentPadding.horizontal),
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
                Box(modifier = Modifier.padding(horizontal = contentPadding.horizontal)) {
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
    lastEntryStopTime: (() -> Instant?)? = null,
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
            lastEntryStopTime?.invoke()?.let {
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
        text = stringResource(
            id = R.string.total_group_duration,
            durationFormatter(totalDuration, numComponents = numComponents)
        ),
        modifier = modifier
    )
}


@Preview(showBackground = true)
@Composable
fun TimeEntryListView_Preview() {
    AppTheme {
        TimeEntryListView(
            entries = mockModel.timeEntries,
            projects = mockModel.projects,
            entryCurrentlyOngoing = false
        )

    }
}