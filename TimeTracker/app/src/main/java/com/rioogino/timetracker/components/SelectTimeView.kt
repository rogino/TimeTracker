package com.rioogino.timetracker.components

import android.content.res.Configuration
import android.text.format.DateFormat
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rioogino.timetracker.R
import com.rioogino.timetracker.ui.theme.AppTheme
import com.vanpra.composematerialdialogs.MaterialDialog
import com.vanpra.composematerialdialogs.datetime.date.datepicker
import com.vanpra.composematerialdialogs.datetime.time.timepicker
import com.vanpra.composematerialdialogs.rememberMaterialDialogState
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SelectTimeView(
    label: String,
    date: Instant?,
    zoneId: ZoneId,
    lastStopTime: Instant?, 
    unsetText: String?,
    now: Instant?,
    setDate: (Instant?) -> Unit,
    allowEditing: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val dateDialog = rememberMaterialDialogState()
    val timeDialog = rememberMaterialDialogState()
    var isExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() } // Added for clickable fix

    val stableNowFallback = remember { Instant.now() }
    val currentInstantForDisplay = date ?: now ?: stableNowFallback

    val localDateTime = remember(currentInstantForDisplay, zoneId) { 
        ZonedDateTime.ofInstant(currentInstantForDisplay, zoneId)
    }
    val localDate = remember(localDateTime) { localDateTime.toLocalDate() }
    val localTime = remember(localDateTime) { localDateTime.toLocalTime() }

    val context = LocalContext.current
    val systemDateFormat = remember { DateFormat.getDateFormat(context) }
    val datePattern = remember(systemDateFormat) { (systemDateFormat as SimpleDateFormat).toLocalizedPattern() }
    val timePattern = remember(date) { if (date != null) "hh:mm a" else "hh:mm:ss a" }
    
    val dateFormatter = remember(datePattern, zoneId) { DateTimeFormatter.ofPattern(datePattern).withZone(zoneId) }
    val timeFormatter = remember(timePattern, zoneId) { DateTimeFormatter.ofPattern(timePattern).withZone(zoneId) }

    fun toggleExpand() {
        if (allowEditing) isExpanded = !isExpanded
    }

    Column(modifier = modifier) { // Outermost Column
        // MaterialDialogs are now the FIRST children of the outer Column.
        MaterialDialog(
            dialogState = dateDialog,
            buttons = {
                positiveButton(stringResource(R.string.ok), textStyle = TextStyle(color = MaterialTheme.colorScheme.primary))
                negativeButton(stringResource(R.string.cancel), textStyle = TextStyle(color = MaterialTheme.colorScheme.primary))
            }
        ) {
            datepicker(initialDate = localDate, title = stringResource(R.string.select_date)) { newDate ->
                val newLocalDateTime = LocalDateTime.of(newDate, localTime)
                setDate(newLocalDateTime.atZone(zoneId).toInstant())
            }
        }

        MaterialDialog(
            dialogState = timeDialog,
            buttons = {
                positiveButton(stringResource(R.string.ok), textStyle = TextStyle(color = MaterialTheme.colorScheme.primary))
                negativeButton(stringResource(R.string.cancel), textStyle = TextStyle(color = MaterialTheme.colorScheme.primary))
            }
        ) {
            timepicker(initialTime = localTime, title = stringResource(R.string.select_time), is24HourClock = DateFormat.is24HourFormat(context)) { newTime ->
                val newLocalDateTime = LocalDateTime.of(localDate, newTime)
                setDate(newLocalDateTime.atZone(zoneId).toInstant())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp) 
                .clip(MaterialTheme.shapes.extraSmall) 
                .border(
                    width = 1.dp,
                    color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = MaterialTheme.shapes.extraSmall
                )
                // Updated clickable modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = allowEditing, 
                    onClick = ::toggleExpand
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f) 
                )

                val displayDateText = when {
                    date == null -> stringResource(R.string.not_set)
                    localDate.isEqual(LocalDate.now(zoneId)) -> stringResource(R.string.today)
                    localDate.isEqual(LocalDate.now(zoneId).minusDays(1)) -> stringResource(R.string.yesterday)
                    else -> dateFormatter.format(localDateTime)
                }

                TextButton(
                    onClick = { if (allowEditing) dateDialog.show() }, 
                    enabled = allowEditing && date != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(displayDateText)
                }

                TextButton(
                    onClick = { if (allowEditing) timeDialog.show() }, 
                    enabled = allowEditing && date != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(timeFormatter.format(localDateTime))
                }
            }
        }
        
        if (isExpanded && allowEditing) {
            Spacer(Modifier.height(8.dp))
            SelectTimeViewDeltaButtons(
                currentDate = date,
                setDate = setDate,
                lastStopTime = lastStopTime,
                unsetText = unsetText,
                nowForDelta = now ?: stableNowFallback, // Use stable now reference
                zoneId = zoneId,
                allowEditing = allowEditing
            )
        }
    }
}

