package org.agora.app.ui.auth

import org.agora.app.ui.components.softShadow
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.AgoraTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import org.agora.app.R
import org.agora.app.data.remote.ApiException
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import org.agora.app.ui.components.BrandName
import org.agora.app.ui.components.PillTabs
import org.agora.app.ui.theme.Agora
import org.agora.app.ui.components.ButtonProgress
import org.agora.app.ui.components.LocalContainer

/** Login page of the PWA: soft cyan/emerald wash, centred white card (`.login-card`). */
@Composable
private fun AuthScaffold(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val wash = Brush.linearGradient(
        listOf(Color(0xFF06B6D4).copy(alpha = 0.16f), scheme.background, Color(0xFF10B981).copy(alpha = 0.14f)),
        start = Offset.Zero, end = Offset.Infinite
    )
    Box(Modifier.fillMaxSize().background(scheme.background).background(wash)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .softShadow(Color(0xFF0F172A).copy(alpha = 0.16f), blur = 24.dp, shape = MaterialTheme.shapes.large, offsetY = 8.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(scheme.surface)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) { content() }
        }
    }
}

/** Church logo from the server (SVG), falling back to the bundled Agora logo. */
@Composable
fun ChurchLogo(size: androidx.compose.ui.unit.Dp) {
    val container = LocalContainer.current
    val fallback = @Composable {
        Image(painterResource(R.drawable.agora_logo), null, Modifier.size(size).clip(MaterialTheme.shapes.medium))
    }
    if (container.api.baseUrl.isBlank()) fallback()
    else SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(container.api.absolute("/assets/church-logo.svg")).build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(size),
        error = { fallback() },
        loading = { fallback() }
    )
}

@Composable
fun ServerScreen(onConnect: suspend (String) -> Unit, initialUrl: String = "") {
    var url by rememberSaveable { mutableStateOf(initialUrl.removePrefix("https://")) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val setupText = stringResource(R.string.server_setup_mode)
    val unreachable = stringResource(R.string.server_unreachable)
    val httpsRequired = stringResource(R.string.server_https_required)
    val scope = rememberCoroutineScope()
    val submit: () -> Unit = { if (url.isNotBlank() && !busy) scope.launch {
        busy = true
        error = null
        try {
            onConnect(url)
        } catch (e: ApiException) {
            error = when (e.status) {
                503 -> setupText
                org.agora.app.data.AppStore.HTTPS_REQUIRED -> httpsRequired
                else -> "$unreachable (${e.message})"
            }
        } catch (e: Exception) {
            error = unreachable
        }
        busy = false
    } }

    AuthScaffold {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.agora_logo), null, Modifier.size(44.dp).clip(MaterialTheme.shapes.small))
            BrandName("Agora", Modifier.padding(start = 12.dp), fontSize = 34.sp)
        }
        Text(stringResource(R.string.server_title), style = MaterialTheme.typography.titleLarge, color = Agora.colors.heading, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.server_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        AgoraTextField(
            value = url,
            onValueChange = { url = it.trim() },
            label = { Text(stringResource(R.string.server_address)) },
            placeholder = { Text("agora.gemeinde.de") },
            leadingIcon = { Icon(Icons.Outlined.Dns, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        PrimaryButton(onClick = submit, enabled = url.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            if (busy) ButtonProgress() else Text(stringResource(R.string.server_connect))
        }
    }
}

@Composable
fun LoginScreen(
    appName: String,
    serverHost: String,
    onLogin: suspend (String, String) -> Unit,
    onRegister: suspend (code: String, email: String, first: String, last: String, password: String) -> Unit,
    onChangeServer: () -> Unit
) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var password2 by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val fillAll = stringResource(R.string.error_fill_all)
    val shortPw = stringResource(R.string.error_password_short)
    val mismatch = stringResource(R.string.error_password_mismatch)
    val generic = stringResource(R.string.error_generic)

    val submit: () -> Unit = { if (!busy) scope.launch submit@{
        error = null
        if (registerMode) {
            when {
                listOf(code, email, firstName, lastName, password, password2).any { it.isBlank() } -> { error = fillAll; return@submit }
                password.length < 6 -> { error = shortPw; return@submit }
                password != password2 -> { error = mismatch; return@submit }
            }
        } else if (email.isBlank() || password.isBlank()) {
            error = fillAll
            return@submit
        }
        busy = true
        try {
            if (registerMode) onRegister(code, email, firstName, lastName, password) else onLogin(email, password)
        } catch (e: ApiException) {
            error = e.message ?: generic
        } catch (e: Exception) {
            error = generic
        }
        busy = false
    } }

    val visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
    val eye = @Composable {
        IconButton(onClick = { showPassword = !showPassword }) {
            Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(R.string.show_password))
        }
    }

    AuthScaffold {
        // `.login-brand`: logo next to the gradient app name
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            ChurchLogo(44.dp)
            BrandName(appName, Modifier.padding(start = 12.dp), fontSize = 34.sp)
        }
        Text(
            stringResource(if (registerMode) R.string.register_subtitle else R.string.login_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        PillTabs(
            listOf(stringResource(R.string.login_tab) to null, stringResource(R.string.register_tab) to null),
            selected = if (registerMode) 1 else 0,
            onSelect = { registerMode = it == 1; error = null },
            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
        )
        if (registerMode) {
            AgoraTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.register_code)) },
                leadingIcon = { Icon(Icons.Outlined.Key, null) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth())
        }
        AgoraTextField(email, { email = it.trim() }, label = { Text(stringResource(R.string.email)) }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.MailOutline, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
        if (registerMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AgoraTextField(firstName, { firstName = it }, label = { Text(stringResource(R.string.first_name)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.weight(1f))
                AgoraTextField(lastName, { lastName = it }, label = { Text(stringResource(R.string.last_name)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.weight(1f))
            }
        }
        AgoraTextField(password, { password = it }, label = { Text(stringResource(if (registerMode) R.string.password_min6 else R.string.password)) },
            singleLine = true, visualTransformation = visual, trailingIcon = eye, leadingIcon = { Icon(Icons.Outlined.Lock, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (registerMode) ImeAction.Next else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }), modifier = Modifier.fillMaxWidth())
        if (registerMode) {
            AgoraTextField(password2, { password2 = it }, label = { Text(stringResource(R.string.password_repeat)) },
                singleLine = true, visualTransformation = visual,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }), modifier = Modifier.fillMaxWidth())
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        PrimaryButton(onClick = submit, enabled = !busy, modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(52.dp)) {
            if (busy) ButtonProgress() else Text(stringResource(if (registerMode) R.string.btn_register else R.string.btn_login))
        }
        TextButton(onClick = onChangeServer) {
            Icon(Icons.Outlined.Dns, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("  $serverHost · " + stringResource(R.string.change_server), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
