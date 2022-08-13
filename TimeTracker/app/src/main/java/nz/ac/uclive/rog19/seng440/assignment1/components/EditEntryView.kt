package nz.ac.uclive.rog19.seng440.assignment1.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nz.ac.uclive.rog19.seng440.assignment1.TAG
import nz.ac.uclive.rog19.seng440.assignment1.model.Project
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntry
import nz.ac.uclive.rog19.seng440.assignment1.model.mockModel
import nz.ac.uclive.rog19.seng440.assignment1.newlineEtAlRegex
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun EditEntryView(
    entry: TimeEntry? = null,
    isMostRecentEntry: Boolean = false,
    projects: Map<Long, Project>,
    allTags: Collection<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        var description by remember { mutableStateOf(TextFieldValue(entry?.description ?: "")) }
        var projectId: Long? by remember { mutableStateOf(null) }
        var projectListOpen by remember { mutableStateOf(false) }


        var selectedTags by remember {
            mutableStateOf(
                listOf(*(entry?.tagNames?.toTypedArray() ?: emptyArray()))
            )
        }
//        var selectedTags by remember { mutableStateOf(
//
//        ) }
        var tagListOpen by remember { mutableStateOf(false) }

        val coroutineScope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        val projectsAndNull = mutableListOf<Project?>(null)
        projectsAndNull.addAll(projects.values)

        TextField(
            value = description,
            label = { Text(text = "Description") },
            onValueChange = {
                if (it.text.contains(newlineEtAlRegex)) {
                    focusManager.moveFocus(FocusDirection.Next)
                } else {
                    description = it
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(
            expanded = projectListOpen,
            onExpandedChange = { projectListOpen = it },
            modifier = Modifier
        ) {
//         https://stackoverflow.com/questions/67111020/exposed-drop-down-menu-for-jetpack-compose
            val selectedProject = if (projectId == null) null else projects[projectId]
            TextField(
                readOnly = true,
                value = if (projectId == null) "[No project selected]" else (selectedProject?.name
                    ?: "Unknown"),
                onValueChange = {},
                label = { Text(text = "Project") },
                trailingIcon = {
                    ColoredDot(project = if (projectId != null) projects[projectId] else null)
                },
//                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectListOpen) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
            )
            // Not in the correct position in the Compose live preview
            DropdownMenu(
                expanded = projectListOpen,
                onDismissRequest = { projectListOpen = false },
                // https://stackoverflow.com/a/70683378
                modifier = Modifier.exposedDropdownSize()
            ) {
                projectsAndNull.forEach { project ->
                    val isSelected = projectId == project?.id
                    DropdownMenuItem(
                        onClick = {
                            projectId = project?.id
                            projectListOpen = false
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

        ExposedDropdownMenuBox(
            expanded = tagListOpen,
            onExpandedChange = { tagListOpen = it },
            modifier = Modifier
        ) {
//         https://stackoverflow.com/questions/67111020/exposed-drop-down-menu-for-jetpack-compose
            TextField(
                readOnly = true,
                value = entry?.tagNames?.joinToString { it } ?: "[No tags selected]",
                onValueChange = {},
                label = { Text(text = "Tags") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tagListOpen) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
            )

            DropdownMenu(
                expanded = tagListOpen,
                onDismissRequest = { tagListOpen = false },
                // https://stackoverflow.com/a/70683378
                modifier = Modifier.exposedDropdownSize()
            ) {
                allTags.forEach { tagName ->
                    val index = selectedTags.indexOf(tagName)
                    val isSelected = index != -1
                    DropdownMenuItem(
                        onClick = {
                            if (isSelected) {
                                selectedTags = selectedTags.filter{ it != tagName }
                                Log.d(TAG, selectedTags.joinToString { it })
                            } else {
                                selectedTags = selectedTags + tagName
                                Log.d(TAG, selectedTags.joinToString { it })
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
                entry = mockModel.currentEntry,
                projects = mockModel.projects,
                allTags = mockModel.tags
            )
        }
    }
}