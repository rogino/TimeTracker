package com.rioogino.timetracker

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.time.Duration
import java.time.Instant

/// Log.d(A, "some message")
val TAG = "GodModel"

val newlineEtAlRegex = Regex("[\\r\\n\\t]")

/// Format a duration, showing the most significant `n` components
fun durationFormatter(duration: Duration, numComponents: Int = 2): String {
    val components: Array<Pair<Int, String>> = arrayOf(
        Pair(60, "s"),
        Pair(60, "m"),
        Pair(24, "h"),
        Pair(7, "d")
    )

    var bla = false
    var count = numComponents
    var out = ""
    var remainder = duration.seconds.toInt()
    val unitized = components.map { (divisor, shortSuffix) ->
        val unitValue = remainder % divisor
        remainder = (remainder - unitValue) / divisor
        Pair(unitValue, shortSuffix)
    }

    for ((unitValue, shortSuffix) in unitized.reversed()) {
        if (!bla && unitValue == 0) {
            continue
        }

        var unitValueText = "$unitValue"
        // TODO add property
        if (arrayOf("s", "m", "h").contains(shortSuffix)) {
            // All but the most significant unit should have leading zeros
            if (bla && unitValueText.length == 1) {
                unitValueText = "${"0".repeat(1)}$unitValue"
            }
        }

        // even if value is 0, show it
        bla = true
        if (out.isNotEmpty()) {
            out += " "
        }
        out += "$unitValueText$shortSuffix"

        count -= 1
        if (count <= 0) {
            break
        }
    }

    if (out.isEmpty()) {
        out = "Zero"
    }
    return out
}

val PaddingValues.top: Dp
    get() = calculateTopPadding()

val PaddingValues.bottom: Dp
    get() = calculateBottomPadding()

fun PaddingValues(horizontal: Dp = 0.dp, top: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues =
    PaddingValues(
        start = horizontal,
        top = top,
        end = horizontal,
        bottom = bottom
    )

val PaddingValues.horizontal: Dp
    get() = (calculateStartPadding(LayoutDirection.Ltr) + calculateEndPadding(LayoutDirection.Ltr)) / 2

fun Instant.minusDays(days: Long): Instant {
    return this.minusSeconds(60 * 60 * 24 * days)
}

fun Instant.minusDays(days: Float): Instant {
    return this.minusSeconds((60f * 60f * 24f * days).toLong())
}

fun Instant.plusDays(days: Long): Instant {
    return this.plusSeconds(60 * 60 * 24 * days)
}

fun Instant.plusDays(days: Float): Instant {
    return this.plusSeconds((60f * 60f * 24f * days).toLong())
}


fun makeConcurrentRequests(
    coroutineScope: CoroutineScope,
    onEnd: ((Throwable?) -> Unit)?,
    vararg apiCalls: (suspend () -> Unit)
) {
    coroutineScope.launch {
        try {
            supervisorScope {
                withContext(Dispatchers.IO) {
                    apiCalls.map { async { it() } }.awaitAll()
                }
            }
            onEnd?.invoke(null)
        } catch (err: Throwable) {
            onEnd?.invoke(err)
        }
    }
}

fun showErrorToast(context: Context, error: Throwable) {
    Log.d(TAG, error.stackTraceToString())
    Toast.makeText(
        context,
        error.message ?: error.toString(),
        Toast.LENGTH_LONG
    ).show()
}

fun makeRequestsShowingToastOnError(
    coroutineScope: CoroutineScope,
    context: Context,
    onEnd: ((Boolean) -> Unit)?,
    vararg apiCalls: (suspend () -> Unit)
) {
    makeConcurrentRequests(
        coroutineScope = coroutineScope,
        onEnd = {
            onEnd?.invoke(it == null)
            it?.let { showErrorToast(context = context, error = it) }
        },
        apiCalls = apiCalls
    )
}

@Composable
fun TextFieldClearButton(textFieldValue: String, clear: () -> Unit, isFocused: Boolean) {
    if (textFieldValue.isNotEmpty() && isFocused) {
        IconButton(onClick = clear ) {
            Icon(
                Icons.Filled.Clear,
                contentDescription = "Clear",
                tint = MaterialTheme.colors.primary
            )
        }
    }
}
@Composable
fun TextFieldClearButton(textFieldValue: TextFieldValue, clear: () -> Unit, isFocused: Boolean) {
    TextFieldClearButton(textFieldValue.text, clear, isFocused)
}


// https://manavtamboli.medium.com/infinite-list-paged-list-in-jetpack-compose-b10fc7e74768
@Composable
fun LazyListState.OnBottomReached(
    loadMore : () -> Unit
){
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf true

            lastVisibleItem.index == layoutInfo.totalItemsCount - 1
        }
    }

    // Convert the state into a cold flow and collect
    LaunchedEffect(shouldLoadMore){
        snapshotFlow { shouldLoadMore.value }
            .collect {
                // if should load more, then invoke loadMore
                if (it) loadMore()
            }
    }
}
