package nz.ac.uclive.rog19.seng440.assignment1

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import nz.ac.uclive.rog19.seng440.assignment1.components.EditEntryPage
import nz.ac.uclive.rog19.seng440.assignment1.components.EditEntryView
import nz.ac.uclive.rog19.seng440.assignment1.components.LoginView
import nz.ac.uclive.rog19.seng440.assignment1.model.GodModel
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntryObservable
import nz.ac.uclive.rog19.seng440.assignment1.model.getTagsFromEntries
import nz.ac.uclive.rog19.seng440.assignment1.model.mockModel
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme
import java.time.Instant
import kotlin.concurrent.fixedRateTimer

// https://developer.android.com/studio/write/java8-support-table

val timeTrackerPreferencesFileName = "main"

class MainActivity : ComponentActivity() {
    private lateinit var model: GodModel
    private lateinit var handler: Handler
    private lateinit var updateTask: Runnable
    private lateinit var apiRequest: ApiRequest
    private var now = mutableStateOf(Instant.now())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = mockModel
        apiRequest = ApiRequest()

        val preferences = getSharedPreferences(timeTrackerPreferencesFileName, Context.MODE_PRIVATE)
        var currentlyEditedEntry: TimeEntryObservable = TimeEntryObservable()

        val key = preferences.getString("API_KEY", null)
        val workspaceId = preferences.getInt("DEFAULT_WORKSPACE_ID", -1)
        if (key != null && workspaceId > 0) {
            apiRequest.apiKey = key
            apiRequest.workspaceId = workspaceId
        }

        handler = Handler(Looper.getMainLooper())
        updateTask = Runnable {
            now.value = Instant.now()
            handler.postDelayed(updateTask, 1000)
        }

        var startDestination = "login"
        if (apiRequest.authenticated) {
            startDestination = "entries"
        }


        setContent {
            val navController = rememberNavController()
            TimeTrackerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    NavHost(navController, startDestination = startDestination) {
                        composable("login") {
                            LoginView(
                                apiRequest = apiRequest
                            ) {
                                with(
                                    getSharedPreferences(
                                        timeTrackerPreferencesFileName,
                                        Context.MODE_PRIVATE
                                    ).edit()
                                ) {
                                    putString("API_KEY", it.apiToken)
                                    putInt("DEFAULT_WORKSPACE_ID", it.defaultWorkspaceId)
                                    commit()
                                }
                                navController.navigate("entries") {
                                    popUpTo("login") {
                                        inclusive = true
                                    }
                                }
                            }
                        }
                        composable("entries") {
                            TimeEntryListView(
                                entries = model.timeEntries,
                                projects = model.projects,
                                now = now,
                                apiRequest = apiRequest,
                                setData = { entries, projects ->
                                    model.timeEntries.clear()
                                    model.timeEntries.addAll(entries)

                                    model.projects.clear()
                                    model.projects.putAll(projects.associateBy { it.id })

                                    model.tags = getTagsFromEntries(model.timeEntries).toMutableStateList()
                                },
                                goToLogin = { navController.navigate("login") },
                                editEntry = { entry ->
                                    currentlyEditedEntry = entry?.toObservable() ?: TimeEntryObservable()
                                    navController.navigate("edit_entry")
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        composable("edit_entry") {
                            val mostRecent = model.timeEntries.first()
                            val current = currentlyEditedEntry
                            var lastEntryStopTime: Instant? = null

                            if (current.id == null && mostRecent != null) {
                                lastEntryStopTime = mostRecent.endTime
                            } else if (current.id == mostRecent?.id && model.timeEntries.count() >= 2) {
                                lastEntryStopTime = model.timeEntries[1].endTime
                            }

                            EditEntryPage(
                                projects = model.projects,
                                entry = current,
                                allTags = model.tags,
                                now = now,
                                lastEntryStopTime = lastEntryStopTime,
                                saveAndExit = {
                                    navController.popBackStack()
                                },
                                cancelAndExit = {
                                    currentlyEditedEntry = TimeEntryObservable()
                                    navController.popBackStack()
                                }
                            )
                        }
                    }

                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        handler.post(updateTask)
        lifecycleScope.launch {
//            ApiRequest().getCurrentTimeEntry()?.let {
//                it.endTime = Instant.now()
//                ApiRequest().updateTimeEntry(it)
//            }
//            ApiRequest().newTimeEntry(TimeEntry(description="TEST", startTime = Instant.now().minusSeconds(60), endTime = Instant.now()
//            ))
//            ApiRequest().authenticate("EMAIL", "PASSWORD")
//            ApiRequest().getProjects()?.let {
//                model.projects.clear()
//                model.projects.putAll(it.associateBy { it.id })
//            }
//            ApiRequest().getTimeEntries(
////                startDate = Instant.now().minusSeconds(60 * 60 * 24 * 1),
////                endDate = Instant.now().minusSeconds(60 * 60 * 24 * 0),
//            )?.let {
//                model.timeEntries.clear()
//                model.timeEntries.addAll(it)
//            }
        }

    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateTask)
    }
}




