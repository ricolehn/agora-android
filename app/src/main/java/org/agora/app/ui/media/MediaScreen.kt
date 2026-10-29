package org.agora.app.ui.media

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.agora.app.R
import org.agora.app.data.MediaFile
import org.agora.app.data.MediaFolder
import org.agora.app.data.UploadSource
import org.agora.app.data.mediaPreviewUrl
import org.agora.app.data.remote.ApiException
import org.agora.app.ui.components.AgoraFabMenu
import org.agora.app.ui.components.AgoraSnackbar
import org.agora.app.ui.components.ConfirmSheet
import org.agora.app.ui.components.EmptyState
import org.agora.app.ui.components.FabAction
import org.agora.app.ui.components.FrostScaffold
import org.agora.app.ui.components.LoadingBox
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.SubPageHeader
import org.agora.app.ui.components.TextInputSheet
import org.agora.app.ui.components.containerViewModel
import org.agora.app.ui.components.frosted
import org.agora.app.ui.components.pagePadding
import org.agora.app.ui.components.rememberActionRunner
import org.agora.app.ui.components.softShadow
import org.agora.app.ui.theme.Agora
import org.agora.app.util.Dates

/** The library's colour (its tile in Extras) and the colours of the file kinds (web: #media .kind-*). */
internal val MediaAccent = Color(0xFFE0874F)
internal fun kindColor(kind: String): Color = when (kind) {
    "pdf" -> Color(0xFFD6667C)
    "video" -> Color(0xFF7C6FD6)
    "audio" -> Color(0xFFE0874F)
    "image" -> Color(0xFF4A98C4)
    else -> Color(0xFF8A837A)
}

internal fun kindIcon(kind: String): ImageVector = when (kind) {
    "pdf" -> Icons.Outlined.PictureAsPdf
    "video" -> Icons.Outlined.Movie
    "audio" -> Icons.Outlined.MusicNote
    "image" -> Icons.Outlined.Image
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

private const val MAX_UPLOAD_FILES = 50
private const val MAX_UPLOAD_BYTES = 1024L * 1024 * 1024

/** "Save as": the system's document picker with a suggested name and type (no storage permission needed). */
internal class SaveAs : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(input.second).putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Name, size and type of a picked file (the content resolver knows them). */
private fun uploadSource(context: Context, uri: Uri): UploadSource {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    var size = -1L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { cursor.getString(it) }?.let { name = it }
            cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let { size = cursor.getLong(it) }
        }
    }
    return UploadSource(uri, name, size, context.contentResolver.getType(uri))
}

/** Hands a downloaded copy to another app (FileProvider), with its type so the right apps offer themselves. */
internal fun openInOtherApp(context: Context, file: java.io.File, type: String, share: Boolean = false) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
    val mime = type.ifBlank { "application/octet-stream" }
    val intent = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
    else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(if (share) Intent.createChooser(intent, null) else intent)
}

