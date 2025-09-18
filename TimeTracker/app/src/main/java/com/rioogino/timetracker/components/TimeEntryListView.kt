package com.rioogino.timetracker.components // Corrected package name

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background // Added for Modifier.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState // Needed for OnBottomReached type
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi // Kept for pullRefresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface // Added for previews
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rioogino.timetracker.ApiRequest // Needed for preview
import com.rioogino.timetracker.R
import com.rioogino.timetracker.TAG // Assuming TAG is defined elsewhere, e.g. a const val
import com.rioogino.timetracker.OnBottomReached // Added import
import com.rioogino.timetracker.durationFormatter // Assuming defined elsewhere
import com.rioogino.timetracker.makeRequestsShowingToastOnError // Assuming defined elsewhere
import com.rioogino.timetracker.showErrorToast // Assuming defined elsewhere
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.rioogino.timetracker.model.GodModel
import com.rioogino.timetracker.model.Project
import com.rioogino.timetracker.model.TimeEntry
import com.rioogino.timetracker.model.mockModel
import com.rioogino.timetracker.ui.theme.AppTheme

class TimeEntryPeriod(
    val name: String,
    val entries: ArrayList<TimeEntry> = ArrayList<TimeEntry>()
) {
    val totalDuration: Duration
        get() {
            return entries.fold(Duration.ZERO) { total, entry: TimeEntry ->
                total + (entry.duration ?: Duration.ZERO)
            }
        }

    fun totalDuration(now: Instant): Duration {
        return entries.fold(Duration.ZERO) { total, entry: TimeEntry ->
            total + (entry.duration(now) ?: Duration.ZERO)
        }
    }

}

fun groupEntries(
    entries: List<TimeEntry>,
    zoneId: ZoneId,
    now: Instant,
    context: Context? = null
): List<TimeEntryPeriod> {
    val groups = ArrayList<TimeEntryPeriod>()
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
    currentPeriod?.let {
        if (it.entries.isNotEmpty()) {
            groups.add(it)
        }
    }

    return groups
}

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

// Helper function to format quota reset time
private fun formatQuotaResetTime(resetTimestamp: Instant?, now: Instant, context: Context, zoneId: ZoneId): String {
    if (resetTimestamp == null) {
        return context.getString(R.string.resets_unknown)
    }

    val durationUntilReset = Duration.between(now, resetTimestamp)

    if (durationUntilReset.isNegative || durationUntilReset.isZero) {
        return context.getString(R.string.quota_resets_now)
    }

    val totalSeconds = durationUntilReset.seconds

    return when {
        totalSeconds < 60 -> context.getString(R.string.quota_resets_in_seconds, totalSeconds)
        totalSeconds < 3600 -> { // Less than 1 hour
            val minutes = totalSeconds / 60
            context.getString(R.string.quota_resets_in_minutes, minutes)
        }
        totalSeconds < 6 * 3600 -> { // Less than 6 hours
            val hours = totalSeconds / 3600
            val remainingMinutes = (totalSeconds % 3600) / 60
            context.getString(R.string.quota_resets_in_hours_minutes, hours, remainingMinutes)
        }
        else -> {
            val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zoneId)
            context.getString(R.string.quota_resets_at_time, formatter.format(resetTimestamp))
        }
    }
}

