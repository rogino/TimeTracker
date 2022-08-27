package nz.ac.uclive.rog19.seng440.assignment1.components

import android.content.Context
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nz.ac.uclive.rog19.seng440.assignment1.*
import nz.ac.uclive.rog19.seng440.assignment1.PaddingValues
import nz.ac.uclive.rog19.seng440.assignment1.R
import nz.ac.uclive.rog19.seng440.assignment1.model.Me
import nz.ac.uclive.rog19.seng440.assignment1.ui.theme.AppTheme

class LoginPageViewModel : ViewModel() {
    var email by mutableStateOf(TextFieldValue(""))
    var password by mutableStateOf(TextFieldValue(""))
    var errorMessage by mutableStateOf("")
    var requestInProgress by mutableStateOf(false)
    var passwordVisible by mutableStateOf(false)
}

@Composable
fun LoginView(
    apiRequest: ApiRequest,
    context: Context,
    contentPadding: PaddingValues = PaddingValues(),
    vm: LoginPageViewModel = viewModel(),
    onLogin: ((Me) -> Unit)? = null,
) {

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    var parentSize by remember { mutableStateOf(IntSize.Zero) }

    @Composable
    fun spacing() {
        Spacer(modifier = Modifier.height(20.dp))
    }

    @Composable
    fun halfSpacing() {
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
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false)
        ) {
            /// Portrait and height greater than some minimum
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

            spacing()

            Text(
                text = stringResource(R.string.login_page_title),
                style = MaterialTheme.typography.h6
            )

            spacing()

            var emailFocused by remember { mutableStateOf(false) }
            TextField(
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
                modifier = Modifier.onFocusChanged { emailFocused = it.hasFocus }
            )

            spacing()

            var passwordFocused by remember { mutableStateOf(false) }
            TextField(
                value = vm.password,
                label = { Text(text = stringResource(R.string.password)) },
                visualTransformation = if (vm.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                trailingIcon = {
                    if (passwordFocused) {
                        IconButton(onClick = {
                            vm.passwordVisible = !vm.passwordVisible
                        }) {
                            Icon(
                                painter = painterResource(
                                    if (vm.passwordVisible) R.drawable.ic_baseline_visibility_24
                                    else R.drawable.ic_baseline_visibility_off_24
                                ),
                                tint = MaterialTheme.colors.primary,
                                contentDescription = stringResource(
                                    if (vm.passwordVisible) R.string.hide_password else
                                        R.string.show_password
                                )
                            )
                        }
                    }
                },
                onValueChange = {
                    if (it.text.contains(newlineEtAlRegex)) {
                        focusManager.clearFocus()
                    } else vm.password = it
                },
                modifier = Modifier.onFocusChanged { passwordFocused = it.hasFocus }
            )

            Column(modifier = Modifier.height(20.dp), verticalArrangement = Arrangement.Center) {
                if (vm.requestInProgress) {
                    LinearProgressIndicator()
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
                        vm.password.text.length > 8
            ) {
                Text(text = stringResource(R.string.login))
            }

            halfSpacing()

            if (vm.errorMessage.isNotEmpty()) {
                Text(text = vm.errorMessage, textAlign = TextAlign.Center)
                halfSpacing()
            }


            OutlinedButton(onClick = {
                uriHandler.openUri(context.resources.getString(R.string.toggl_track_signup_url))
            }) {
                Text(stringResource(R.string.create_toggl_account))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Login_Preview() {
    AppTheme {
        LoginView(
            apiRequest = ApiRequest(),
            context = LocalContext.current,
        )
    }
}