/**
 * The media library (Extras → Media) like the web app 3.3: the community's Nextcloud folder with its folders and
 * thumbnails, the path above, "Folders" and "Files". Pictures open in a full-screen viewer, PDFs page by page,
 * videos and music in the player, anything else in another app. Holding an entry selects; the selection downloads
 * as one ZIP or, with write access, is deleted. Back goes up a folder; members with extended media rights set the
 * rights of every folder with the shield.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = LocalContainer.current
    val store = container.store
    val vm = containerViewModel { MediaViewModel(it, context) }
    val page by vm.page.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val transfer by vm.transfer.collectAsStateWithLifecycle()
    val data by store.data.collectAsStateWithLifecycle()
    val canManage = data.media.canManage || page.listing?.canManage == true
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val runner = rememberActionRunner()

    LaunchedEffect(Unit) { if (page.listing == null) vm.open(page.path) else vm.load(silent = true) }
    // Added, deleted or rights changed on another device (live update "media"): the folder follows in place
    LaunchedEffect(Unit) { store.serverChanges.collect { areas -> if ("media" in areas || "all" in areas) vm.load(silent = true) } }
    val renamedText = stringResource(R.string.media_uploaded_renamed)
    LaunchedEffect(vm) {
        vm.notices.collect { notice ->
            val text = notice.message ?: resources.getString(notice.text, *notice.args.toTypedArray())
            snackbar.showSnackbar(if (notice.renamed) "$text $renamedText" else text)
        }
    }

    val listing = page.listing
    val folders = remember(listing, sort) { sortFolders(listing?.folders.orEmpty(), sort) }
    val files = remember(listing, sort) { sortFiles(listing?.files.orEmpty(), sort) }
    val access = listing?.access ?: "none"
    val selecting = selected.isNotEmpty()

    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    var rightsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var overview by rememberSaveable { mutableStateOf(false) }
    var newFolder by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var opening by remember { mutableStateOf(false) }

    BackHandler(selecting) { vm.clearSelection() }
    BackHandler(!selecting && page.path.isNotEmpty()) { vm.open(parentPath(page.path)) }

    // Downloads go where the person picks (no storage permission); the address waits while the picker is open
    var pendingDownload by rememberSaveable { mutableStateOf<String?>(null) }
    val saveAs = rememberLauncherForActivityResult(SaveAs()) { uri ->
        val url = pendingDownload?.toHttpUrlOrNull()
        pendingDownload = null
        if (uri != null && url != null) vm.downloadTo(context.contentResolver, uri, url)
    }
    fun downloadFile(file: MediaFile) {
        pendingDownload = vm.fileUrl(file).toString()
        saveAs.launch(file.name to file.contentType.ifBlank { "application/octet-stream" })
    }
    val preparing = stringResource(R.string.media_download_preparing)
    fun downloadSelection(names: List<String>) {
        // One file: as it is; folders or several entries: one ZIP the server puts together
        val single = files.firstOrNull { names.size == 1 && it.name == names[0] }
        if (single != null) return downloadFile(single)
        runner.run {
            scope.launch { snackbar.showSnackbar(preparing) }
            val url = vm.prepareZip(names)
            snackbar.currentSnackbarData?.dismiss()
            val base = if (names.size == 1 && folders.any { it.name == names[0] }) names[0]
            else page.path.takeIf { it.isNotEmpty() }?.let(::folderName) ?: resources.getString(R.string.media_title)
            pendingDownload = url.toString()
            saveAs.launch("$base.zip" to "application/zip")
        }
    }
    val noApp = stringResource(R.string.media_no_app)
    fun openElsewhere(file: MediaFile, share: Boolean = false) {
        if (opening) return
        opening = true
        scope.launch {
            try {
                openInOtherApp(context, vm.localCopy(file), file.contentType, share)
            } catch (e: CancellationException) {
                throw e
            } catch (_: ActivityNotFoundException) {
                snackbar.showSnackbar(noApp)
            } catch (e: Exception) {
                snackbar.showSnackbar((e as? ApiException)?.message?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.media_download_failed))
            } finally {
                opening = false
            }
        }
    }

    val tooMany = stringResource(R.string.media_too_many)
    val tooLarge = stringResource(R.string.media_too_large)
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val sources = uris.map { uploadSource(context, it) }
        when {
            sources.size > MAX_UPLOAD_FILES -> scope.launch { snackbar.showSnackbar(tooMany) }
            sources.any { it.size > MAX_UPLOAD_BYTES } -> scope.launch { snackbar.showSnackbar(tooLarge) }
            else -> vm.upload(context.contentResolver, sources)
        }
    }

    val openEntry: (String) -> Unit = { name ->
        val folder = folders.firstOrNull { it.name == name }
        val file = files.firstOrNull { it.name == name }
        when {
            selecting -> vm.toggle(name)
            folder != null -> vm.open(folder.path)
            file == null -> Unit
            opensInViewer(file) || opensAsPdf(file) || opensInPlayer(file) -> viewing = file.name
            else -> openElsewhere(file)
        }
    }

    val title = if (page.path.isEmpty()) stringResource(R.string.media_title) else folderName(page.path)
    FrostScaffold(
        topBar = {
            if (selecting) SelectionHeader(
                count = selected.size,
                allSelected = selected.size >= folders.size + files.size,
                canDelete = canDeleteSelection(access, folders, selected),
                showDelete = access == "write",
                onClear = vm::clearSelection,
                onToggleAll = { if (selected.size >= folders.size + files.size) vm.clearSelection() else vm.selectAll(folders.map { it.name } + files.map { it.name }) },
                onDownload = { downloadSelection(selected.toList()) },
                onDelete = { confirmDelete = selected.toList() }
            )
            else SubPageHeader(
                title,
                onBack = { if (page.path.isNotEmpty()) vm.open(parentPath(page.path)) else onBack() },
                actions = {
                    SortMenu(sort, vm::setSort)
                    // Managers: the rights of the open folder (shield) and of every folder that has some (list)
                    if (canManage && listing != null) IconButton(onClick = { rightsFor = page.path }) {
                        Icon(Icons.Outlined.Shield, stringResource(R.string.media_rights_title), tint = Agora.colors.heading)
                    }
                    if (canManage) IconButton(onClick = { overview = true }) {
                        Icon(Icons.AutoMirrored.Outlined.ListAlt, stringResource(R.string.media_rules_overview), tint = Agora.colors.heading)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) { AgoraSnackbar(it) } }
    ) { padding ->
        val density = LocalDensity.current
        val screenWidth = LocalConfiguration.current.screenWidthDp
        val phone = screenWidth < 600
        // Thumbnails as sharp as the tile (about a third of the screen on phones), from the server's fixed sizes
        val thumb = with(density) { thumbSize(((if (phone) screenWidth / 3 else 200).dp).roundToPx()) }
        val grid = rememberLazyGridState()
        PrefetchPreviews(files, grid) { container.repo.mediaPreviewUrl(it, thumb, crop = true) }
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                // Three pictures side by side on phones
                columns = if (phone) GridCells.Fixed(3) else GridCells.Adaptive(150.dp),
                state = grid,
                modifier = Modifier.fillMaxSize(),
                contentPadding = pagePadding(padding.calculateTopPadding() + 4.dp, padding.calculateBottomPadding() + 112.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }
                item(key = "crumbs", span = full) {
                    Crumbs(page.path, access = if (listing == null) null else access, showWrite = !canManage) { vm.open(it) }
                }
                when {
                    page.error != null -> item(key = "error", span = full) {
                        EmptyState(
                            Icons.Outlined.FolderOpen,
                            if (page.error == MediaViewModel.UNAVAILABLE) stringResource(R.string.media_unavailable)
                            else page.error?.takeIf { it.isNotBlank() } ?: stringResource(R.string.media_load_failed)
                        ) { SecondaryButton(onClick = { vm.load() }) { Text(stringResource(R.string.media_retry)) } }
                    }
                    listing == null -> item(key = "loading", span = full) { LoadingBox(Modifier.fillMaxWidth().padding(top = 48.dp)) }
                    folders.isEmpty() && files.isEmpty() -> item(key = "empty", span = full) {
                        EmptyState(
                            Icons.Outlined.FolderOpen,
                            stringResource(R.string.media_empty),
                            subtitle = if (access == "write") stringResource(R.string.media_empty_hint) else null
                        )
                    }
                    else -> {
                        if (folders.isNotEmpty()) {
                            item(key = "folders-head", span = full) { SectionLabel(stringResource(R.string.media_section_folders), folders.size) }
                            items(folders, key = { "d:" + it.name }, span = { GridItemSpan(if (maxLineSpan >= 4) maxLineSpan / 2 else maxLineSpan) }) { folder ->
                                FolderCard(
                                    folder,
                                    selected = folder.name in selected,
                                    selecting = selecting,
                                    canManage = canManage,
                                    onOpen = { openEntry(folder.name) },
                                    onSelect = { vm.toggle(folder.name) },
                                    onRights = { rightsFor = folder.path },
                                    // Only moving, no fading: the fade's layer is cut to the card and would swallow its shadow
                                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                                )
                            }
                        }
                        if (files.isNotEmpty()) {
                            item(key = "files-head", span = full) { SectionLabel(stringResource(R.string.media_section_files), files.size) }
                            items(files, key = { "f:" + it.name }) { file ->
                                FileTile(
                                    file,
                                    previewUrl = if (file.hasPreview) container.repo.mediaPreviewUrl(file, thumb, crop = true) else null,
                                    selected = file.name in selected,
                                    selecting = selecting,
                                    onOpen = { openEntry(file.name) },
                                    onSelect = { vm.toggle(file.name) },
                                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                                )
                            }
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding())) {
                TransferBanner(transfer, opening, onCancel = vm::cancelTransfer)
            }
            if (access == "write" && !selecting) AgoraFabMenu(
                listOf(
                    FabAction(stringResource(R.string.media_upload), Icons.Outlined.UploadFile) { pickFiles.launch(arrayOf("*/*")) },
                    FabAction(stringResource(R.string.media_new_folder), Icons.Outlined.CreateNewFolder) { newFolder = true }
                ),
                stringResource(R.string.media_upload),
                Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = padding.calculateBottomPadding())
            )
        }
    }

    viewing?.let { name ->
        val file = files.firstOrNull { it.name == name }
        when {
            file == null -> viewing = null
            opensInViewer(file) -> {
                val pictures = files.filter(::opensInViewer)
                PictureViewer(
                    pictures,
                    start = pictures.indexOfFirst { it.name == name }.coerceAtLeast(0),
                    onClose = { viewing = null },
                    onDownload = ::downloadFile,
                    onShare = { openElsewhere(it, share = true) }
                )
            }
            opensAsPdf(file) -> PdfViewer(file, vm, onClose = { viewing = null }, onDownload = { downloadFile(file) }, onOpenElsewhere = { openElsewhere(file) })
            else -> PlayerViewer(file, vm.fileUrl(file, download = false), onClose = { viewing = null }, onDownload = { downloadFile(file) })
        }
    }

    if (newFolder) {
        val created = stringResource(R.string.media_folder_created)
        TextInputSheet(
            title = stringResource(R.string.media_new_folder),
            subtitle = stringResource(R.string.media_new_folder_sub),
            label = stringResource(R.string.media_folder_name),
            confirmLabel = stringResource(R.string.media_create),
            icon = Icons.Outlined.CreateNewFolder,
            iconTint = MediaAccent,
            onDismiss = { newFolder = false },
            onConfirm = { name -> if (name.isNotBlank()) runner.run(created) { vm.createFolder(name) } }
        )
    }

    confirmDelete?.let { names ->
        val single = names.singleOrNull()
        val deletedOne = stringResource(R.string.media_deleted_one)
        ConfirmSheet(
            title = stringResource(R.string.media_delete),
            text = when {
                single == null -> stringResource(R.string.media_delete_confirm_many, names.size)
                folders.any { it.name == single } -> stringResource(R.string.media_delete_confirm_folder, single)
                else -> stringResource(R.string.media_delete_confirm_file, single)
            },
            confirmLabel = stringResource(R.string.media_delete),
            destructive = true,
            onConfirm = {
                runner.run {
                    val result = vm.delete(names)
                    val text = when {
                        result.failed.isNotEmpty() -> resources.getString(R.string.media_delete_partly, result.failed.size, result.failed.first().error)
                        result.deleted.size == 1 -> deletedOne
                        else -> resources.getString(R.string.media_deleted, result.deleted.size)
                    }
                    snackbar.showSnackbar(text)
                }
            },
            onDismiss = { confirmDelete = null }
        )
    }

    rightsFor?.let { path ->
        MediaRightsSheet(path, onDismiss = { rightsFor = null }, onSaved = { vm.load(silent = true) })
    }
    if (overview) MediaRulesOverview(
        currentPath = page.path,
        onDismiss = { overview = false },
        onRights = { rightsFor = it },
        onOpen = { vm.open(it) }
    )
}

