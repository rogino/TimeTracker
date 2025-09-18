package com.rioogino.timetracker.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration // Keep for potential future use or specific preview configs
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
// Removed ExperimentalMaterialApi for M2 ExposedDropdownMenuBox, M3 is used
// import androidx.compose.material.ExposedDropdownMenuDefaults // M2 - will be replaced by M3
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear // For TextFieldClearButton (from LoginView.kt)
import androidx.compose.material.icons.filled.Check // Added for SelectTagsDropdown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu // M3 DropdownMenu
import androidx.compose.material3.DropdownMenuItem // M3 DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox // M3 ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults // M3 for TrailingIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue // For TextFieldClearButton (from LoginView.kt)
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.rioogino.timetracker.*
import com.rioogino.timetracker.R
import com.rioogino.timetracker.model.*
import com.rioogino.timetracker.ui.theme.AppTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

// Removed: val newlineEtAlRegex = Regex("[\n\r\t]") - Defined in LoginView.kt
// Removed: @Composable fun TextFieldClearButton(...) - Defined in LoginView.kt

class EditEntryPageViewModel: ViewModel() {
    var isSaving by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryPage(
    model: GodModel,
    now: State<Instant> = mutableStateOf(Instant.now()),
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    allowEditing: Boolean = true,
    apiRequest: ApiRequest,
    context: Context,
    goBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    vm: EditEntryPageViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val entry = model.currentlyEditedEntry
    val canSave = entry.startTime != null && entry.startTime!!.isBefore(entry.endTime ?: now.value)
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun save(payload: TimeEntry) {
        vm.isSaving = true
        focusManager.clearFocus()
        makeRequestsShowingToastOnError(coroutineScope, context, { vm.isSaving = false }, {
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { save(entry.toTimeEntry()!!) },
                        enabled = canSave && allowEditing && !vm.isSaving,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            )
        },
    ) { scaffoldPaddingValues ->
        EditEntryView(
            entry = entry,
            lastEntryStopTime = model.lastEntryStopTime(),
            projects = model.projects,
            allTags = model.tags,
            now = now,
            zoneId = zoneId,
            allowEditing = allowEditing && !vm.isSaving,
            isSaving = vm.isSaving,
            isCurrentOrNewestTimeEntry = entry.isOngoing || model.timeEntries.firstOrNull()?.id == entry.id,
            stopAndSaveEntry = {
                save(it)
            },
            deleteEntry = { id ->
                if (vm.isSaving) return@EditEntryView
                vm.isSaving = true
                focusManager.clearFocus()
                makeRequestsShowingToastOnError(coroutineScope, context, { vm.isSaving = false }, {
                    apiRequest.deleteEntry(id, entry.workspaceId)
                    model.deleteEntry(id)
                    withContext(Dispatchers.Main) {
                        goBack()
                    }
                })
            },
            modifier = modifier
                .padding(contentPadding)
                .padding(scaffoldPaddingValues)
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
    isSaving: Boolean = false,
    isCurrentOrNewestTimeEntry: Boolean = false,
    stopAndSaveEntry: (TimeEntry) -> Unit = {},
    deleteEntry: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    val selectedRowColor = blendColors(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.surface,
        0.2f
    )
    Column(
        modifier = Modifier
            .verticalScroll(state = rememberScrollState(), enabled = true)
            .padding(horizontal = 16.dp)
            .then(modifier), 
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                if (it.contains(newlineEtAlRegex)) { // This will now use newlineEtAlRegex from LoginView.kt
                    focusManager.moveFocus(FocusDirection.Down)
                } else {
                    entry.description = it
                }
            },
            enabled = allowEditing,
            trailingIcon = {
                TextFieldClearButton( // This will now use TextFieldClearButton from LoginView.kt
                    textFieldValue = TextFieldValue(entry.description),
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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

@Deprecated("M2 Component, use M3 Button elevation parameters directly")
class UnelevatedButtonElevation : androidx.compose.material.ButtonElevation { 
    @Composable
    override fun elevation(enabled: Boolean, interactionSource: InteractionSource): State<Dp> {
        return remember { mutableStateOf(0.dp) }
    }
}

fun blendColors(c1: Color, c2: Color, t: Float): Color {
    val t_ = 1 - t
    return Color(
        red = c1.red * t + c2.red * t_,
        green = c1.green * t + c2.green * t_,
        blue = c1.blue * t + c2.blue * t_,
        alpha = c1.alpha * t + c2.alpha * t_
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectProjectDropdown(
    selectedProjectId: Long?,
    projectSelected: (Long?) -> Unit,
    projects: Map<Long, Project>,
    allowEditing: Boolean = true,
    selectedRowColor: Color,
    listOpen: MutableState<Boolean> = remember { mutableStateOf(false) }
) {
    val expanded = listOpen.value && allowEditing

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { listOpen.value = !expanded }, 
        modifier = Modifier.fillMaxWidth()
    ) {
        val projectsAndNull = remember(projects) { mutableListOf<Project?>(null).apply { addAll(projects.values.sortedBy { it.name }) } }
        val selectedProject = projects[selectedProjectId]

        OutlinedTextField(
            value = selectedProject?.name ?: stringResource(R.string.no_project_selected),
            onValueChange = {}, 
            readOnly = true,
            enabled = allowEditing,
            label = { Text(text = stringResource(R.string.project)) },
            trailingIcon = {
                 ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            leadingIcon = { 
                ColoredDot(project = selectedProject, modifier = Modifier.padding(start = 8.dp))
            },
            modifier = Modifier.menuAnchor()
                           .fillMaxWidth()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { listOpen.value = false },
        ) {
            projectsAndNull.forEach { project ->
                val isSelected = selectedProjectId == project?.id
                DropdownMenuItem(
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColoredDot(
                                project = project,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(text = project?.name ?: stringResource(R.string.no_project))
                        }
                    },
                    onClick = {
                        if (allowEditing) {
                            projectSelected(project?.id)
                        }
                        listOpen.value = false
                    },
                    modifier = Modifier.background(
                        color = if (isSelected) selectedRowColor else Color.Transparent
                    )
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectTagsDropdown(
    selectedTags: Collection<String>,
    tagToggled: (String, Boolean) -> Unit,
    allTags: Collection<String>,
    allowEditing: Boolean = true,
    selectedRowColor: Color,
    listOpen: MutableState<Boolean> = remember { mutableStateOf(false) }
) {
    val expanded = listOpen.value && allowEditing

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { listOpen.value = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        val sortedAllTags = remember(allTags) { allTags.sorted() }

        OutlinedTextField(
            value = if (selectedTags.isNotEmpty()) selectedTags.joinToString() else stringResource(R.string.no_tags_selected),
            onValueChange = {}, 
            readOnly = true,
            enabled = allowEditing,
            label = { Text(text = stringResource(R.string.tags)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
                           .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { listOpen.value = false },
        ) {
            sortedAllTags.forEach { tagName ->
                val isSelected = selectedTags.contains(tagName)
                DropdownMenuItem(
                    text = { Text(text = tagName) },
                    onClick = {
                        if (allowEditing) {
                            tagToggled(tagName, !isSelected)
                        }
                    },
                    modifier = Modifier.background(
                        color = if (isSelected) selectedRowColor else Color.Transparent
                    ),
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.selected)) }
                    } else null
                )
            }
        }
    }
}


@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, name = "EditEntryPage Light")
@Composable
fun EditEntryPage_Preview_Light() {
    AppTheme(useDarkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            val previewVm = EditEntryPageViewModel()
            val mock = remember { mockModel }
            EditEntryPage(
                model = mock, 
                apiRequest = ApiRequest(),
                context = LocalContext.current,
                goBack = {},
                contentPadding = PaddingValues(0.dp),
                vm = previewVm,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, name = "EditEntryPage Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun EditEntryPage_Preview_Dark() {
    AppTheme(useDarkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            val previewVm = EditEntryPageViewModel()
            val mock = remember { mockModel }
            EditEntryPage(
                model = mock,
                apiRequest = ApiRequest(),
                context = LocalContext.current,
                goBack = {},
                contentPadding = PaddingValues(0.dp),
                vm = previewVm,
                modifier = Modifier.padding(16.dp) 
            )
        }
    }
}

// Reminder: Ensure R.string.selected and R.string.clear_text_field are defined in strings.xml