class ListPageViewModel : ViewModel() {
    var isDownloading by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TimeEntryListPage(
    modifier: Modifier = Modifier,
    model: GodModel,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: State<Instant> = remember { mutableStateOf(Instant.now()) }, 
    apiRequest: ApiRequest? = null,
    context: Context, // Retained for other uses, localContext used for reset time string
    logout: (() -> Unit)? = null,
    goToEditEntryView: (() -> Unit)? = null,
    isDarkMode: Boolean? = null,
    setTheme: ((Boolean?) -> Unit)? = null,
    isRefreshing: MutableState<Boolean> = remember { mutableStateOf(false) }, 
    contentPadding: PaddingValues = PaddingValues(),
    vm: ListPageViewModel = viewModel(),
) {
    var currentlyUpdatingEntry by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val localContext = LocalContext.current // Use LocalContext for composable-specific context needs

    fun editEntry(entry: TimeEntry?) {
        model.currentlyEditedEntrySaveState = entry
        model.currentlyEditedEntry = (entry ?: TimeEntry()).toObservable()
        goToEditEntryView?.invoke()
    }

    fun stopEntry(entry: TimeEntry) {
        currentlyUpdatingEntry = true
        makeRequestsShowingToastOnError(
            coroutineScope,
            localContext, // Use localContext for toast
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
            localContext, // Use localContext for toast
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.time_entries)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    OverflowMenu {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.logout)) },
                            onClick = { logout?.invoke() }
                        )
                        if (isDarkMode != null) {
                             DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.use_system_theme)) },
                                onClick = { setTheme?.invoke(null) }
                            )
                        }
                        if (isDarkMode != true) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.use_dark_theme)) },
                                onClick = { setTheme?.invoke(true) }
                            )
                        }
                        if (isDarkMode != false) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.use_light_theme)) },
                                onClick = { setTheme?.invoke(false) }
                            )
                        }
                        Divider()
                        val remaining = model.apiQuotaRemaining
                        val resetTimestamp = model.apiQuotaResetTimestamp

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (remaining != null) stringResource(R.string.quota_remaining_format, remaining)
                                           else stringResource(R.string.quota_unknown)
                                )
                            },
                            onClick = {},
                            enabled = false
                        )
                        DropdownMenuItem(
                            text = {
                                Text(text = formatQuotaResetTime(resetTimestamp, now.value, localContext, zoneId))
                            },
                            onClick = {},
                            enabled = false
                        )
                    }
                }
            )
        },
        floatingActionButton = {
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
        },
        bottomBar = {
            val height = with(LocalDensity.current) { 100.dp.roundToPx() }
            AnimatedVisibility(visible = model.currentEntry != null,
                enter = slideInVertically { height } + fadeIn(),
                exit = slideOutVertically { height } + fadeOut()
            ) {
                val bottomPaddingForCurrentEntry = max(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    10.dp
                )
                var dropdownOpen by remember { mutableStateOf(false) }
                Column(modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            model.currentlyEditedEntrySaveState = model.currentEntry
                            model.currentlyEditedEntry =
                                (model.currentEntry ?: TimeEntry()).toObservable()
                            goToEditEntryView?.invoke()
                        },
                        onLongPress = { dropdownOpen = true }
                    )
                }
                ) {
                    Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    val entry = model.currentEntry ?: TimeEntry() 
                    TimeEntryListItem(
                        timeEntry = entry,
                        projects = model.projects,
                        now = now,
                        zoneId = zoneId,
                        modifier = Modifier
                            .padding(horizontal = 16.dp) 
                            .padding(bottom = bottomPaddingForCurrentEntry, top = 6.dp),
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
                        resumeEntry = ::resumeEntry
                    )
                }
            }
        }
    ) { scaffoldPaddingValues ->
        val pullRefreshState = rememberPullRefreshState(isRefreshing.value, {
            apiRequest?.let { currentApiRequest ->
                isRefreshing.value = true
                model.refreshEverything(
                    coroutineScope = coroutineScope,
                    apiRequest = currentApiRequest,
                    onEnd = {
                        isRefreshing.value = false
                        it?.let {
                            showErrorToast(context = localContext, error = it) // Use localContext
                        }
                    })
            }
        })
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPaddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            if (model.timeEntries.isNotEmpty()) {
                TimeEntryListView(
                    modifier = modifier,
                    entries = model.timeEntries,
                    projects = model.projects,
                    entryCurrentlyOngoing = model.currentEntry != null,
                    lastEntryStopTime = { model.lastEntryStopTime() },
                    zoneId = zoneId,
                    now = now,
                    contentPaddingForList = scaffoldPaddingValues, 
                    editEntry = ::editEntry,
                    stopEntry = ::stopEntry,
                    resumeEntry = ::resumeEntry,
                    loadMore = {
                        val oldest = model.timeEntries.lastOrNull()?.startTime
                        if (oldest == null || vm.isDownloading) {
                            return@TimeEntryListView
                        }
                        vm.isDownloading = true
                        makeRequestsShowingToastOnError(coroutineScope, localContext, { // Use localContext
                            vm.isDownloading = false
                        }, {
                            Log.d(TAG, "Downloading older entries, ${oldest}")
                            apiRequest?.getTimeEntries(
                                startDate = oldest.minus(java.time.Duration.ofDays(7)),
                                endDate = oldest.plus(java.time.Duration.ofDays(1))
                            )?.let { model.addEntries(it) }
                        })
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.no_entries),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
            PullRefreshIndicator(isRefreshing.value, pullRefreshState, Modifier.align(Alignment.TopCenter))
            if (currentlyUpdatingEntry || vm.isDownloading) { 
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)) 
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimeEntryListView(
    modifier: Modifier = Modifier,
    entries: SnapshotStateList<TimeEntry>,
    projects: SnapshotStateMap<Long, Project>,
    entryCurrentlyOngoing: Boolean,
    lastEntryStopTime: (() -> Instant?)? = null,
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    now: State<Instant> = remember { mutableStateOf(Instant.now()) }, 
    contentPaddingForList: PaddingValues = PaddingValues(),
    editEntry: ((TimeEntry?) -> Unit)? = null,
    stopEntry: ((TimeEntry) -> Unit)? = null,
    resumeEntry: ((TimeEntry) -> Unit)? = null,
    loadMore: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val firstItemId = entries.firstOrNull()?.id

    val listState: LazyListState = rememberLazyListState()
    listState.OnBottomReached {
        Log.d(TAG, "Load more")
        loadMore?.invoke()
    }
    LazyColumn(
        state = listState, 
        modifier = modifier,
        contentPadding = PaddingValues(bottom = contentPaddingForList.calculateBottomPadding()) 
    ) {
        groupEntries(entries, zoneId, now.value, context).forEachIndexed { i, group -> 
            stickyHeader { 
                Surface(tonalElevation = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant) { 
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium, 
                            modifier = Modifier.alignByBaseline()
                        )
                        GroupDurationText(
                            group = group,
                            now = now,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }
            }

            items(items = group.entries, key = { it.id ?: java.util.UUID.randomUUID().toString() }) { entry -> 
                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp) 
                Box(modifier = Modifier.padding(horizontal = 16.dp)) { 
                    var dropdownOpen by remember(entry.id) { mutableStateOf(false) }
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
    }
}

@Composable
fun TimeEntryListItemDropdownMenu(
    entry: TimeEntry,
    lastEntryStopTime: (() -> Instant?)? = null,
    expanded: Boolean,
    entryCurrentlyOngoing: Boolean,
    isMostRecentEntry: Boolean,
    dismiss: () -> Unit,
    editEntry: ((TimeEntry?) -> Unit)?,
    stopEntry: ((TimeEntry) -> Unit)?,
    resumeEntry: ((TimeEntry) -> Unit)?
) {
    fun editWithStartTime(startTime: Instant) {
        val copy = entry.copy(id = null, startTime = startTime, endTime = null)
        dismiss()
        editEntry?.invoke(copy)
    }

    DropdownMenu(expanded = expanded, onDismissRequest = dismiss) {
        if (!entryCurrentlyOngoing) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.start_time_entry_now)) },
                onClick = { editWithStartTime(startTime = Instant.now()) }
            )
            lastEntryStopTime?.invoke()?.let {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.start_time_entry_last_stop_time)) },
                    onClick = { editWithStartTime(startTime = it) }
                )
            }
            if (isMostRecentEntry && !entry.isOngoing &&
                entry.endTime?.isAfter(Instant.now().minusSeconds(60 * 60)) == true
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.continue_time_entry)) },
                    onClick = {
                        dismiss()
                        resumeEntry?.invoke(entry)
                    }
                )
            }
        }
        if (entry.isOngoing) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stop_time_entry)) },
                onClick = {
                    dismiss()
                    stopEntry?.invoke(entry)
                }
            )
        }
    }
}

