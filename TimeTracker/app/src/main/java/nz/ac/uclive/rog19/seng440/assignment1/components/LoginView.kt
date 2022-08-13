package nz.ac.uclive.rog19.seng440.assignment1.components

import android.util.Log
import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import nz.ac.uclive.rog19.seng440.assignment1.ApiRequest
import nz.ac.uclive.rog19.seng440.assignment1.TAG
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

    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
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
            singleLine = true,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        TextField(value = password,
            label = { Text(text = "Password" ) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =  KeyboardOptions(keyboardType = KeyboardType.Password),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChange = {
                if (it.text.contains(newlineEtAlRegex)) {
                    focusManager.clearFocus()
                } else password = it
            },
            modifier = Modifier.padding(bottom = 20.dp)
        )


        Button(onClick = {
            coroutineScope.launch(CoroutineExceptionHandler { _, exception ->
                errorMessage = exception.message ?: exception.toString()
                requestInProgress = false
            }) {
                requestInProgress = true
                val result = apiRequest.authenticate(
                    email = email.text.trim(),
                    password = password.text
                )
                requestInProgress = false
                result?.let {
                    onLogin?.invoke(result)
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

        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage)
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