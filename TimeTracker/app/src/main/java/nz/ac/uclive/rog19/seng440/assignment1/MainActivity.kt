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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.launch
import nz.ac.uclive.rog19.seng440.assignment1.components.EditEntryPage
import nz.ac.uclive.rog19.seng440.assignment1.components.LoginView
import nz.ac.uclive.rog19.seng440.assignment1.model.*
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.AppTheme
import java.time.Instant

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
        WindowCompat.setDecorFitsSystemWindows(window, false)


        model = GodModel()
        apiRequest = ApiRequest()
        apiRequest.context = baseContext

        val preferences = getSharedPreferences(timeTrackerPreferencesFileName, Context.MODE_PRIVATE)
        var currentlyEditedEntry = TimeEntryObservable()
        var currentlyEditedEntryDidHaveEndTimeSet: Boolean? = null

        val key = preferences.getString("API_KEY", null)
        val workspaceId = preferences.getInt("DEFAULT_WORKSPACE_ID", -1)
        if (key != null && workspaceId > 0) {
            apiRequest.apiKey = key
            apiRequest.defaultWorkspaceId = workspaceId
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
            val systemUiController = rememberSystemUiController()
            val useDarkIcons = MaterialTheme.colors.isLight
            SideEffect {
                // Update all of the system bar colors to be transparent, and use
                // dark icons if we're in light theme
                systemUiController.setSystemBarsColor(
                    color = Color.Transparent,
                    darkIcons = useDarkIcons
                )
            }
            val navController = rememberNavController()

            val paddingModifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
            AppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val mostRecent = if (model.timeEntries.isEmpty()) null else model.timeEntries.first()
                    var lastEntryStopTime: Instant? = null

                    if (currentlyEditedEntry?.id == null && mostRecent != null) {
                        lastEntryStopTime = mostRecent.endTime
                    } else if (currentlyEditedEntry.id == mostRecent?.id && model.timeEntries.count() >= 2) {
                        lastEntryStopTime = model.timeEntries[1].endTime
                    }
                    if (lastEntryStopTime?.isBefore(Instant.now().minusSeconds(60 * 60 * 24)) == true) {
                        // Max age of 1 day
                        lastEntryStopTime = null
                    }

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
                                    // Remove login page from stack
                                    popUpTo("login") {
                                        inclusive = true
                                    }
                                }
                            }
                        }
                        composable("entries") {
                            TimeEntryListPage(
                                model = model,
                                now = now,
                                lastEntryStopTime = lastEntryStopTime,
                                apiRequest = apiRequest,
                                logout = {
                                    navController.navigate("login") {
                                        // Prevent return to entries page
                                        popUpTo("entries") { inclusive = true }

                                        with(
                                            getSharedPreferences(
                                                timeTrackerPreferencesFileName,
                                                Context.MODE_PRIVATE
                                            ).edit()
                                        ) {
                                            remove("API_KEY")
                                            remove("DEFAULT_WORKSPACE_ID")
                                            commit()
                                        }
                                    }
                                },
                                editEntry = { entry ->
                                    currentlyEditedEntry =
                                        entry?.toObservable() ?: TimeEntryObservable()
                                    currentlyEditedEntryDidHaveEndTimeSet = currentlyEditedEntry.endTime != null
                                    navController.navigate("edit_entry")
                                },
                                modifier = paddingModifier
                            )
                        }
                        composable("edit_entry") {
                            EditEntryPage(
                                model = model,
                                entry = currentlyEditedEntry,
                                now = now,
                                lastEntryStopTime = lastEntryStopTime,
                                apiRequest = apiRequest,
                                didHaveEndTimeSet = currentlyEditedEntryDidHaveEndTimeSet,
                                goBack = {
                                    navController.popBackStack()
                                },
                                modifier = paddingModifier
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