/** The path above the list: Media › Photos › 2026 (each step opens), and what the member may do here. */
@Composable
private fun Crumbs(path: String, access: String?, showWrite: Boolean, onOpen: (String) -> Unit) {
    val crumbs = breadcrumb(path)
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (crumbs.isNotEmpty()) {
                CrumbButton(stringResource(R.string.media_title), current = false, icon = Icons.Outlined.Folder) { onOpen("") }
                crumbs.forEachIndexed { index, (name, target) ->
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    CrumbButton(name, current = index == crumbs.lastIndex) { onOpen(target) }
                }
            }
        }
        when {
            access == "read" -> AccessBadge(stringResource(R.string.media_access_read), Icons.Outlined.Visibility, highlight = false)
            access == "write" && showWrite -> AccessBadge(stringResource(R.string.media_access_write), Icons.Outlined.UploadFile, highlight = true)
        }
    }
}

@Composable
private fun CrumbButton(text: String, current: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !current, onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, null, Modifier.padding(end = 4.dp).size(15.dp), tint = MediaAccent)
        Text(
            text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (current) Agora.colors.heading else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 180.dp)
        )
    }
}

@Composable
private fun AccessBadge(text: String, icon: ImageVector, highlight: Boolean) {
    val color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.padding(start = 8.dp).clip(CircleShape)
            .background(if (highlight) color.copy(alpha = 0.08f) else Agora.colors.surfaceAlt)
            .border(1.dp, if (highlight) color.copy(alpha = 0.25f) else Agora.colors.hairline, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(12.dp), tint = color)
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun SectionLabel(text: String, count: Int) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(Agora.colors.surfaceAlt).padding(horizontal = 7.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun entryMeta(size: Long?, modified: String?): String {
    val locale = Dates.locale(LocalContext.current)
    return listOf(formatSize(size, locale), modified?.let { Dates.short(it, locale) }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
}

/** A folder: flat icon with a thin border, name (two lines at most), size and date; the shield for managers. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    folder: MediaFolder,
    selected: Boolean,
    selecting: Boolean,
    canManage: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onRights: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(18.dp)
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth()
            .softShadow(Agora.colors.shadow.copy(alpha = 0.06f), blur = 6.dp, shape = shape, offsetY = 1.dp)
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.08f).compositeOver(surface) else surface)
            .border(1.dp, if (selected) primary.copy(alpha = 0.45f) else Agora.colors.hairline, shape)
            .combinedClickable(onClick = onOpen, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSelect() })
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tileShape = RoundedCornerShape(12.dp)
        Box(
            Modifier.size(42.dp).clip(tileShape).background(MediaAccent.copy(alpha = 0.12f).compositeOver(surface))
                .border(1.dp, MediaAccent.copy(alpha = 0.22f), tileShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Folder, null, Modifier.size(22.dp), tint = MediaAccent)
            if (folder.hiddenFor.isNotEmpty()) Icon(
                Icons.Outlined.VisibilityOff, stringResource(R.string.media_hidden_for_some),
                Modifier.align(Alignment.BottomEnd).padding(2.dp).size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
            Text(folder.name, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = entryMeta(folder.size, folder.modified)
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        when {
            selecting -> SelectMark(selected, Modifier.padding(end = 8.dp))
            canManage -> IconButton(onClick = onRights) {
                // One shield per folder: blue with rights of its own
                Icon(
                    Icons.Outlined.Shield,
                    stringResource(if (folder.hasRules) R.string.media_has_rights else R.string.media_rights_title),
                    Modifier.size(20.dp),
                    tint = if (folder.hasRules) Color(0xFF4A98C4) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
            else -> Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.padding(end = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

/** A file: its thumbnail over a tile in the colour of its kind (the kind's icon without one), name and size. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTile(
    file: MediaFile,
    previewUrl: String?,
    selected: Boolean,
    selecting: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val color = kindColor(file.kind)
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(modifier) {
        Column(
            Modifier
                .combinedClickable(
                    interactionSource = interaction, indication = null,
                    onClick = onOpen, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSelect() }
                )
                .padding(bottom = 4.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .softShadow(Agora.colors.shadow.copy(alpha = 0.07f), blur = 6.dp, shape = shape, offsetY = 1.dp)
                    .clip(shape)
                    .background(color.copy(alpha = 0.10f).compositeOver(surface))
                    .border(if (selected) 2.dp else 1.dp, if (selected) primary else Agora.colors.hairline, shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(kindIcon(file.kind), null, Modifier.size(34.dp), tint = color.copy(alpha = 0.85f))
                if (previewUrl != null) AsyncImage(
                    model = remember(previewUrl) { ImageRequest.Builder(context).data(previewUrl).crossfade(true).build() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (file.kind == "video" && previewUrl != null) Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(22.dp), tint = Color.White) }
                // Photos and videos show what they are (the picture, the play button): the type only for the rest
                val label = if (file.kind == "image" || file.kind == "video") "" else typeLabel(file.name)
                if (label.isNotEmpty()) Text(
                    label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.92f)).padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (selecting || selected) SelectMark(selected, Modifier.align(Alignment.TopEnd).padding(8.dp), onImage = true)
            }
            Text(
                file.name, style = MaterialTheme.typography.labelLarge, color = Agora.colors.heading, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)
            )
            val meta = entryMeta(file.size, file.modified)
            if (meta.isNotBlank()) Text(
                meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        // The ripple in the tile's rounded shape, on its own: clipping the whole tile cut off the picture's shadow
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(18.dp)).indication(interaction, ripple()))
    }
}

/**
 * The previews of the files further down load in the background, so they are there when scrolled to: from the first
 * one below the screen on (those on it load themselves), two at a time, leaving the connection free for what is on
 * the screen. Only into the disk cache (the tiles take them from there), not into memory.
 */
@Composable
private fun PrefetchPreviews(files: List<MediaFile>, grid: LazyGridState, previewUrl: (MediaFile) -> String) {
    val context = LocalContext.current
    LaunchedEffect(files) {
        // One frame later the grid shows these files
        withFrameNanos { }
        val shown = grid.layoutInfo.visibleItemsInfo.mapTo(HashSet()) { it.key }
        val loader = SingletonImageLoader.get(context)
        val lanes = Semaphore(2)
        coroutineScope {
            files.filter { it.hasPreview && "f:" + it.name !in shown }.forEach { file ->
                lanes.acquire()
                launch {
                    try {
                        loader.execute(
                            ImageRequest.Builder(context).data(previewUrl(file))
                                .memoryCachePolicy(CachePolicy.DISABLED).size(PREFETCH_DECODE_SIZE).build()
                        )
                    } finally {
                        lanes.release()
                    }
                }
            }
        }
    }
}

/** Prefetched previews are only decoded this small: what counts is that they are on the device. */
private const val PREFETCH_DECODE_SIZE = 32

/** The round mark of a selected entry (an empty ring while selecting). */
@Composable
private fun SelectMark(selected: Boolean, modifier: Modifier = Modifier, onImage: Boolean = false) {
    if (selected) Icon(
        Icons.Filled.CheckCircle, null,
        modifier.size(24.dp).clip(CircleShape).background(Color.White), tint = MaterialTheme.colorScheme.primary
    ) else Box(
        modifier.size(24.dp).clip(CircleShape)
            .background(if (onImage) Color.Black.copy(alpha = 0.18f) else Color.Transparent)
            .border(2.dp, if (onImage) Color.White else MaterialTheme.colorScheme.outline, CircleShape)
    )
}

/** Name or newest first (remembered on the device). */
@Composable
private fun SortMenu(sort: MediaSort, onSort: (MediaSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.media_sort), tint = Agora.colors.heading) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            listOf(MediaSort.NAME to R.string.media_sort_name, MediaSort.NEWEST to R.string.media_sort_newest).forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label), fontWeight = if (value == sort) FontWeight.Bold else FontWeight.Normal) },
                    trailingIcon = if (value == sort) ({ Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }) else null,
                    onClick = { open = false; onSort(value) }
                )
            }
        }
    }
}

