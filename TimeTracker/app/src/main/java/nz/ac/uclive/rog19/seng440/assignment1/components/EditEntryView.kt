package nz.ac.uclive.rog19.seng440.assignment1.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.ui.TopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.ac.uclive.rog19.seng440.assignment1.*
import nz.ac.uclive.rog19.seng440.assignment1.R
import nz.ac.uclive.rog19.seng440.assignment1.model.*
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.AppTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Composable
fun EditEntryPage(
    model: GodModel,
    now: State<Instant> = mutableStateOf(Instant.now()),
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    allowEditing: Boolean = true,
    apiRequest: ApiRequest,
    goBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val entry = model.currentlyEditedEntry
    val canSave = entry.startTime != null && entry.startTime!!.isBefore(entry.endTime ?: now.value)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isSaving by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun save(payload: TimeEntry) {
        isSaving = true
        focusManager.clearFocus()
        makeRequestsShowingToastOnError(coroutineScope, context, { isSaving = false }, {
            val response = if (payload.id == null) {
                apiRequest.newTimeEntry(payload)
            } else {
                if (model.currentlyEditedEntrySaveState?.endTime != null && payload.endTime == null) {
                    val res =
                        apiRequest.updateTimeEntryByDeletingAndCreatingBecauseTogglV9ApiSucks(
                            payload
                        )
                    model.deleteEntry(payload.id!!)
                    res
                } else {
                    apiRequest.updateTimeEntry(payload)
                }
            }
            response?.let {
                entry.copyPropertiesFromEntry(response)
                model.currentlyEditedEntrySaveState = response
            }
            model.addOrUpdate(entry.toTimeEntry()!!)
            withContext(Dispatchers.Main) {
                goBack()
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(if (entry.id != null) R.string.edit_time_entry else R.string.create_time_entry)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        goBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { save(entry.toTimeEntry()!!) },
                        enabled = canSave && allowEditing && !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.Unspecified.copy(alpha = 0.6f),
                            backgroundColor = Color.Transparent,
                            disabledBackgroundColor = Color.Transparent
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            style = MaterialTheme.typography.body1
                        )
                    }
                },
                contentPadding = WindowInsets.statusBars.asPaddingValues()
            )
        },
    ) {
        EditEntryView(
            entry = entry,
            lastEntryStopTime = model.lastEntryStopTime(),
            projects = model.projects,
            allTags = model.tags,
            now = now,
            zoneId = zoneId,
            allowEditing = allowEditing && !isSaving,
            isSaving = isSaving,
            isCurrentOrNewestTimeEntry = entry.isOngoing || model.timeEntries.firstOrNull()?.id == entry.id,
            stopAndSaveEntry = {
                save(it)
            },
            deleteEntry = { id ->
                if (isSaving) return@EditEntryView
                isSaving = true
                focusManager.clearFocus()
                makeRequestsShowingToastOnError(coroutineScope, context, { isSaving = false }, {
                    apiRequest?.deleteEntry(id, entry.workspaceId)
                    model.deleteEntry(id)
                    withContext(Dispatchers.Main) {
                        goBack()
                    }
                })
            },
            modifier = modifier
                .padding(contentPadding)
                .padding(it)
                .padding(WindowInsets.ime.asPaddingValues())
        )
    }
}