@Composable
fun SelectTimeViewDeltaButtons(
    currentDate: Instant?,
    setDate: (Instant?) -> Unit,
    lastStopTime: Instant?,
    unsetText: String?,
    nowForDelta: Instant, 
    zoneId: ZoneId,
    allowEditing: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            val increments = listOf(-5L, -1L, 1L, 5L)
            increments.forEach { min ->
                OutlinedButton(
                    onClick = { 
                        currentDate?.let { setDate(it.plusSeconds(min * 60)) }
                    },
                    enabled = allowEditing && currentDate != null,
                    modifier = Modifier.padding(horizontal = 2.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(text = "${if (min > 0) "+" else ""}$min")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { setDate(nowForDelta) }, 
                enabled = allowEditing,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(stringResource(R.string.now))
            }
            if (lastStopTime != null) {
                OutlinedButton(
                    onClick = { setDate(lastStopTime) },
                    enabled = allowEditing,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(stringResource(R.string.last_stop_time))
                }
            }
            if (unsetText != null) {
                OutlinedButton(
                    onClick = { setDate(null) },
                    enabled = allowEditing && currentDate != null,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(text = unsetText)
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "SelectTimeView Light - Expanded Placeholder")
@Composable
fun SelectTimeView_Preview_Light_Set_Expanded() {
    AppTheme(useDarkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(16.dp)) {
            val previewNow = remember { Instant.now() }
            SelectTimeView(
                label = "Start Time (Click to Expand)",
                date = previewNow.minusSeconds(3600),
                zoneId = ZoneId.systemDefault(),
                lastStopTime = previewNow.minusSeconds(7200),
                unsetText = "Ongoing",
                now = previewNow,
                setDate = {},
                allowEditing = true
            )
        }
    }
}

@Preview(showBackground = true, name = "SelectTimeView Dark - Set (Restored Box)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SelectTimeView_Preview_Dark_Set() {
    AppTheme(useDarkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(16.dp)) {
            val previewNow = remember { Instant.now() }
            SelectTimeView(
                label = "End Time",
                date = previewNow,
                zoneId = ZoneId.systemDefault(),
                lastStopTime = previewNow.minusSeconds(3600),
                unsetText = "Current",
                now = previewNow,
                setDate = {},
                allowEditing = true
            )
        }
    }
}

@Preview(showBackground = true, name = "SelectTimeView Light - Not Set (Restored Box)")
@Composable
fun SelectTimeView_Preview_Light_Not_Set() {
    AppTheme(useDarkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(16.dp)) {
            val previewNow = remember { Instant.now() } 
            SelectTimeView(
                label = "End Time",
                date = null, 
                zoneId = ZoneId.systemDefault(),
                lastStopTime = previewNow.minusSeconds(3600),
                unsetText = "Track",
                now = previewNow, 
                setDate = {},
                allowEditing = true
            )
        }
    }
}

@Preview(showBackground = true, name = "SelectTimeView Dark - Not Set (Restored Box)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SelectTimeView_Preview_Dark_Not_Set() {
    AppTheme(useDarkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(16.dp)) {
            val previewNow = remember { Instant.now() } 
            SelectTimeView(
                label = "End Time",
                date = null, 
                zoneId = ZoneId.systemDefault(),
                lastStopTime = previewNow.minusSeconds(3600),
                unsetText = "Track",
                now = previewNow, 
                setDate = {},
                allowEditing = true
            )
        }
    }
}
