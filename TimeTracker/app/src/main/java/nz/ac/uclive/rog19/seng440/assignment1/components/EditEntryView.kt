package nz.ac.uclive.rog19.seng440.assignment1.components

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.vanpra.composematerialdialogs.MaterialDialog
import com.vanpra.composematerialdialogs.datetime.date.datepicker
import com.vanpra.composematerialdialogs.datetime.time.timepicker
import com.vanpra.composematerialdialogs.rememberMaterialDialogState
import nz.ac.uclive.rog19.seng440.assignment1.model.Project
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntryObservable
import nz.ac.uclive.rog19.seng440.assignment1.model.mockModel
import nz.ac.uclive.rog19.seng440.assignment1.newlineEtAlRegex
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme
import java.text.Format
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun EditEntryPage(
    entry: TimeEntryObservable = remember { TimeEntryObservable() },
    lastEntryStopTime: Instant? = null,
    projects: Map<Long, Project>,
    allTags: Collection<String>,
    now: State<Instant> = mutableStateOf(Instant.now()),
    zoneId: ZoneId = Clock.systemDefaultZone().zone,
    saveAndExit: () -> Unit,
    cancelAndExit: () -> Unit,
) {
    val canSave = entry.startTime != null && entry.startTime!!.isBefore(entry.endTime ?: now.value)

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(
                    text = "${if (entry.id == null) "Create" else "Edit"} Time Entry"
                )
            },
            navigationIcon = {
                IconButton(onClick = { cancelAndExit() }) {
                    Icon(Icons.Filled.ArrowBack, "backIcon")
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        saveAndExit()
                    }, enabled = canSave,
                    colors = ButtonDefaults.buttonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.Unspecified.copy(alpha = 0.6f),
                        backgroundColor = Color.Transparent,
                        disabledBackgroundColor = Color.Transparent
                    )
                ) {
                    Text(text = "Save", style = MaterialTheme.typography.body1)
                }
            }
        )
    }) {
        EditEntryView(
            entry = entry,
            lastEntryStopTime = lastEntryStopTime,
            projects = projects,
            allTags = allTags,
            now = now,
            zoneId = zoneId,
            modifier = Modifier.padding(it)
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
    modifier: Modifier = Modifier
) {
    val padding = 12.dp
    Column(modifier = modifier) {
        val coroutineScope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        OutlinedTextField(
            value = entry.description,
            label = { Text(text = "Description") },
            onValueChange = {
                if (it.contains(newlineEtAlRegex)) {
                    focusManager.moveFocus(FocusDirection.Next)
                } else {
                    entry.description = it
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(padding))

        SelectProjectDropdown(
            selectedProjectId = entry.projectId,
            projectSelected = { entry.projectId = it },
            projects = projects
        )

        Spacer(modifier = Modifier.height(padding))

        SelectTagsDropdown(
            selectedTags = entry.tagNames,
            tagToggled = { tagName, add ->
                if (add) entry.tagNames.add(tagName)
                else entry.tagNames.remove(tagName)
            }, allTags = allTags
        )

        Spacer(modifier = Modifier.height(padding))

        SelectTimeView(
            label = "Start Time",
            date = entry.startTime,
            zoneId = zoneId,
            lastStopTime = lastEntryStopTime,
            unsetText = null,
            now = if (lastEntryStopTime != null) now.value else null,
            setDate = { entry.startTime = it }
        )

        Spacer(modifier = Modifier.height(padding))

        SelectTimeView(
            label = "End Time",
            date = entry.endTime,
            zoneId = zoneId,
            lastStopTime = null,
            unsetText = "Continue time entry",
            now = if (lastEntryStopTime != null) now.value else null,
            setDate = { entry.endTime = it }
        )
    }
}

class UnelevatedButttonElevation : ButtonElevation {
    @Composable
    override fun elevation(enabled: Boolean, interactionSource: InteractionSource): State<Dp> {
        return remember { mutableStateOf(0.dp) }
    }
}


@Composable
fun SelectTimeView(
    label: String,
    date: Instant?,
    zoneId: ZoneId,
    lastStopTime: Instant?,
    unsetText: String?,
    now: Instant?,
    setDate: (Instant?) -> Unit
) {
    val dateDialog = rememberMaterialDialogState()
    val timeDialog = rememberMaterialDialogState()
    val isExpanded = remember { MutableTransitionState(false) }

    val localDateTime = ZonedDateTime.ofInstant(date ?: now ?: Instant.now(), zoneId)
    val localDate = localDateTime.toLocalDate()
    val localTime = localDateTime.toLocalTime()

    val dateFormat: Format = DateFormat.getDateFormat(LocalContext.current)
    val datePattern: String = (dateFormat as SimpleDateFormat).toLocalizedPattern()
    val timePattern: String = if (date != null) "hh:mm a" else "hh:mm:ss a"
    val dateFormatter = DateTimeFormatter.ofPattern(datePattern)
    val timeFormatter = DateTimeFormatter.ofPattern(timePattern)

    val buttonColors: ButtonColors = ButtonDefaults.buttonColors(
        backgroundColor = MaterialTheme.colors.background,
        contentColor = Color.Unspecified
    )
    val expanded = isExpanded.targetState

    fun toggleExpand() {
        isExpanded.targetState = !isExpanded.targetState
    }

    Column(
        modifier = Modifier
            .background(
                MaterialTheme.colors.background,
                TextFieldDefaults.OutlinedTextFieldShape
            )
            .border(
                TextFieldDefaults.UnfocusedBorderThickness,
                Color.LightGray,
                TextFieldDefaults.OutlinedTextFieldShape
            )
    ) {
        Button(
            onClick = { toggleExpand() },
            colors = buttonColors,
            shape = RectangleShape,
            contentPadding = PaddingValues(0.dp),
            elevation = if (expanded) UnelevatedButttonElevation() else ButtonDefaults.elevation(),
            modifier = Modifier
                .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)

        ) {
            val buttonHorizontalPadding =
                ButtonDefaults.ContentPadding.calculateEndPadding(LayoutDirection.Ltr)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label, style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(start = buttonHorizontalPadding)
                )
                Row() {
                    Button(
                        onClick = { if (expanded) dateDialog.show() else toggleExpand() },
                        colors = buttonColors,
                        elevation = if (expanded) ButtonDefaults.elevation() else UnelevatedButttonElevation(),
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        var dateText = dateFormatter.format(localDateTime)
                        if (localDate.isEqual(LocalDate.now())) dateText = "Today"
                        else if (localDate.isEqual(LocalDate.now().minusDays(1))) dateText =
                            "Yesterday"
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.body1,
                        )
                    }

                    Button(
                        onClick = { if (expanded) timeDialog.show() else toggleExpand() },
                        colors = buttonColors,
                        elevation = if (expanded) ButtonDefaults.elevation() else UnelevatedButttonElevation(),
                        contentPadding = PaddingValues(start = 0.dp, end = buttonHorizontalPadding),
                        modifier = Modifier
                            .padding(end = buttonHorizontalPadding)
                            .width(120.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = timeFormatter.format(localDateTime),
                                style = MaterialTheme.typography.body1,
                            )
                        }

                    }
                }
            }
        }

        MaterialDialog(
            dialogState = dateDialog,
            buttons = {
                positiveButton("Ok")
                negativeButton("Cancel")
            }
        ) {
            datepicker(initialDate = localDate) { date ->
                val localDateTime = LocalDateTime.of(date, localTime)
                val instant = localDateTime.toInstant(zoneId.rules.getOffset(localDateTime))
                setDate(instant)
            }
        }

        MaterialDialog(
            dialogState = timeDialog,
            buttons = {
                positiveButton("Ok")
                negativeButton("Cancel")
            }
        ) {
            timepicker(initialTime = localTime) { time ->
                val localDateTime = LocalDateTime.of(localDate, time)
                val instant = localDateTime.toInstant(zoneId.rules.getOffset(localDateTime))
                setDate(instant)
            }
        }

        AnimatedVisibility(visibleState = isExpanded) {

            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                for (i in arrayOf<Long?>(-5, -1, 1, 5, null)) {
                    Button(
                        onClick = { setDate(if (i == null) Instant.now() else date!!.plusSeconds(60 * i)) },
                        colors = buttonColors,
                        enabled = i == null || date != null,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(
                                minWidth = ButtonDefaults.MinHeight,
                                minHeight = ButtonDefaults.MinHeight,
                            )
                            .padding(horizontal = 10.dp),
                    ) {
                        Text(text = if (i == null) "now" else (if (i > 0) "+$i" else "$i"))
                    }
                }
            }
        }

        // Needs to be in separate animation things for some reason
        AnimatedVisibility(visibleState = isExpanded) {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                if (lastStopTime != null) {
                    Button(
                        onClick = { setDate(lastStopTime) },
                        colors = buttonColors,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(text = "Last stop time")
                    }
                }
                if (unsetText != null) {
                    Button(
                        onClick = { setDate(null) },
                        colors = buttonColors,
                        enabled = date != null,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(text = unsetText)
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SelectProjectDropdown(
    selectedProjectId: Long?,
    projectSelected: (Long?) -> Unit,
    projects: Map<Long, Project>,
    listOpen: MutableState<Boolean> = remember { mutableStateOf(false) }
) {
    ExposedDropdownMenuBox(
        expanded = listOpen.value,
        onExpandedChange = { listOpen.value = it },
        modifier = Modifier
    ) {
//         https://stackoverflow.com/questions/67111020/exposed-drop-down-menu-for-jetpack-compose

        val projectsAndNull = mutableListOf<Project?>(null)
        projectsAndNull.addAll(projects.values.sortedBy { it.name })

        val selectedProject = if (selectedProjectId == null) null else projects[selectedProjectId!!]
        OutlinedTextField(
            readOnly = true,
            value = if (selectedProject == null) "[No project selected]" else (selectedProject?.name
                ?: "Unknown"),
            onValueChange = {},
            label = { Text(text = "Project") },
            trailingIcon = {
                ColoredDot(project = selectedProject)
            },
//                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectListOpen) },
            modifier = Modifier
                .fillMaxWidth()
        )
        // Not in the correct position in the Compose live preview
        DropdownMenu(
            expanded = listOpen.value,
            onDismissRequest = { listOpen.value = false },
            // https://stackoverflow.com/a/70683378
            modifier = Modifier.exposedDropdownSize()
        ) {
            projectsAndNull.forEach { project ->
                val isSelected = selectedProjectId == project?.id
                DropdownMenuItem(
                    onClick = {
                        projectSelected(project?.id)
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
                        Text(text = project?.name ?: "No project")
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
    listOpen: MutableState<Boolean> = remember { mutableStateOf(false) }
) {
    ExposedDropdownMenuBox(
        expanded = listOpen.value,
        onExpandedChange = { listOpen.value = it },
        modifier = Modifier
    ) {
//         https://stackoverflow.com/questions/67111020/exposed-drop-down-menu-for-jetpack-compose
        OutlinedTextField(
            readOnly = true,
            value = if (selectedTags.isNotEmpty()) selectedTags.joinToString { it } else "[No tags selected]",
            onValueChange = {},
            label = { Text(text = "Tags") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = listOpen.value) },
            modifier = Modifier
                .fillMaxWidth()
        )

        DropdownMenu(
            expanded = listOpen.value,
            onDismissRequest = { listOpen.value = false },
            // https://stackoverflow.com/a/70683378
            modifier = Modifier.exposedDropdownSize()
        ) {
            allTags.forEach { tagName ->
                val index = selectedTags.indexOf(tagName)
                val isSelected = index != -1
                DropdownMenuItem(
                    onClick = {
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
                projects = mockModel.projects,
                allTags = mockModel.tags,
                saveAndExit = {},
                cancelAndExit = {}
            )
        }
    }
}