@Composable
fun EditEntryView(
    entry: TimeEntryObservable = remember { TimeEntryObservable() },
    lastEntryStopTime: Instant? = null,
    projects: Map<Long, Project>,
    allTags: Collection<String>,
    now: State<Instant> = mutableStateOf(Instant.now()),
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    allowEditing: Boolean = true,
    isSaving: Boolean = true,
    isCurrentOrNewestTimeEntry: Boolean,
    stopAndSaveEntry: (TimeEntry) -> Unit,
    deleteEntry: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    val selectedRowColor = blendColors(
        MaterialTheme.colors.primary,
        MaterialTheme.colors.background,
        0.2f
    )
    Column(
        modifier = Modifier
            .verticalScroll(state = rememberScrollState(), enabled = true)
            .then(modifier), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .height(10.dp)
                .fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        var descriptionFocused by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = entry.description,
            label = { Text(text = stringResource(R.string.description)) },
            onValueChange = {
                if (it.contains(newlineEtAlRegex)) {
                    focusManager.moveFocus(FocusDirection.Down)
                } else {
                    entry.description = it
                }
            },
            enabled = allowEditing,
            trailingIcon = {
                TextFieldClearButton(
                    textFieldValue = entry.description,
                    clear = { entry.description = "" },
                    isFocused = descriptionFocused
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { descriptionFocused = it.hasFocus }
        )

        SelectProjectDropdown(
            selectedProjectId = entry.projectId,
            projectSelected = { entry.projectId = it },
            projects = projects,
            selectedRowColor = selectedRowColor,
            allowEditing = allowEditing
        )

        SelectTagsDropdown(
            selectedTags = entry.tagNames,
            tagToggled = { tagName, add ->
                if (add) entry.tagNames.add(tagName)
                else entry.tagNames.remove(tagName)
            },
            allTags = allTags,
            selectedRowColor = selectedRowColor,
            allowEditing = allowEditing
        )

        SelectTimeView(
            label = stringResource(R.string.start_time),
            date = entry.startTime,
            zoneId = zoneId,
            lastStopTime = lastEntryStopTime,
            unsetText = null,
            now = if (lastEntryStopTime != null) now.value else null,
            setDate = { entry.startTime = it },
            allowEditing = allowEditing,
        )

        SelectTimeView(
            label = stringResource(R.string.end_time),
            date = entry.endTime,
            zoneId = zoneId,
            lastStopTime = null,
            unsetText = if (isCurrentOrNewestTimeEntry) stringResource(R.string.continue_time_entry) else null,
            now = if (lastEntryStopTime != null) now.value else null,
            setDate = { entry.endTime = it },
            allowEditing = allowEditing,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (entry.id != null) {
                Button(
                    onClick = { deleteEntry(entry.id!!) },
                    enabled = allowEditing,
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                ) {
                    Text(stringResource(R.string.delete_time_entry))
                }
            }
            if (entry.id != null && isCurrentOrNewestTimeEntry && !entry.isOngoing) {
                Button(
                    onClick = {
                        stopAndSaveEntry(entry.toTimeEntry()!!.copy(endTime = null))
                    },
                    enabled = allowEditing,
                ) {
                    Text(stringResource(R.string.continue_time_entry))
                }
            }
            if (entry.isOngoing) {
                Button(
                    onClick = {
                        stopAndSaveEntry(entry.toTimeEntry()!!.copy(endTime = Instant.now()))
                    },
                    enabled = allowEditing,
                ) {
                    Text(stringResource(R.string.stop_time_entry))
                }
            }
        }

        Spacer(
            modifier = Modifier.padding(
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding()
            )
        )
    }
}

class UnelevatedButtonElevation : ButtonElevation {
    @Composable
    override fun elevation(enabled: Boolean, interactionSource: InteractionSource): State<Dp> {
        return remember { mutableStateOf(0.dp) }
    }
}

/// Linear blend of colors. Gamma? What's that?
fun blendColors(c1: Color, c2: Color, t: Float): Color {
    val t1 = 1 - t
    return Color(
        red = c1.red * t + c2.red * t1,
        green = c1.green * t + c2.green * t1,
        blue = c1.blue * t + c2.blue * t1,
        alpha = c1.alpha * t + c2.alpha * t1
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SelectProjectDropdown(
    selectedProjectId: Long?,
    projectSelected: (Long?) -> Unit,
    projects: Map<Long, Project>,
    allowEditing: Boolean = true,
    selectedRowColor: Color = Color.LightGray,
    listOpen: MutableState<Boolean> = remember { mutableStateOf(false) }
) {
    ExposedDropdownMenuBox(
        expanded = listOpen.value && allowEditing,
        onExpandedChange = { listOpen.value = it },
        modifier = Modifier
    ) {
//         https://stackoverflow.com/questions/67111020/exposed-drop-down-menu-for-jetpack-compose

        val projectsAndNull = mutableListOf<Project?>(null)
        projectsAndNull.addAll(projects.values.sortedBy { it.name })

        val selectedProject = if (selectedProjectId == null) null else projects[selectedProjectId!!]
        OutlinedTextField(
            readOnly = true,
            enabled = allowEditing,
            value = if (selectedProject == null) stringResource(R.string.no_project_selected)
            else (selectedProject?.name ?: stringResource(R.string.unknown)),
            onValueChange = {},
            label = { Text(text = stringResource(R.string.project)) },
            trailingIcon = {
                ColoredDot(project = selectedProject)
            },
            modifier = Modifier
                .fillMaxWidth()
        )
        // Not in the correct position in the Compose live preview
        DropdownMenu(
            expanded = listOpen.value && allowEditing,
            onDismissRequest = { listOpen.value = false },
            // https://stackoverflow.com/a/70683378
            modifier = Modifier.exposedDropdownSize()
        ) {
            projectsAndNull.forEach { project ->
                val isSelected = selectedProjectId == project?.id
                DropdownMenuItem(
                    onClick = {
                        if (allowEditing) {
                            projectSelected(project?.id)
                        }
                        listOpen.value = false
                    },
                    modifier = Modifier.background(
                        color = if (isSelected) selectedRowColor else Color.Unspecified
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColoredDot(
                            project = project,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(text = project?.name ?: stringResource(R.string.no_project))
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SelectTagsDropdown(
    selectedTags: Collection<String>,
    tagToggled: (String, Boolean) -> Unit,
    allTags: Collection<String>,
    allowEditing: Boolean = true,
    selectedRowColor: Color = Color.LightGray,
    listOpen: MutableState<Boolean> = remember { mutableStateOf(false) }
) {
    ExposedDropdownMenuBox(
        expanded = listOpen.value && allowEditing,
        onExpandedChange = { listOpen.value = it },
        modifier = Modifier
    ) {
//         https://stackoverflow.com/questions/67111020/exposed-drop-down-menu-for-jetpack-compose
        OutlinedTextField(
            readOnly = true,
            enabled = allowEditing,
            value = if (selectedTags.isNotEmpty()) selectedTags.joinToString { it }
            else stringResource(R.string.no_tags_selected),
            onValueChange = {},
            label = { Text(text = stringResource(R.string.tags)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = listOpen.value) },
            modifier = Modifier
                .fillMaxWidth()
        )

        DropdownMenu(
            expanded = listOpen.value && allowEditing,
            onDismissRequest = { listOpen.value = false },
            // https://stackoverflow.com/a/70683378
            modifier = Modifier.exposedDropdownSize()
        ) {
            allTags.forEach { tagName ->
                val index = selectedTags.indexOf(tagName)
                val isSelected = index != -1
                val backgroundColor = if (isSelected) selectedRowColor else Color.Unspecified
                DropdownMenuItem(
                    onClick = {
                        if (!allowEditing) return@DropdownMenuItem
                        if (isSelected) {
                            tagToggled(tagName, false)
                        } else {
                            tagToggled(tagName, true)
                        }
                    },
                    modifier = Modifier.background(
                        color = backgroundColor
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = tagName)
                    }
                }
            }
        }
    }
}


@Composable
@Preview(showBackground = true)
fun EditEntry_Preview() {
    AppTheme {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.height(600.dp)
        ) {
            EditEntryPage(
                model = mockModel,
                apiRequest = ApiRequest(),
                goBack = {},
            )
        }
    }
}