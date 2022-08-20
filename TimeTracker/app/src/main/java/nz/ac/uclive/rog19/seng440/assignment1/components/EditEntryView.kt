package nz.ac.uclive.rog19.seng440.assignment1.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.ui.TopAppBar
import kotlinx.coroutines.launch
import nz.ac.uclive.rog19.seng440.assignment1.ApiRequest
import nz.ac.uclive.rog19.seng440.assignment1.R
import nz.ac.uclive.rog19.seng440.assignment1.model.GodModel
import nz.ac.uclive.rog19.seng440.assignment1.model.Project
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntryObservable
import nz.ac.uclive.rog19.seng440.assignment1.model.mockModel
import nz.ac.uclive.rog19.seng440.assignment1.newlineEtAlRegex
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Composable
fun EditEntryPage(
    entry: TimeEntryObservable = remember { TimeEntryObservable() },
    lastEntryStopTime: Instant? = null,
    model: GodModel,
    now: State<Instant> = mutableStateOf(Instant.now()),
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    allowEditing: Boolean = true,
    apiRequest: ApiRequest,
    goBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSave = entry.startTime != null && entry.startTime!!.isBefore(entry.endTime ?: now.value)
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(if (entry.isOngoing) R.string.create_time_entry else R.string.edit_time_entry)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) {
                        Icon(Icons.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                val payload = entry.toTimeEntry()
                                if (payload == null) return@launch
                                isSaving = true
                                try {
                                    val response =
                                        if (entry.id != null) apiRequest.updateTimeEntry(payload)
                                        else apiRequest.newTimeEntry(payload)
                                    response?.let { entry.copyPropertiesFromEntry(response) }
                                    model.addOrUpdate(entry.toTimeEntry()!!)
                                } finally {
                                    isSaving = false
                                }
                                goBack()
                            }
                        }, enabled = canSave && allowEditing && !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.Unspecified.copy(alpha = 0.6f),
                            backgroundColor = Color.Transparent,
                            disabledBackgroundColor = Color.Transparent
                        )
                    ) {
                        Text(text = stringResource(R.string.save), style = MaterialTheme.typography.body1)
                    }
                },
                contentPadding = WindowInsets.statusBars.asPaddingValues()
            )
        },
    ) {
        val howDoIGetThisErrorToGoAway = it
        EditEntryView(
            entry = entry,
            lastEntryStopTime = lastEntryStopTime,
            projects = model.projects,
            allTags = model.tags,
            now = now,
            zoneId = zoneId,
            allowEditing = allowEditing,
            isCurrentOrNewestTimeEntry = entry.isOngoing || model.timeEntries.first().id == entry.id,
            modifier = modifier,
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
    isCurrentOrNewestTimeEntry: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val focusManager = LocalFocusManager.current

        OutlinedTextField(
            value = entry.description,
            label = { Text(text = stringResource(R.string.description)) },
            onValueChange = {
                if (it.contains(newlineEtAlRegex)) {
                    focusManager.moveFocus(FocusDirection.Next)
                } else {
                    entry.description = it
                }
            },
            enabled = allowEditing,
            modifier = Modifier.fillMaxWidth()
        )

        SelectProjectDropdown(
            selectedProjectId = entry.projectId,
            projectSelected = { entry.projectId = it },
            projects = projects,
            allowEditing = allowEditing
        )

        SelectTagsDropdown(
            selectedTags = entry.tagNames,
            tagToggled = { tagName, add ->
                if (add) entry.tagNames.add(tagName)
                else entry.tagNames.remove(tagName)
            }, allTags = allTags,
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
    }
}

class UnelevatedButtonElevation : ButtonElevation {
    @Composable
    override fun elevation(enabled: Boolean, interactionSource: InteractionSource): State<Dp> {
        return remember { mutableStateOf(0.dp) }
    }
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SelectProjectDropdown(
    selectedProjectId: Long?,
    projectSelected: (Long?) -> Unit,
    projects: Map<Long, Project>,
    allowEditing: Boolean = true,
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
                        color = if (isSelected) Color.LightGray else Color.Unspecified
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
                        color = if (isSelected) Color.LightGray else Color.Unspecified
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
    TimeTrackerTheme {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.height(600.dp)
        ) {
            EditEntryPage(
                entry = mockModel.currentEntry!!.toObservable(),
                model = mockModel,
                apiRequest = ApiRequest(),
                goBack = {},
            )
        }
    }
}