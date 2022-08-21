package nz.ac.uclive.rog19.seng440.assignment1

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.beust.klaxon.Klaxon
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nz.ac.uclive.rog19.seng440.assignment1.components.EditEntryPage
import nz.ac.uclive.rog19.seng440.assignment1.components.LoginView
import nz.ac.uclive.rog19.seng440.assignment1.model.*
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.AppTheme
import java.time.Instant

// https://developer.android.com/studio/write/java8-support-table

val timeTrackerPreferencesFileName = "main"

class PreferenceWrapper(val preferences: SharedPreferences) {
    private val API_KEY = "API_KEY"
    private val WORKSPACE_ID = "WORKSPACE_ID"
    private val PROJECTS = "PROJECT_LIST"
    private val TAG_NAME_LIST = "TAG_NAME_LIST"
    private val RECENT_ENTRIES = "RECENT_ENTRIES"

    fun initApiRequest(apiRequest: ApiRequest) {
        val key = preferences.getString(API_KEY, null)
        val workspaceId = preferences.getInt(WORKSPACE_ID, -1)
        if (key != null && workspaceId > 0) {
            apiRequest.apiKey = key
            apiRequest.defaultWorkspaceId = workspaceId
        }
    }

    fun saveCredentials(apiToken: String, workspaceId: Int) {
        with(preferences.edit()) {
            putString(API_KEY, apiToken)
            putInt(WORKSPACE_ID, workspaceId)
            commit()
        }
    }

    fun clearCredentials() {
        with(preferences.edit()) {
            remove(API_KEY)
            remove(WORKSPACE_ID)
            commit()
        }
    }

    val maxEntryAge: Long = 3
    private val jsonConverter = Klaxon().converter(DateTimeConverter)
    suspend fun saveModel(model: GodModel) {
        withContext(Dispatchers.IO) {
            val tags = jsonConverter.toJsonString(model.tags)
            val projects = jsonConverter.toJsonString(model.projects.values)

            val now = Instant.now()
            val entries = model.timeEntries.filter { now.minusDays(maxEntryAge).isBefore(it.startTime) }
            val entriesString = jsonConverter.toJsonString(entries)

            with(preferences.edit()) {
                putString(TAG_NAME_LIST, tags)
                putString(PROJECTS, projects)
                putString(RECENT_ENTRIES, entriesString)
                commit()
            }
        }
    }

    /// Will not overwrite if there's already stuff in the model
    suspend fun populateModelWithCached(model: GodModel) {
        withContext(Dispatchers.IO) {
            try {
                val now = Instant.now()
                val entries = jsonConverter.parseArray<TimeEntry>(
                    preferences.getString(RECENT_ENTRIES, null) ?: "[]"
                )?.filter { now.minusDays(maxEntryAge).isBefore(it.startTime) }

                withContext(Dispatchers.Main) {
                    if (model.timeEntries.isEmpty()) {
                        Log.d(TAG, "Add ${entries?.count()} entries from disk to the model")
                        entries?.let { model.setEntries(entries) }
                    }
                }


                val projects = jsonConverter.parseArray<Project>(
                    preferences.getString(PROJECTS, null) ?: "[]"
                )
                withContext(Dispatchers.Main) {
                    if (model.projects.isEmpty()) {
                        Log.d(TAG, "Add ${projects?.count()} projects from disk to the model")
                        projects?.let { model.setProjects(it) }
                    }
                }


                val tags = jsonConverter.parseArray<String>(
                    preferences.getString(TAG_NAME_LIST, null) ?: "[]"
                )
                withContext(Dispatchers.Main) {
                    if (model.tags.isEmpty()) {
                        Log.d(TAG, "Add ${tags?.count()} projects from disk to the model")
                        tags?.let { model.setTags(tags) }
                    }
                }
            } catch (err: Throwable) {
                Log.d(TAG, "Failed to populate model from cached data in disk")
                Log.d(TAG, err.stackTraceToString())
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var model: GodModel
    private lateinit var handler: Handler
    private lateinit var updateTask: Runnable
    private lateinit var apiRequest: ApiRequest
    private lateinit var preferences: PreferenceWrapper
    private var now = mutableStateOf(Instant.now())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        model = GodModel()
        preferences = PreferenceWrapper(
            getSharedPreferences(timeTrackerPreferencesFileName, Context.MODE_PRIVATE)
        )
        lifecycleScope.launch {
            Log.d(TAG, "Retrieving model from disk")
            preferences.populateModelWithCached(model)
            Log.d(TAG, "Retrieved model from disk")
        }

        apiRequest = ApiRequest()
        apiRequest.context = baseContext
        preferences.initApiRequest(apiRequest)

        var currentlyEditedEntry = TimeEntryObservable()
        var currentlyEditedEntryDidHaveEndTimeSet: Boolean? = null

        handler = Handler(Looper.getMainLooper())
        updateTask = Runnable {
            now.value = Instant.now()
            handler.postDelayed(updateTask, 1000)
        }

        var isRefreshing = mutableStateOf(false)

        var startDestination = "login"
        if (apiRequest.authenticated) {
            startDestination = "entries"
            isRefreshing.value = true
            model.refreshEverything(coroutineScope = lifecycleScope, apiRequest = apiRequest) {
                isRefreshing.value = false
                it?.let { showErrorToast(baseContext, it) }
            }
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);


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

            val recommendedPadding = PaddingValues(horizontal = 16.dp, top = 8.dp)
            AppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    NavHost(navController, startDestination = startDestination) {
                        composable("login") {
                            LoginView(
                                apiRequest = apiRequest,
                                contentPadding = recommendedPadding
                            ) {
                                preferences.saveCredentials(it.apiToken, it.defaultWorkspaceId)
                                navController.navigate("entries") {
                                    // Remove login page from stack
                                    model.refreshEverything(coroutineScope = lifecycleScope, apiRequest = apiRequest) {
                                        isRefreshing.value = false
                                        it?.let { showErrorToast(baseContext, it) }
                                    }
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
                                lastEntryStopTime = { model.lastEntryStopTime(currentlyEditedEntry.id) },
                                apiRequest = apiRequest,
                                logout = {
                                    navController.navigate("login") {
                                        // Prevent return to entries page
                                        popUpTo("entries") { inclusive = true }
                                        preferences.clearCredentials()
                                    }
                                },
                                editEntry = { entry ->
                                    currentlyEditedEntry =
                                        entry?.toObservable() ?: TimeEntryObservable()
                                    currentlyEditedEntryDidHaveEndTimeSet = currentlyEditedEntry.endTime != null
                                    navController.navigate("edit_entry")
                                },
                                isRefreshing = isRefreshing,
                                contentPadding = recommendedPadding
                            )
                        }
                        composable("edit_entry") {
                            EditEntryPage(
                                model = model,
                                entry = currentlyEditedEntry,
                                now = now,
                                apiRequest = apiRequest,
                                didHaveEndTimeSet = currentlyEditedEntryDidHaveEndTimeSet,
                                goBack = {
                                    navController.popBackStack()
                                },
                                contentPadding = recommendedPadding
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
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateTask)
        lifecycleScope.launch {
            Log.d(TAG, "Saving model to disk")
            preferences.saveModel(model)
            Log.d(TAG, "Saved model to disk")
        }
    }
}




