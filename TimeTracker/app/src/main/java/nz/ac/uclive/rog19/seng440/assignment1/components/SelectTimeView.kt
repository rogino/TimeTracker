package nz.ac.uclive.rog19.seng440.assignment1.components

import android.text.format.DateFormat
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.vanpra.composematerialdialogs.MaterialDialog
import com.vanpra.composematerialdialogs.datetime.date.datepicker
import com.vanpra.composematerialdialogs.datetime.time.timepicker
import com.vanpra.composematerialdialogs.rememberMaterialDialogState
import java.text.Format
import java.text.SimpleDateFormat
import java.time.*
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

    val localDateTime = ZonedDateTime.ofInstant(date ?: now ?: Instant.now(), zoneId)
    val localDate = localDateTime.toLocalDate()
    val localTime = localDateTime.toLocalTime()

    val dateFormat: Format = DateFormat.getDateFormat(LocalContext.current)
    val datePattern: String = (dateFormat as SimpleDateFormat).toLocalizedPattern()
    val timePattern: String = if (date != null) "hh:mm a" else "hh:mm:ss a"
    val dateFormatter = DateTimeFormatter.ofPattern(datePattern)
    val timeFormatter = DateTimeFormatter.ofPattern(timePattern)

    val outlinedTextFieldLikeButtonColors: ButtonColors = ButtonDefaults.buttonColors(
        backgroundColor = MaterialTheme.colors.background,
        contentColor = Color.Unspecified
    )
    val outlinedBoringButtonColors: ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = Color.Unspecified
    )

    fun toggleExpand() {
        isExpanded = !isExpanded
    }
    Button(
        onClick = { toggleExpand() },
        colors = outlinedTextFieldLikeButtonColors,
        shape = RectangleShape,
        contentPadding = PaddingValues(0.dp),
        elevation = UnelevatedButtonElevation(),
        modifier = Modifier
            .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
            .background(
                MaterialTheme.colors.background,
                TextFieldDefaults.OutlinedTextFieldShape
            )
            .border(
                TextFieldDefaults.UnfocusedBorderThickness,
                TextFieldDefaults
                    .textFieldColors()
                    .indicatorColor(
                        enabled = false,
                        isError = false,
                        interactionSource = MutableInteractionSource(),
                    ).value,
                TextFieldDefaults.OutlinedTextFieldShape
            )
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = 200,
                    easing = LinearOutSlowInEasing
                )
            )
            .then(modifier)
    ) {
        Column() {
            val buttonHorizontalPadding =
                ButtonDefaults.ContentPadding.calculateEndPadding(LayoutDirection.Ltr)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label, style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(start = buttonHorizontalPadding)
                )
                Row() {
                    Button(
                        onClick = { if (isExpanded) dateDialog.show() else toggleExpand() },
                        colors = outlinedTextFieldLikeButtonColors,
                        enabled = allowEditing,
                        elevation = if (isExpanded) ButtonDefaults.elevation() else UnelevatedButtonElevation(),
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        var dateText = dateFormatter.format(localDateTime)
                        if (localDate.isEqual(LocalDate.now())) dateText = "Today"
                        else if (localDate.isEqual(LocalDate.now().minusDays(1))) dateText =
                            "Yesterday"
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.body1,
                        )
                    }

                    Button(
                        onClick = { if (isExpanded) timeDialog.show() else toggleExpand() },
                        colors = outlinedTextFieldLikeButtonColors,
                        enabled = allowEditing,
                        elevation = if (isExpanded) ButtonDefaults.elevation() else UnelevatedButtonElevation(),
                        contentPadding = PaddingValues(start = 0.dp, end = buttonHorizontalPadding),
                        modifier = Modifier
                            .padding(end = buttonHorizontalPadding)
                            .width(120.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = timeFormatter.format(localDateTime),
                                style = MaterialTheme.typography.body1,
                            )
                        }

                    }
                }
            }

            if (isExpanded) {
                SelectTimeViewDeltaButtons(
                    date = date,
                    setDate = setDate,
                    lastStopTime = lastStopTime,
                    unsetText = unsetText,
                    buttonColors = outlinedBoringButtonColors,
                    allowEditing = allowEditing
                )
            }
        }
    }


    MaterialDialog(
        dialogState = dateDialog,
        buttons = {
            positiveButton("Ok")
            negativeButton("Cancel")
        }
    ) {
        datepicker(initialDate = localDate) { date ->
            val localDateTime = LocalDateTime.of(date, localTime)
            val instant = localDateTime.toInstant(zoneId.rules.getOffset(localDateTime))
            setDate(instant)
        }
    }

    MaterialDialog(
        dialogState = timeDialog,
        buttons = {
            positiveButton("Ok")
            negativeButton("Cancel")
        }
    ) {
        timepicker(initialTime = localTime) { time ->
            val localDateTime = LocalDateTime.of(localDate, time)
            val instant = localDateTime.toInstant(zoneId.rules.getOffset(localDateTime))
            setDate(instant)
        }
    }
}

@Composable
fun SelectTimeViewDeltaButtons(
    date: Instant?,
    setDate: (Instant?) -> Unit,
    lastStopTime: Instant?,
    unsetText: String?,
    buttonColors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    allowEditing: Boolean = true
) {
    Column() {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            for (i in arrayOf<Long?>(-5, -1, 1, 5, null)) {
                Button(
                    onClick = {
                        setDate(
                            if (i == null) Instant.now() else date!!.plusSeconds(
                                60 * i
                            )
                        )
                    },
                    colors = buttonColors,
                    enabled = allowEditing && (i == null || date != null),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = ButtonDefaults.MinHeight,
                            minHeight = ButtonDefaults.MinHeight,
                        )
                        .padding(horizontal = 10.dp),
                ) {
                    Text(text = if (i == null) "now" else (if (i > 0) "+$i" else "$i"))
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (lastStopTime != null) {
                Button(
                    onClick = { setDate(lastStopTime) },
                    colors = buttonColors,
                    enabled = allowEditing,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Text(text = "Last stop time")
                }
            }
            if (unsetText != null) {
                Button(
                    onClick = { setDate(null) },
                    colors = buttonColors,
                    enabled = allowEditing && date != null,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Text(text = unsetText)
                }
            }
        }
    }
}