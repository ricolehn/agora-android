package org.agora.app.ui.settings

import org.agora.app.ui.components.AvatarRings
import org.agora.app.ui.components.rememberSheetController
import org.agora.app.ui.components.SheetActions
import org.agora.app.ui.components.AgoraSheet
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Policy
import org.agora.app.ui.components.PrimaryButton
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.AgoraTextField
import org.agora.app.ui.components.AgoraSwitch
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import kotlinx.coroutines.launch
import org.agora.app.BuildConfig
import org.agora.app.R
import org.agora.app.data.local.ThemeMode
import org.agora.app.data.model.CalendarFeed
import org.agora.app.data.model.NotificationSettings
import org.agora.app.data.model.User
import org.agora.app.data.model.parseAmount
import org.agora.app.push.Notifier
import org.agora.app.ui.components.AgoraCard
import org.agora.app.ui.components.AmountField
import org.agora.app.ui.components.ConfirmSheet
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.SectionTitle
import org.agora.app.ui.components.UserAvatar
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Images
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import org.agora.app.ui.components.AgoraSnackbar
import org.agora.app.ui.components.CapsLabel
import org.agora.app.ui.components.PillTabs
import org.agora.app.ui.components.SubPageHeader
import java.util.Locale
import kotlin.random.Random

fun openInBrowser(context: Context, url: String) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Agora", text))
}

