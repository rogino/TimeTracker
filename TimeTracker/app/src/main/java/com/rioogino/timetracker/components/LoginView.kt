package com.rioogino.timetracker.components

import android.annotation.SuppressLint
import android.content.Context
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.* // Keep this for other layout components
import androidx.compose.foundation.layout.WindowInsets // Explicit import for WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface // Added for Debug Preview
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel // Re-added
import com.rioogino.timetracker.ApiRequest
import com.rioogino.timetracker.R
import com.rioogino.timetracker.model.Me
import com.rioogino.timetracker.ui.theme.AppTheme
// Using standard Compose PaddingValues
import androidx.compose.foundation.layout.PaddingValues
import kotlinx.coroutines.Dispatchers // Added
import kotlinx.coroutines.launch // Added
import kotlinx.coroutines.withContext // Added

// Define the regex for newline characters
val newlineEtAlRegex = Regex("[\n\r\t]")

class LoginPageViewModel : ViewModel() {
    var email by mutableStateOf(TextFieldValue(""))
    var password by mutableStateOf(TextFieldValue(""))
    var errorMessage by mutableStateOf("")
    var requestInProgress by mutableStateOf(false)
    var passwordVisible by mutableStateOf(false)
}

@Composable
fun TextFieldClearButton(
    textFieldValue: TextFieldValue,
    clear: () -> Unit,
    isFocused: Boolean
) {
    if (isFocused && textFieldValue.text.isNotEmpty()) {
        IconButton(onClick = clear) {
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = stringResource(R.string.clear_text_field) // Add to strings.xml
            )
        }
    }
}

@Composable
fun LoginView(
    apiRequest: ApiRequest,
    context: Context,
    contentPadding: PaddingValues = PaddingValues(),
    vm: LoginPageViewModel = viewModel(), // Restored to viewModel()
    onLogin: ((Me) -> Unit)? = null,
) {

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    var parentSize by remember { mutableStateOf(IntSize.Zero) }

    @Composable
    fun Spacing() {
        Spacer(modifier = Modifier.height(20.dp))
    }

    @Composable
    fun HalfSpacing() {
        Spacer(modifier = Modifier.height(10.dp))
    }

    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                parentSize = coordinates.size
            }
            .padding(contentPadding)
            .padding(WindowInsets.ime.asPaddingValues())
            .padding(horizontal = 16.dp)
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false)
        ) {
            if (parentSize.width.toFloat() / parentSize.height.toFloat() < 1f &&
                with(density) {
                    parentSize.height.toDp()
                } > 500.dp
            ) {
                Image(
                    painterResource(R.drawable.logo_no_margins_512),
                    contentDescription = stringResource(R.string.icon),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .sizeIn(maxHeight = 100.dp)
                )
            }

            Spacing()

            Text(
                text = stringResource(R.string.login_page_title),
                style = MaterialTheme.typography.titleLarge
            )

            Spacing()

            var emailFocused by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = vm.email,
                label = { Text(text = stringResource(R.string.email)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                keyboardActions = KeyboardActions(onDone = { focusManager.moveFocus(FocusDirection.Next) }),
                trailingIcon = {
                    TextFieldClearButton(
                        textFieldValue = vm.email,
                        clear = { vm.email = TextFieldValue() },
                        isFocused = emailFocused
                    )
                },
                onValueChange = {
                    if (it.text.contains(newlineEtAlRegex)) {
                        focusManager.moveFocus(FocusDirection.Down)
                    } else vm.email = it
                },
                maxLines = 1,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { emailFocused = it.hasFocus }
            )

            Spacing()

            var passwordFocused by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = vm.password,
                label = { Text(text = stringResource(R.string.password)) },
                visualTransformation = if (vm.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                trailingIcon = {
                    if (passwordFocused) { // Only show visibility toggle when focused, clear button is separate
                        IconButton(onClick = {
                            vm.passwordVisible = !vm.passwordVisible
                        }) {
                            Icon(
                                painter = painterResource(
                                    if (vm.passwordVisible) R.drawable.ic_baseline_visibility_24
                                    else R.drawable.ic_baseline_visibility_off_24
                                ),
                                contentDescription = stringResource(
                                    if (vm.passwordVisible) R.string.hide_password else
                                        R.string.show_password
                                )
                            )
                        }
                    } else if (vm.password.text.isNotEmpty()){ // Show clear button if not focused but has text
                         TextFieldClearButton(
                            textFieldValue = vm.password,
                            clear = { vm.password = TextFieldValue() },
                            isFocused = true // Effectively true for showing button
                        )
                    }
                },
                onValueChange = {
                    if (it.text.contains(newlineEtAlRegex)) {
                        focusManager.clearFocus()
                    } else vm.password = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { passwordFocused = it.hasFocus }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (vm.requestInProgress) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        focusManager.clearFocus()
                        withContext(Dispatchers.IO) {
                            vm.requestInProgress = true
                            try {
                                apiRequest.authenticate(
                                    email = vm.email.text.trim(),
                                    password = vm.password.text
                                )?.let {
                                    withContext(Dispatchers.Main) {
                                        onLogin?.invoke(it)
                                    }
                                } ?: run {
                                    vm.errorMessage = context.resources.getString(
                                        R.string.error_json_not_parsed
                                    )
                                }
                            } catch (exception: Throwable) {
                                vm.errorMessage =
                                    exception.message ?: exception.toString()
                            } finally {
                                vm.requestInProgress = false
                            }
                        }
                    }
                },
                enabled = !vm.requestInProgress &&
                        vm.email.text.trim().isNotEmpty() &&
                        Patterns.EMAIL_ADDRESS.matcher(vm.email.text.trim())
                            .matches() &&
                        vm.password.text.length > 8, // Assuming M2 password length validation or similar
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.login))
            }

            HalfSpacing()

            if (vm.errorMessage.isNotEmpty()) {
                Text(
                    text = vm.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                HalfSpacing()
            }


            OutlinedButton(
                onClick = {
                    uriHandler.openUri(context.resources.getString(R.string.toggl_track_signup_url))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.create_toggl_account))
            }
            Spacing()
        }
    }
}


@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, name = "Login Light (Forced & VM)")
@Composable
fun Login_Preview_Light_Forced_With_VM() {
    AppTheme(useDarkTheme = false) { // Force light theme
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            val previewVm = LoginPageViewModel().apply {
                email = TextFieldValue("test@example.com")
                // Ensure password is long enough for button enablement
                password = TextFieldValue("password12345")
            }
            LoginView(
                apiRequest = ApiRequest(), // Assuming ApiRequest() is fine for preview
                context = LocalContext.current,
                contentPadding = PaddingValues(16.dp), // Provide some padding
                vm = previewVm // Provide the VM with data
            )
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, name = "Login Dark (Forced & VM)")
@Composable
fun Login_Preview_Dark_Forced_With_VM() {
    AppTheme(useDarkTheme = true) { // Force dark theme
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            val previewVm = LoginPageViewModel().apply {
                email = TextFieldValue("test@example.com")
                password = TextFieldValue("password12345") // Ensure password is long enough
            }
            LoginView(
                apiRequest = ApiRequest(),
                context = LocalContext.current,
                contentPadding = PaddingValues(16.dp), // Provide some padding
                vm = previewVm // Provide the VM with data
            )
        }
    }
}

