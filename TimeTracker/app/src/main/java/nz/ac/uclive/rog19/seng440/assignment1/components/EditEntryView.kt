package nz.ac.uclive.rog19.seng440.assignment1.components

import android.util.Log
import android.widget.TimePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import nz.ac.uclive.rog19.seng440.assignment1.TAG
import nz.ac.uclive.rog19.seng440.assignment1.model.Project
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntryObservable
import nz.ac.uclive.rog19.seng440.assignment1.model.mockModel
import nz.ac.uclive.rog19.seng440.assignment1.newlineEtAlRegex
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme

@Composable
fun EditEntryView(
    entry: TimeEntryObservable = remember { TimeEntryObservable() },
    enableStartAtLastStopTime: Boolean = false,
    projects: Map<Long, Project>,
    allTags: Collection<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        var tagListOpen by remember { mutableStateOf(false) }

        val coroutineScope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        TextField(
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

        SelectProjectDropdown(
            selectedProjectId = entry.projectId,
            projectSelected = { entry.projectId = it },
            projects = projects
        )

        SelectTagsDropdown(
            selectedTags = entry.tagNames,
            tagToggled = { tagName, add ->
                if (add) entry.tagNames.add(tagName)
                else entry.tagNames.remove(tagName)
            }, allTags = allTags
        )
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
        TextField(
            readOnly = true,
            value = if (selectedProject == null) "[No project selected]" else (selectedProject?.name
                ?: "Unknown"),
            onValueChange = {},
            label = { Text(text = "Project") },
            trailingIcon = {
                ColoredDot(project = selectedProject)
            },
//                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectListOpen) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
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
        TextField(
            readOnly = true,
            value = if (selectedTags.isNotEmpty()) selectedTags.joinToString { it } else "[No tags selected]",
            onValueChange = {},
            label = { Text(text = "Tags") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = listOpen.value) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
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
            EditEntryView(
                entry = mockModel.currentEntry!!.toObservable(),
                projects = mockModel.projects,
                allTags = mockModel.tags
            )
        }
    }
}