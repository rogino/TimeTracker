package nz.ac.uclive.rog19.seng440.assignment1

import java.time.Duration

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
