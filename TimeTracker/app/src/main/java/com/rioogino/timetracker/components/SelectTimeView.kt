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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
// import androidx.compose.material3.TimePickerDialog // Using AlertDialog for TimePicker now
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rioogino.timetracker.R
import com.rioogino.timetracker.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }

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

    var isExpanded by remember { mutableStateOf(false) }
    fun toggleExpand() {
        if (allowEditing) isExpanded = !isExpanded
    }

    Column(modifier = modifier) {
        if (showDatePickerDialog) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = localDateTime.toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { showDatePickerDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val newLocalDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
                            val newLocalDateTime = LocalDateTime.of(newLocalDate, localTime)
                            setDate(newLocalDateTime.atZone(zoneId).toInstant())
                        }
                        showDatePickerDialog = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePickerDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            ) {
                DatePicker(
                    state = datePickerState,
                    title = { Text(stringResource(R.string.select_date), modifier = Modifier.padding(start = 24.dp, top = 24.dp, end=24.dp, bottom = 12.dp)) }
                )
            }
        }

        if (showTimePickerDialog) {
            val timePickerState = rememberTimePickerState(
                initialHour = localTime.hour,
                initialMinute = localTime.minute,
                is24Hour = DateFormat.is24HourFormat(context)
            )
            AlertDialog(
                onDismissRequest = { showTimePickerDialog = false },
                title = {
                    Text(stringResource(R.string.select_time))
                },
                text = {
                       TimePicker(state = timePickerState)
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newLocalTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        val newLocalDateTime = LocalDateTime.of(localDate, newLocalTime)
                        setDate(newLocalDateTime.atZone(zoneId).toInstant())
                        showTimePickerDialog = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePickerDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
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
                    onClick = { if (allowEditing) showDatePickerDialog = true },
                    enabled = allowEditing && date != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(displayDateText)
                }

                TextButton(
                    onClick = { if (allowEditing) showTimePickerDialog = true },
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
                nowForDelta = now ?: stableNowFallback,
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

@OptIn(ExperimentalMaterial3Api::class)
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

@OptIn(ExperimentalMaterial3Api::class)
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

@OptIn(ExperimentalMaterial3Api::class)
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

@OptIn(ExperimentalMaterial3Api::class)
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