/** In the header's place while entries are selected: how many, all / none, download and delete. */
@Composable
private fun SelectionHeader(
    count: Int,
    allSelected: Boolean,
    canDelete: Boolean,
    showDelete: Boolean,
    onClear: () -> Unit,
    onToggleAll: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val notAllowed = stringResource(R.string.media_delete_not_allowed)
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    Row(
        Modifier.fillMaxWidth().frosted().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, stringResource(R.string.media_select_clear), tint = Agora.colors.heading) }
        Text(
            if (count == 1) stringResource(R.string.media_selected_one) else stringResource(R.string.media_selected, count),
            style = MaterialTheme.typography.titleLarge, color = Agora.colors.heading, modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        IconButton(onClick = onToggleAll) {
            Icon(Icons.Outlined.SelectAll, stringResource(if (allSelected) R.string.media_select_none else R.string.media_select_all), tint = Agora.colors.heading)
        }
        IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, stringResource(R.string.media_download), tint = Agora.colors.heading) }
        if (showDelete) IconButton(onClick = { if (canDelete) onDelete() else scope.launch { snackbar.showSnackbar(notAllowed) } }) {
            Icon(
                Icons.Outlined.DeleteOutline, stringResource(R.string.media_delete),
                tint = if (canDelete) Agora.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

/** Upload or download running: a slim bar with what happens and how far it is, and a way to stop it. */
@Composable
private fun TransferBanner(transfer: MediaTransfer?, opening: Boolean, onCancel: () -> Unit) {
    AnimatedVisibility(
        visible = transfer != null || opening,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        val shape = RoundedCornerShape(16.dp)
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Agora.colors.hairline),
            // Room for the shadow above too: the fade's layer is cut to the banner's bounds
            modifier = Modifier.padding(start = 16.dp, end = 88.dp, top = 16.dp, bottom = 16.dp).widthIn(max = 420.dp)
                .softShadow(Agora.colors.shadow.copy(alpha = 0.12f), blur = 12.dp, shape = shape, offsetY = 3.dp)
        ) {
            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val percent = transfer?.percent
                    Text(
                        when {
                            transfer == null -> stringResource(R.string.media_downloading_plain)
                            transfer.storing -> stringResource(R.string.media_upload_storing)
                            transfer.upload -> stringResource(R.string.media_uploading_percent, percent ?: 0)
                            percent != null -> stringResource(R.string.media_downloading, percent)
                            else -> stringResource(R.string.media_downloading_plain)
                        },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val progress = transfer?.percent?.takeIf { transfer.storing.not() }
                    if (progress != null) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                if (transfer != null) TextButton(onClick = onCancel) { Text(stringResource(R.string.media_cancel_transfer)) }
            }
        }
    }
}
