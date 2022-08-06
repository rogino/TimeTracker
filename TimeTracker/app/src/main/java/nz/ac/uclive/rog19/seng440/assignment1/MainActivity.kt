package nz.ac.uclive.rog19.seng440.assignment1

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nz.ac.uclive.rog19.seng440.assignment1.model.GodModel
import nz.ac.uclive.rog19.seng440.assignment1.model.mockModel
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme
import java.time.Instant

// https://developer.android.com/studio/write/java8-support-table


class MainActivity : ComponentActivity() {
    private lateinit var model: GodModel
    private lateinit var handler: Handler
    private lateinit var updateTask: Runnable
    private var now = mutableStateOf(Instant.now())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = mockModel

        handler = Handler(Looper.getMainLooper())
        updateTask = Runnable {
            now.value = Instant.now()
            handler.postDelayed(updateTask, 1000)
        }

        setContent {
            TimeTrackerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    TimeEntryListView(
                        entries = model.timeEntries,
                        projects = model.projects,
                        now = now
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        handler.post(updateTask)
        lifecycleScope.launch {
//            ApiRequest().getCurrentTimeEntry()?.let {
//                model.timeEntries.add(it)
//            }
            ApiRequest().getTimeEntries(
                startDate = Instant.now().minusSeconds(60 * 60 * 24 * 9),
                endDate = Instant.now().minusSeconds(60 * 60 * 24 * 8),
            )?.let {
                model.timeEntries.clear()
                model.timeEntries.addAll(it)
            }
        }

    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateTask)
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}