@Composable
private fun SwitchItem(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Bold, color = Agora.colors.heading) },
        supportingContent = { Text(subtitle) },
        trailingContent = { AgoraSwitch(checked, onChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

/** Settings card with its heading inside (`.card-header`, weight 800) like the PWA. */
@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    titlePadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) = AgoraCard(contentPadding = contentPadding) {
    Row(Modifier.padding(horizontal = titlePadding).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, Modifier.padding(end = 10.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleLarge, color = Agora.colors.heading)
    }
    content()
}

/** Dashed outline of the PWA's invite code box. */
private fun Modifier.dashedBorder(color: Color, radius: Dp) = drawBehind {
    val stroke = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
    drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(radius.toPx()))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(user: User, onBack: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = rememberActionRunner()
    val snackbar = LocalSnackbar.current
    val data by container.store.data.collectAsStateWithLifecycle()
    val theme by container.sessionStore.theme.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    val pushStatus by container.push.status.collectAsStateWithLifecycle()
    var avatarVersion by remember { mutableIntStateOf(0) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var feed by remember { mutableStateOf<CalendarFeed?>(null) }
    var confirmFeedReset by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var oldPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var notifPermission by remember { mutableStateOf(Notifier.canNotify(context)) }
    val prefs = user.effectiveNotifications
    val showFinancePref = user.canViewFinances || user.isAdmin

    val savedMsg = stringResource(R.string.saved)
    val uploadFailed = stringResource(R.string.error_upload)
    val pictureSaved = stringResource(R.string.profile_pic_saved)
    val copied = stringResource(R.string.copied)
    val pwChanged = stringResource(R.string.password_changed)
    val testSent = stringResource(R.string.push_test_sent)

    LaunchedEffect(Unit) {
        container.push.refreshStatus()
        feed = runCatching { container.repo.calendarFeed() }.getOrNull()
        if (user.canManageRegistrationCode) inviteCode = container.repo.inviteCode()
    }

    // A picked picture goes through the round crop step before it is uploaded
    var cropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> cropUri = uri }
    cropUri?.let { uri ->
        org.agora.app.ui.components.ImageCropDialog(uri, aspect = 1f, circle = true, onDismiss = { cropUri = null }, onCrop = { region ->
            cropUri = null
            runner.run(pictureSaved) {
                val jpeg = Images.profilePicture(context, uri, region) ?: throw org.agora.app.data.remote.ApiException(0, uploadFailed)
                container.repo.uploadProfilePicture(jpeg)
                // The old picture may still sit in the HTTP cache (the server lets clients keep pictures for a few minutes)
                container.api.clearHttpCache()
                val url = container.repo.profilePictureUrl(user.userId)
                SingletonImageLoader.get(context).memoryCache?.remove(MemoryCache.Key(url))
                SingletonImageLoader.get(context).diskCache?.remove(url)
                avatarVersion++
            }
        })
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifPermission = it }

    fun saveNotifications(settings: NotificationSettings) = runner.run {
        container.repo.saveNotificationSettings(user.userId, settings)
        container.store.reloadUser()
    }

    Scaffold(
        topBar = {
            SubPageHeader(stringResource(R.string.nav_settings), onBack)
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) { AgoraSnackbar(it) } },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, padding.calculateTopPadding() + 4.dp, 20.dp, padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile
            item {
                SettingsCard(stringResource(R.string.profile_picture)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.runtime.key(avatarVersion) { UserAvatar(user.userId, user.fullName, 72.dp, ring = AvatarRings.of(user)) }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(user.fullName, style = MaterialTheme.typography.titleMedium)
                            Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    SecondaryButton(onClick = { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !runner.busy, modifier = Modifier.padding(top = 12.dp)) {
                        Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp))
                        Text(" " + stringResource(R.string.btn_upload_pic))
                    }
                    Text(stringResource(R.string.profile_pic_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Invite code
            if (user.canManageRegistrationCode) item {
                SettingsCard(stringResource(R.string.invite_title), Icons.Outlined.Lock) {
                  Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Agora.colors.surfaceAlt)
                        .dashedBorder(MaterialTheme.colorScheme.outline, 16.dp)
                        .padding(18.dp)
                  ) {
                    CapsLabel(stringResource(R.string.invite_current))
                    Text(inviteCode ?: "…", style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 6.sp), fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black, color = Agora.colors.heading, modifier = Modifier.padding(top = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        SecondaryButton(onClick = { inviteCode?.let { copyToClipboard(context, it); scope.launch { snackbar.showSnackbar(copied) } } },
                            enabled = inviteCode != null) {
                            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                            Text(" " + stringResource(R.string.copy_btn))
                        }
                        SecondaryButton(onClick = {
                            val code = Random.nextInt(100000, 1000000).toString()
                            runner.run(savedMsg) { container.repo.setInviteCode(code); inviteCode = code }
                        }, enabled = !runner.busy) {
                            Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                            Text(" " + stringResource(R.string.btn_generate_new))
                        }
                    }
                  }
                }
            }

            // Notifications & push
            item {
                SettingsCard(stringResource(R.string.notif_channels_title), Icons.Outlined.NotificationsNone, contentPadding = PaddingValues(top = 18.dp, bottom = 8.dp), titlePadding = 18.dp) {
                    SwitchItem(stringResource(R.string.notif_duties_title), stringResource(R.string.notif_duties_desc), prefs.duties, !runner.busy) {
                        saveNotifications(prefs.copy(duties = it))
                    }
                    SwitchItem(stringResource(R.string.notif_events_title), stringResource(R.string.notif_events_desc), prefs.events, !runner.busy) {
                        saveNotifications(prefs.copy(events = it))
                    }
                    SwitchItem(stringResource(R.string.notif_messages_title), stringResource(R.string.notif_messages_desc), prefs.messages, !runner.busy) {
                        saveNotifications(prefs.copy(messages = it))
                    }
                    if (showFinancePref) SwitchItem(stringResource(R.string.notif_finances_title), stringResource(R.string.notif_finances_desc), prefs.finances, !runner.busy) {
                        saveNotifications(prefs.copy(finances = it))
                    }
                }
            }
            item {
                AgoraCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, null, tint = if (pushStatus.registered) Agora.colors.success else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.push_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 10.dp))
                    }
                    val statusText = when {
                        pushStatus.registered -> stringResource(R.string.push_active)
                        pushStatus.serverEnabled == false -> stringResource(R.string.push_server_disabled)
                        else -> stringResource(R.string.push_not_set_up)
                    }
                    Text(statusText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    if (!pushStatus.registered) Text(stringResource(R.string.push_fallback_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    pushStatus.error?.let { Text(stringResource(R.string.push_error, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        when {
                            !notifPermission && Build.VERSION.SDK_INT >= 33 -> PrimaryButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text(stringResource(R.string.allow_notifications))
                            }
                            pushStatus.serverEnabled != false -> SecondaryButton(onClick = { runner.run { container.push.sync() } }, enabled = !runner.busy) {
                                Text(stringResource(if (pushStatus.registered) R.string.push_reconnect else R.string.push_set_up))
                            }
                        }
                        if (pushStatus.registered) SecondaryButton(onClick = { runner.run(testSent) { container.repo.testPush() } }, enabled = !runner.busy) {
                            Text(stringResource(R.string.push_test))
                        }
                    }
                }
            }

            // Appearance & language
            item {
                SettingsCard(stringResource(R.string.appearance), Icons.Outlined.Palette) {
                    CapsLabel(stringResource(R.string.theme_title))
                    val modes = ThemeMode.entries
                    PillTabs(
                        modes.map { mode ->
                            stringResource(when (mode) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }) to
                                when (mode) { ThemeMode.SYSTEM -> Icons.Outlined.PhoneAndroid; ThemeMode.LIGHT -> Icons.Outlined.LightMode; ThemeMode.DARK -> Icons.Outlined.DarkMode }
                        },
                        selected = modes.indexOf(theme), onSelect = { i -> scope.launch { container.sessionStore.setTheme(modes[i]) } },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    CapsLabel(stringResource(R.string.settings_language), Modifier.padding(top = 18.dp))
                    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').ifBlank { "system" }
                    val languages = listOf("system", "de", "en")
                    PillTabs(
                        languages.map { lang -> (when (lang) { "de" -> "Deutsch"; "en" -> "English"; else -> stringResource(R.string.theme_system) }) to null },
                        selected = languages.indexOf(current).coerceAtLeast(0),
                        onSelect = { i ->
                            val lang = languages[i]
                            AppCompatDelegate.setApplicationLocales(if (lang == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang))
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Monthly fees (admins)
            if (user.isAdmin) item {
                SectionTitle(stringResource(R.string.monthly_fees))
                FeeEditor(data.fees.rates) { rates -> runner.run(savedMsg) { container.repo.saveFeeSettings(rates); container.store.refreshAll() } }
            }

            // Password
            item {
                SettingsCard(stringResource(R.string.change_password_title), Icons.Outlined.Key) {
                    AgoraTextField(oldPassword, { oldPassword = it }, label = { Text(stringResource(R.string.old_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth())
                    AgoraTextField(newPassword, { newPassword = it }, label = { Text(stringResource(R.string.new_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = { Text(stringResource(R.string.password_min6)) }, modifier = Modifier.fillMaxWidth())
                    PrimaryButton(onClick = {
                        runner.run(pwChanged) {
                            container.repo.changePassword(oldPassword, newPassword)
                            // The server invalidates the token: sign in again with the new password
                            container.store.logout(remote = false)
                        }
                    }, enabled = oldPassword.isNotBlank() && newPassword.length >= 6 && !runner.busy, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.change_password_title))
                    }
                }
            }

            // Calendar subscription
            item {
                SettingsCard(stringResource(R.string.calendar_sub_title), Icons.Outlined.CalendarMonth) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.calendar_sub_desc), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                    }
                    val f = feed
                    if (f != null) {
                        AgoraTextField(f.feedUrl, {}, readOnly = true, label = { Text(stringResource(R.string.calendar_sub_feed_url)) }, singleLine = true,
                            trailingIcon = { IconButton(onClick = { copyToClipboard(context, f.feedUrl); scope.launch { snackbar.showSnackbar(copied) } }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy_btn)) } },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SecondaryButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(f.webcalUrl))) }
                                    .onFailure { openInBrowser(context, "https://calendar.google.com/calendar/r?cid=" + Uri.encode(f.webcalUrl)) }
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.calendar_sub_btn_device)) }
                            SecondaryButton(onClick = { openInBrowser(context, "https://calendar.google.com/calendar/r?cid=" + Uri.encode(f.webcalUrl)) },
                                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.calendar_sub_btn_google)) }
                            TextButton(onClick = { confirmFeedReset = true }) { Text(stringResource(R.string.calendar_sub_btn_reset)) }
                        }
                    }
                }
            }

            // Account
            item {
                SettingsCard(stringResource(R.string.account), Icons.Outlined.AccountCircle, contentPadding = PaddingValues(top = 18.dp, bottom = 8.dp), titlePadding = 18.dp) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.open_in_browser)) },
                        supportingContent = { Text(container.api.baseUrl) },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable { openInBrowser(context, container.api.baseUrl) }
                    )
                    if (user.isAdmin) {
                        HorizontalDivider()
                        TextButton(onClick = { openInBrowser(context, container.api.baseUrl + "/#super-admin-settings") }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                            Text(" " + stringResource(R.string.nav_superadmin_settings))
                        }
                    }
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.privacy_policy)) },
                        leadingContent = { Icon(Icons.Outlined.Policy, null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable { openInBrowser(context, container.api.baseUrl + "/privacy") }
                    )
                    HorizontalDivider()
                    TextButton(onClick = { scope.launch { container.store.logout() } }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text(" " + stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
                    }
                    // The owner account cannot be deleted (the server refuses it as well)
                    if (!user.owner && !user.superAdmin) TextButton(onClick = { deletingAccount = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Outlined.DeleteForever, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Text("Agora Android ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
            }
        }
    }

    if (confirmFeedReset) ConfirmSheet(
        title = stringResource(R.string.calendar_sub_btn_reset),
        text = stringResource(R.string.calendar_reset_confirm),
        confirmLabel = stringResource(R.string.calendar_sub_btn_reset),
        onConfirm = { runner.run(savedMsg) { feed = container.repo.resetCalendarFeed() } },
        onDismiss = { confirmFeedReset = false },
        icon = Icons.Outlined.Refresh
    )
    if (deletingAccount) DeleteAccountSheet(onDismiss = { deletingAccount = false })
}

/** Deletes the own account after password confirmation (Google Play account deletion policy), then logs out. */
@Composable
private fun DeleteAccountSheet(onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val sheet = rememberSheetController(onDismiss)
    val runner = rememberActionRunner()
    var password by remember { mutableStateOf("") }
    val deletedMsg = stringResource(R.string.delete_account_done)
    AgoraSheet(
        title = stringResource(R.string.delete_account),
        icon = Icons.Outlined.DeleteForever,
        iconTint = Agora.colors.danger,
        onDismiss = onDismiss,
        controller = sheet,
        actions = {
            SheetActions(
                confirmLabel = stringResource(R.string.delete_account_confirm_btn),
                onConfirm = {
                    runner.run(deletedMsg) {
                        container.repo.deleteAccount(password)
                        sheet.dismiss()
                        container.store.logout(remote = false)
                    }
                },
                onCancel = { sheet.dismiss() },
                enabled = password.isNotBlank(),
                busy = runner.busy,
                destructive = true
            )
        }
    ) {
        Text(stringResource(R.string.delete_account_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AgoraTextField(
            password, { password = it },
            label = { Text(stringResource(R.string.delete_account_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FeeEditor(rates: Map<String, Double>, onSave: (Map<String, Double>) -> Unit) {
    val keys = listOf("vollverdiener" to R.string.member_status_full, "geringverdiener" to R.string.member_status_low, "keinverdiener" to R.string.member_status_none)
    val values = keys.map { (key, _) -> rememberSaveable(rates[key]) { mutableStateOf(rates[key]?.let { String.format(Locale.GERMANY, "%.2f", it) } ?: "") } }
    AgoraCard {
        keys.forEachIndexed { i, (_, label) ->
            AmountField(stringResource(label), values[i].value, { values[i].value = it }, Modifier.padding(bottom = 8.dp))
        }
        PrimaryButton(onClick = {
            onSave(keys.mapIndexedNotNull { i, (key, _) -> parseAmount(values[i].value)?.let { key to it } }.toMap())
        }) { Text(stringResource(R.string.save)) }
    }
}