@Composable
fun GroupDurationText(
    group: TimeEntryPeriod,
    now: State<Instant> = remember { mutableStateOf(Instant.now()) }, 
    modifier: Modifier = Modifier
) {
    val (totalDuration, numComponents) = if (group.entries.isNotEmpty() && group.entries.first().isOngoing) {
        Pair(group.totalDuration(now.value), 10)
    } else {
        Pair(group.totalDuration, 2)
    }
    Text(
        text = stringResource(
            id = R.string.total_group_duration,
            durationFormatter(totalDuration, numComponents = numComponents)
        ),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}


@Preview(showBackground = true, name = "TimeEntryListView Light")
@Composable
fun TimeEntryListView_Preview_Light() {
    AppTheme(useDarkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            TimeEntryListView(
                entries = mockModel.timeEntries,
                projects = mockModel.projects,
                entryCurrentlyOngoing = false,
                contentPaddingForList = PaddingValues(bottom = 56.dp) 
            )
        }
    }
}

@Preview(showBackground = true, name = "TimeEntryListView Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TimeEntryListView_Preview_Dark() {
    AppTheme(useDarkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            TimeEntryListView(
                entries = mockModel.timeEntries,
                projects = mockModel.projects,
                entryCurrentlyOngoing = false,
                contentPaddingForList = PaddingValues(bottom = 56.dp)
            )
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, name = "TimeEntryListPage Light")
@Composable
fun TimeEntryListPage_Preview_Light() {
    AppTheme(useDarkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            mockModel.onQuotaUpdated(25, Instant.now().plusSeconds(3500L)) 
            TimeEntryListPage(
                model = mockModel,
                apiRequest = ApiRequest(),
                context = LocalContext.current,
                contentPadding = PaddingValues(0.dp), 
                vm = ListPageViewModel()
            )
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, name = "TimeEntryListPage Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TimeEntryListPage_Preview_Dark() {
    AppTheme(useDarkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            mockModel.onQuotaUpdated(5, Instant.now().plusSeconds(120L))
            TimeEntryListPage(
                model = mockModel,
                apiRequest = ApiRequest(),
                context = LocalContext.current,
                contentPadding = PaddingValues(0.dp),
                vm = ListPageViewModel()
            )
        }
    }
}
