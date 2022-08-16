package nz.ac.uclive.rog19.seng440.assignment1.components

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nz.ac.uclive.rog19.seng440.assignment1.ApiRequest
import nz.ac.uclive.rog19.seng440.assignment1.model.Me
import nz.ac.uclive.rog19.seng440.assignment1.newlineEtAlRegex
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.TimeTrackerTheme


@Composable
fun LoginView(
    apiRequest: ApiRequest,
    onLogin: ((Me) -> Unit)? = null
) {
    var email by remember { mutableStateOf(TextFieldValue("")) }
    var password by remember { mutableStateOf(TextFieldValue("")) }
    var errorMessage by remember { mutableStateOf("") }
    var requestInProgress by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        TextField(
            value = email,
            label = { Text(text = "Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            onValueChange = {
                if (it.text.contains(newlineEtAlRegex)) {
                    focusManager.moveFocus(FocusDirection.Next)
                } else email = it
            },
            maxLines = 1,
            singleLine = true
        )

        TextField(
            value = password,
            label = { Text(text = "Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChange = {
                if (it.text.contains(newlineEtAlRegex)) {
                    focusManager.clearFocus()
                } else password = it
            },
        )

        Button(
            onClick = {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        requestInProgress = true
                        try {
                            apiRequest.authenticate(
                                email = email.text.trim(),
                                password = password.text
                            )?.let {
                                onLogin?.invoke(it)
                            } ?: run { errorMessage = "JSON could not be parsed" }
                        } catch (exception: Throwable) {
                            errorMessage = exception.message ?: exception.toString()
                        } finally {
                            requestInProgress = false
                        }
                    }
                }
            },
            enabled = !requestInProgress &&
                    email.text.trim().isNotEmpty() &&
                    Patterns.EMAIL_ADDRESS.matcher(email.text.trim()).matches() &&
                    password.text.length > 8
        ) {
            Text(text = "Login")
        }

        if (requestInProgress) {
            LinearProgressIndicator()
        }

        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, textAlign = TextAlign.Center)
        }

        OutlinedButton(onClick = {
            uriHandler.openUri("https://toggl.com/track/signup")
        }) {
            Text("Create a Toggl Track Account")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Login_Preview() {
    TimeTrackerTheme {
        LoginView(apiRequest = ApiRequest())
    }
}