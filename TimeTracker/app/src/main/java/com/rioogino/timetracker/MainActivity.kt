package com.rioogino.timetracker // Corrected package name

// import android.view.WindowManager // Not used
// import androidx.compose.ui.graphics.Color // Not used
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rioogino.timetracker.components.EditEntryPage
import com.rioogino.timetracker.components.LoginView
import com.rioogino.timetracker.components.TimeEntryListPage
import com.rioogino.timetracker.model.GodModel
import com.rioogino.timetracker.model.GodModelSerialized
import com.rioogino.timetracker.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.properties.Delegates

// https://developer.android.com/studio/write/java8-support-table

val timeTrackerPreferencesFileName = "main"
// Removed top-level: const val TAG = "MainActivity"

class PreferenceWrapper(context: Context, fileName: String = timeTrackerPreferencesFileName) {
    private val preferences: SharedPreferences
    init {
        val builder = MasterKey.Builder(context)
        builder.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)

        val masterKey = builder.build()

        preferences = EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val API_KEY = "API_KEY"
    private val WORKSPACE_ID = "WORKSPACE_ID"
    private val THEME = "COLOR_THEME"

    fun initApiRequest(apiRequest: ApiRequest) {
        val key = preferences.getString(API_KEY, null)
        val workspaceId = preferences.getInt(WORKSPACE_ID, -1)
        if (key != null && workspaceId > 0) {
            apiRequest.apiKey = key
            apiRequest.defaultWorkspaceId = workspaceId
        }
    }

    fun isDarkMode(): Boolean? {
        val theme = preferences.getString(THEME, null)
        if (theme == "dark") {
            return true
        } else if (theme == "light") {
            return false
        }
        return null
    }

    fun setTheme(isDarkMode: Boolean?) {
        with(preferences.edit()) {
            if (isDarkMode == true) {
                putString(THEME, "dark")
            } else if (isDarkMode == false) {
                putString(THEME, "light")
            } else {
                remove(THEME)
            }
            apply()
        }
    }


    fun saveCredentials(apiToken: String, workspaceId: Int) {
        with(preferences.edit()) {
            putString(API_KEY, apiToken)
            putInt(WORKSPACE_ID, workspaceId)
            apply()
        }
    }

    fun clearCredentials() {
        with(preferences.edit()) {
            remove(API_KEY)
            remove(WORKSPACE_ID)
            apply()
        }
    }
}


class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity" // TAG defined in companion object
    }

    private var model: GodModel by Delegates.notNull()
    private var vm: MainActivityViewModel by Delegates.notNull()
    private lateinit var handler: Handler
    private lateinit var updateTask: Runnable
    private lateinit var apiRequest: ApiRequest
    private lateinit var preferences: PreferenceWrapper
    private var now = mutableStateOf(Instant.now())

    private fun makeGodModel(): GodModel {
        val model: GodModel by viewModels()
        return model
    }

    class MainActivityViewModel : ViewModel() {
        var isRefreshing = mutableStateOf(false)
    }

    private fun makeVm(): MainActivityViewModel {
        val vm: MainActivityViewModel by viewModels()
        return vm
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        model = makeGodModel()
        vm = makeVm()
        preferences = PreferenceWrapper(applicationContext)

        var isDarkMode = mutableStateOf<Boolean?>(preferences.isDarkMode())

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                Log.d(TAG, "Retrieving model from disk")
                GodModelSerialized.readAndPopulateModel(applicationContext, model)
                Log.d(TAG, "Retrieved model from disk")
            }
        }

        apiRequest = ApiRequest()
        apiRequest.context = applicationContext
        apiRequest.quotaListener = model
        preferences.initApiRequest(apiRequest)

        handler = Handler(Looper.getMainLooper())
        updateTask = Runnable {
            now.value = Instant.now()
            handler.postDelayed(updateTask, 1000)
        }

        var startDestination = "login"
        if (apiRequest.authenticated) {
            startDestination = "entries"
            val lastUpdated = model.lastUpdated
            if ((lastUpdated == null || lastUpdated.isBefore(Instant.now().minusSeconds(60)))
                && !vm.isRefreshing.value
            ) {
                vm.isRefreshing.value = true
                model.refreshEverything(
                    coroutineScope = lifecycleScope,
                    apiRequest = apiRequest
                ) {
                    vm.isRefreshing.value = false
                    it?.let { showErrorToast(applicationContext, it) }
                }
            }
        }

        enableEdgeToEdge()

        setContent {
            val navController = rememberNavController()

            val recommendedPadding = PaddingValues(horizontal = 16.dp, top = 8.dp)
            AppTheme(useDarkTheme = isDarkMode.value ?: isSystemInDarkTheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(navController, startDestination = startDestination) {
                        composable("login") {
                            LoginView(
                                apiRequest = apiRequest,
                                context = applicationContext,
                                contentPadding = recommendedPadding
                            ) {
                                preferences.saveCredentials(it.apiToken, it.defaultWorkspaceId)

                                vm.isRefreshing.value = true
                                model.refreshEverything(
                                    coroutineScope = lifecycleScope,
                                    apiRequest = apiRequest
                                ) {
                                    vm.isRefreshing.value = false
                                    it?.let { showErrorToast(applicationContext, it) }
                                }

                                navController.navigate("entries") {
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
                                apiRequest = apiRequest,
                                context = applicationContext,
                                logout = {
                                    navController.navigate("login") {
                                        popUpTo("entries") { inclusive = true }
                                        preferences.clearCredentials()
                                        lifecycleScope.launch {
                                            GodModelSerialized.clear(applicationContext)
                                        }
                                    }
                                },
                                goToEditEntryView = {
                                    navController.navigate("edit_entry")
                                },
                                isRefreshing = vm.isRefreshing,
                                contentPadding = recommendedPadding,
                                isDarkMode = isDarkMode.value,
                                setTheme = {
                                    isDarkMode.value = it
                                    lifecycleScope.launch {
                                        withContext(Dispatchers.IO) {
                                            preferences.setTheme(isDarkMode.value)
                                        }
                                    }
                                }
                            )
                        }
                        composable("edit_entry") {
                            EditEntryPage(
                                model = model,
                                now = now,
                                apiRequest = apiRequest,
                                context = applicationContext,
                                goBack = {
                                    if (navController.previousBackStackEntry != null) {
                                        navController.popBackStack()
                                    }
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
        if (apiRequest.authenticated) {
            lifecycleScope.launch {
                if (model.timeEntries.isEmpty() && model.projects.isEmpty() && model.tags.isEmpty()) {
                    return@launch
                }

                Log.d(TAG, "Saving model to disk")
                GodModelSerialized.saveModelToFile(applicationContext, model)
                Log.d(TAG, "Saved model to disk")
            }
        }
    }
}
