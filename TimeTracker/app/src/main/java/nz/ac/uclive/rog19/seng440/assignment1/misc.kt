package nz.ac.uclive.rog19.seng440.assignment1

import java.time.Duration

/// Log.d(A, "some message")
val TAG = "GodModel"

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

    for((unitValue, shortSuffix) in unitized.reversed()) {
        if (!bla && unitValue == 0) {
            continue
        }

        // even if value is 0, show it
        bla = true
        if (out.isNotEmpty()) {
            out += " "
        }
        out += "$unitValue$shortSuffix"

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
