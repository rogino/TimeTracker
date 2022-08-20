package nz.ac.uclive.rog19.seng440.assignment1.components

import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import nz.ac.uclive.rog19.seng440.assignment1.R
import nz.ac.uclive.rog19.seng440.assignment1.model.Me
import nz.ac.uclive.rog19.seng440.assignment1.newlineEtAlRegex
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.AppTheme


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
    val context = LocalContext.current

    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        Image(
            painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = stringResource(R.string.icon),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .aspectRatio(1f)
        )

        Text(text = stringResource(R.string.login_page_title),
            style = MaterialTheme.typography.h6
        )

        TextField(
            value = email,
            label = { Text(text = stringResource(R.string.email)) },
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
            label = { Text(text = stringResource(R.string.password)) },
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
                                withContext(Dispatchers.Main) {
                                    onLogin?.invoke(it)
                                }
                            } ?: run {
                                errorMessage = context.resources.getString(
                                    R.string.error_json_not_parsed
                                )
                            }
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
            Text(text = stringResource(R.string.login))
        }

        if (requestInProgress) {
            LinearProgressIndicator()
        }

        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, textAlign = TextAlign.Center)
        }

        OutlinedButton(onClick = {
            uriHandler.openUri(context.resources.getString(R.string.toggl_track_signup_url))
        }) {
            Text(stringResource(R.string.create_toggl_account))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Login_Preview() {
    AppTheme {
        LoginView(apiRequest = ApiRequest())
    }
}