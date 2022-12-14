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
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.security.crypto.MasterKeys
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nz.ac.uclive.rog19.seng440.assignment1.components.EditEntryPage
import nz.ac.uclive.rog19.seng440.assignment1.components.LoginView
import nz.ac.uclive.rog19.seng440.assignment1.model.GodModel
import nz.ac.uclive.rog19.seng440.assignment1.model.GodModelSerialized
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.AppTheme
import java.time.Instant
import kotlin.properties.Delegates

// https://developer.android.com/studio/write/java8-support-table

val timeTrackerPreferencesFileName = "main"

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
            commit()
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
}


class MainActivity : ComponentActivity() {
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
        preferences = PreferenceWrapper(baseContext)

        var isDarkMode = mutableStateOf<Boolean?>(preferences.isDarkMode())

        if (savedInstanceState == null) {
            // Only retrieve from disk when launching app
            lifecycleScope.launch {
                Log.d(TAG, "Retrieving model from disk")
                GodModelSerialized.readAndPopulateModel(baseContext, model)
                Log.d(TAG, "Retrieved model from disk")
            }
        }

        apiRequest = ApiRequest()
        apiRequest.context = baseContext
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
                && vm.isRefreshing.value == false
            ) {
                vm.isRefreshing.value = true
                model.refreshEverything(
                    coroutineScope = lifecycleScope,
                    apiRequest = apiRequest
                ) {
                    vm.isRefreshing.value = false
                    it?.let { showErrorToast(baseContext, it) }
                }
            }
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);


        setContent {
            val systemUiController = rememberSystemUiController()
            val useDarkIcons = isDarkMode.value ?: MaterialTheme.colors.isLight
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
            AppTheme(useDarkTheme = isDarkMode.value ?: isSystemInDarkTheme()) {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    NavHost(navController, startDestination = startDestination) {
                        composable("login") {
                            LoginView(
                                apiRequest = apiRequest,
                                context = baseContext,
                                contentPadding = recommendedPadding
                            ) {
                                preferences.saveCredentials(it.apiToken, it.defaultWorkspaceId)

                                vm.isRefreshing.value = true
                                model.refreshEverything(
                                    coroutineScope = lifecycleScope,
                                    apiRequest = apiRequest
                                ) {
                                    vm.isRefreshing.value = false
                                    it?.let { showErrorToast(baseContext, it) }
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
                                apiRequest = apiRequest,
                                context = baseContext,
                                logout = {
                                    navController.navigate("login") {
                                        // Prevent return to entries page
                                        popUpTo("entries") { inclusive = true }
                                        preferences.clearCredentials()
                                        lifecycleScope.launch {
                                            GodModelSerialized.clear(baseContext)
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
                                context = baseContext,
                                goBack = {
                                    if (navController.backQueue.isNotEmpty()) {
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
                if (model.timeEntries.isEmpty() || model.projects.isEmpty() || model.tags.isEmpty()) {
                    return@launch
                }

                Log.d(TAG, "Saving model to disk")
                GodModelSerialized.saveModelToFile(baseContext, model)
                Log.d(TAG, "Saved model to disk")
            }
        }
    }
}




