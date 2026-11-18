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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import org.agora.app.ui.components.CapsLabel
import org.agora.app.ui.components.ConfirmSheet
import org.agora.app.ui.components.FabAction
import org.agora.app.ui.components.FieldColors
import org.agora.app.ui.components.FrostScaffold
import org.agora.app.ui.components.LoadingBox
import org.agora.app.ui.components.LocalContainer
import org.agora.app.ui.components.LocalSnackbar
import org.agora.app.ui.components.Motif
import org.agora.app.ui.components.MotifIcon
import org.agora.app.ui.components.PaintedField
import org.agora.app.ui.components.SecondaryButton
import org.agora.app.ui.components.SubPageHeader
import org.agora.app.ui.components.TextInputSheet
import org.agora.app.ui.components.containerViewModel
import org.agora.app.ui.components.frosted
import org.agora.app.ui.components.pageGutter
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

/** The painted field of folders and pictures: the library's warm colours (its Extras tile). */
private val MediaField = FieldColors(Color(0xFFF6C7A4), MediaAccent, Color(0xFFF9DA9E))

/** A file's field in the colours of its kind; documents by their type (text blue, tables green, slides orange). */
private fun fileField(file: MediaFile): FieldColors = when (file.kind) {
    "image" -> MediaField
    "pdf" -> FieldColors(Color(0xFFF2B5C0), Color(0xFFD6667C), Color(0xFFF8C6AE))
    "audio" -> FieldColors(Color(0xFFAED6EC), Color(0xFF4A98C4), Color(0xFFC9C6F3))
    "video" -> FieldColors(Color(0xFFCFC8F3), Color(0xFF7C6FD6), Color(0xFFF0C6E6))
    else -> when (typeLabel(file.name).lowercase()) {
        "doc", "docx", "odt", "rtf", "txt", "md", "pages" -> FieldColors(Color(0xFFB9CCF2), Color(0xFF4A7FD6), Color(0xFFC3E2F0))
        "xls", "xlsx", "ods", "csv", "numbers" -> FieldColors(Color(0xFFB5DEC6), Color(0xFF3E9B6A), Color(0xFFD9EAB4))
        "ppt", "pptx", "odp", "key" -> FieldColors(Color(0xFFF7C9A8), Color(0xFFE07B42), Color(0xFFF6DD9E))
        else -> FieldColors(Color(0xFFE3DCD3), Color(0xFF8A837A), Color(0xFFEDE3CF))
    }
}

private fun fileMotif(file: MediaFile): Motif = when (file.kind) {
    "image" -> Motif.Picture
    "audio" -> Motif.Notes
    "video" -> Motif.Film
    else -> Motif.Document
}

/** Where the still forms of a field lie: every name its own. */
private fun seedOf(name: String): Float = (name.hashCode() and 0xFFFF) / 6553.5f

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

@Composable
private fun darkTheme() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * The media library (Extras → Media) like the web app 3.3: the community's Nextcloud folder with its folders and
 * thumbnails, the path as pills under the title, "Folders" as rows like the apps in Extras and "Files" three to a row
 * with their names under them. A folder that opens rises from below, going back it comes down again. Pictures open in a full-screen viewer, PDFs page by page, videos and music in the player, anything
 * else in another app. Holding an entry selects; the selection downloads as one ZIP or, with write access, is
 * deleted. Back goes up a folder; members with extended media rights set the rights of the open folder with the
 * shield and see every folder's rights in the list - the folders themselves carry no marks of their rights.
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
    // Where each folder was scrolled to: going back up a folder shows it where it was left, a folder opened anew starts on top
    val scrolled = remember { mutableMapOf<String, Pair<Int, Int>>() }

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

    // A folder opened anew starts on top; going up (back, the path) finds the folder where it was left
    fun openFolder(path: String) {
        scrolled.remove(path)
        vm.open(path)
    }
    val openEntry: (String) -> Unit = { name ->
        val folder = folders.firstOrNull { it.name == name }
        val file = files.firstOrNull { it.name == name }
        when {
            selecting -> vm.toggle(name)
            folder != null -> openFolder(folder.path)
            file == null -> Unit
            opensInViewer(file) || opensAsPdf(file) || opensInPlayer(file) -> viewing = file.name
            else -> openElsewhere(file)
        }
    }

    val title = if (page.path.isEmpty()) stringResource(R.string.media_title) else folderName(page.path)
    FrostScaffold(
        topBar = {
            Column {
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
                    titleContent = { FolderTitle(page.path) },
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
                PathBar(page.path) { vm.open(it) }
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) { AgoraSnackbar(it) } }
    ) { padding ->
        val density = LocalDensity.current
        val screenWidth = LocalConfiguration.current.screenWidthDp
        val phone = screenWidth < 600
        // Thumbnails as sharp as the tile (about a third of the screen on phones), from the server's fixed sizes
        val thumb = with(density) { thumbSize(((if (phone) screenWidth / 3 else 200).dp).roundToPx()) }
        // Three pictures side by side on phones, tiles of at least 150dp on tablets
        val fileColumns = if (phone) 3 else ((screenWidth.dp - pageGutter() * 2 + 8.dp) / 158.dp).toInt().coerceAtLeast(4)
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = page,
                contentKey = { it.path },
                transitionSpec = { folderTransition(initialState.path, targetState.path) },
                label = "folder"
            ) { shown ->
                FolderGrid(
                    shown, sort, selected, canManage, fileColumns, thumb, padding, scrolled,
                    onOpen = openEntry, onSelect = vm::toggle, onRetry = { vm.load() }
                )
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
        onOpen = { openFolder(it) }
    )
}

/**
 * The open folder's page: what the member may do here, then "Folders" and "Files". Its grid remembers where it was
 * scrolled to ([scrolled]) when the page moves on, for coming back up.
 */
@Composable
private fun FolderGrid(
    page: MediaPage,
    sort: MediaSort,
    selected: Set<String>,
    canManage: Boolean,
    fileColumns: Int,
    thumb: Int,
    padding: PaddingValues,
    scrolled: MutableMap<String, Pair<Int, Int>>,
    onOpen: (String) -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit
) {
    val container = LocalContainer.current
    val listing = page.listing
    val folders = remember(listing, sort) { sortFolders(listing?.folders.orEmpty(), sort) }
    val files = remember(listing, sort) { sortFiles(listing?.files.orEmpty(), sort) }
    val access = listing?.access ?: "none"
    val selecting = selected.isNotEmpty()
    val grid = rememberLazyGridState(scrolled[page.path]?.first ?: 0, scrolled[page.path]?.second ?: 0)
    DisposableEffect(Unit) { onDispose { scrolled[page.path] = grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset } }
    PrefetchPreviews(files, grid) { container.repo.mediaPreviewUrl(it, thumb, crop = true) }
    // Half columns: a file takes two, so on tablets two folder rows stand side by side above the files
    val columns = fileColumns * 2
    val folderSpan = if (fileColumns == 3) columns else fileColumns
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding(padding.calculateTopPadding() + 6.dp, padding.calculateBottomPadding() + 112.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (listing != null && (access == "read" || access == "write" && !canManage)) item(key = "access", span = { GridItemSpan(maxLineSpan) }) { AccessNote(access) }
        when {
            page.error != null -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                FolderMessage(
                    if (page.error == MediaViewModel.UNAVAILABLE) stringResource(R.string.media_unavailable)
                    else page.error.takeIf { it.isNotBlank() } ?: stringResource(R.string.media_load_failed)
                ) { SecondaryButton(onClick = onRetry) { Text(stringResource(R.string.media_retry)) } }
            }
            listing == null -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) { LoadingBox(Modifier.fillMaxWidth().padding(top = 48.dp)) }
            folders.isEmpty() && files.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                FolderMessage(stringResource(R.string.media_empty), if (access == "write") stringResource(R.string.media_empty_hint) else null)
            }
            else -> {
                if (folders.isNotEmpty()) {
                    item(key = "folders-head", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.media_section_folders), folders.size) }
                    items(folders, key = { "d:" + it.name }, span = { GridItemSpan(folderSpan) }) { folder ->
                        // Only moving, no fading: the fade's layer is cut to the card and would swallow its shadow
                        val modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                        FolderRow(folder, folder.name in selected, selecting, { onOpen(folder.name) }, { onSelect(folder.name) }, modifier)
                    }
                }
                if (files.isNotEmpty()) {
                    item(key = "files-head", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.media_section_files), files.size) }
                    items(files, key = { "f:" + it.name }, span = { GridItemSpan(2) }) { file ->
                        val previewUrl = if (file.hasPreview) container.repo.mediaPreviewUrl(file, thumb, crop = true) else null
                        val modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                        FileTile(file, previewUrl, file.name in selected, selecting, { onOpen(file.name) }, { onSelect(file.name) }, modifier)
                    }
                }
            }
        }
    }
}

/** How a folder replaces the one before: a deeper one rises from below, going back it comes down from above. */
private fun folderTransition(from: String, to: String): ContentTransform {
    val way = if (depth(to) >= depth(from)) 1 else -1
    val enter = slideInVertically(tween(340, easing = FastOutSlowInEasing)) { way * it / 7 } + fadeIn(tween(240, delayMillis = 60))
    val exit = slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { -way * it / 12 } + fadeOut(tween(140))
    // No clipping: the cards' shadows reach over the page's edge
    return ContentTransform(enter, exit, sizeTransform = SizeTransform(clip = false))
}

/** The open folder's name in the header, moving along with the page. */
@Composable
private fun FolderTitle(path: String) {
    val root = stringResource(R.string.media_title)
    AnimatedContent(
        targetState = path,
        transitionSpec = {
            val way = if (depth(targetState) >= depth(initialState)) 1 else -1
            (slideInVertically(tween(320, easing = FastOutSlowInEasing)) { way * it } + fadeIn(tween(200, delayMillis = 60)))
                .togetherWith(slideOutVertically(tween(260)) { -way * it } + fadeOut(tween(120)))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterStart,
        label = "title"
    ) { shown ->
        Text(
            if (shown.isEmpty()) root else folderName(shown),
            style = MaterialTheme.typography.titleLarge, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The way down to the open folder under the title: pills with a small arrow between them, each one opens its folder,
 * the open one painted in the library's warm colours. From three folders down the base folder shows only its sign;
 * deeper than three the middle is folded into "…" (its folders in a menu), so the way home, the folder above and the
 * open one stay in sight on a phone. What still does not fit fades out at the edges.
 */
@Composable
private fun PathBar(path: String, onOpen: (String) -> Unit) {
    val rootName = stringResource(R.string.media_title)
    val steps = remember(path, rootName) { pathSteps(path, rootName) }
    // From three folders down the base folder makes room
    val compactRoot = steps.size >= 4
    val list = rememberLazyListState()
    // The open folder always in sight
    LaunchedEffect(steps) { list.animateScrollToItem(steps.lastIndex) }
    Box(Modifier.fillMaxWidth().frosted().padding(top = 2.dp, bottom = 10.dp)) {
        LazyRow(
            Modifier.fillMaxWidth().fadingEdges({ list.canScrollBackward }, { list.canScrollForward }),
            state = list,
            contentPadding = PaddingValues(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(steps, key = { _, step -> if (step.folded.isEmpty()) step.target else "…" }) { index, step ->
                Row(Modifier.animateItem(), verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.padding(horizontal = 4.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    if (step.folded.isNotEmpty()) FoldedSteps(step.folded, onOpen)
                    else PathPill(step, open = index == steps.lastIndex, compact = step.root && compactRoot) { onOpen(step.target) }
                }
            }
        }
    }
}

/** A step of the path; [compact]: the base folder only as its sign. */
@Composable
private fun PathPill(step: PathStep, open: Boolean, compact: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .animateContentSize()
            .then(
                if (open) Modifier.openStep(CircleShape)
                else Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).border(1.dp, Agora.colors.hairline, CircleShape)
            )
            .clickable(enabled = !open, onClick = onClick)
            .semantics { if (compact) contentDescription = step.name }
            .padding(start = if (step.root) 8.dp else 14.dp, end = if (compact) 8.dp else 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (step.root) MotifIcon(Motif.Folder, 20.dp, Modifier.padding(end = if (compact) 0.dp else 6.dp))
        if (!compact) Text(
            step.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (open) FontWeight.SemiBold else FontWeight.Medium,
            color = if (open) Agora.colors.heading else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

/** The folded middle of a deep path: "…", its folders in a menu, each a little further in, like the way down. */
@Composable
private fun FoldedSteps(folders: List<Pair<String, String>>, onOpen: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).border(1.dp, Agora.colors.hairline, CircleShape)
                .clickable { open = true }.padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.media_path_between), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            folders.forEachIndexed { level, (target, name) ->
                DropdownMenuItem(
                    text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { MotifIcon(Motif.Folder, 20.dp, Modifier.padding(start = (level * 12).dp)) },
                    onClick = {
                        open = false
                        onOpen(target)
                    }
                )
            }
        }
    }
}

/** Lets what scrolls out at an edge fade instead of being cut ([start] / [end]: there is more on that side). */
private fun Modifier.fadingEdges(start: () -> Boolean, end: () -> Boolean): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val fade = 28.dp.toPx()
        if (start()) drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
            size = Size(fade, size.height), blendMode = BlendMode.DstIn
        )
        if (end()) drawRect(
            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
            topLeft = Offset(size.width - fade, 0f), size = Size(fade, size.height), blendMode = BlendMode.DstIn
        )
    }

/** The open step: painted in the library's warm colours. */
@Composable
private fun Modifier.openStep(shape: Shape): Modifier {
    val dark = darkTheme()
    val surface = MaterialTheme.colorScheme.surface
    val warm = Color(0xFFF6C7A4)
    val fill = if (dark) Brush.horizontalGradient(listOf(lerp(surface, MediaAccent, 0.34f), lerp(surface, MediaAccent, 0.20f)))
    else Brush.horizontalGradient(listOf(lerp(warm, Color.White, 0.38f), lerp(warm, Color.White, 0.12f)))
    return clip(shape).background(fill).border(1.dp, Color.White.copy(alpha = if (dark) 0.06f else 0.7f), shape)
}

/** Pressed things give a little, like paper under a finger. */
@Composable
private fun Modifier.pressScale(interaction: InteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
private fun entryMeta(size: Long?, modified: String?): String {
    val locale = Dates.locale(LocalContext.current)
    return listOf(modified?.let { Dates.short(it, locale) }.orEmpty(), formatSize(size, locale)).filter { it.isNotBlank() }.joinToString(" · ")
}

/** A folder: a row like the apps in Extras - the folder in a painted field, its name and date, an arrow. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(folder: MediaFolder, selected: Boolean, selecting: Boolean, onOpen: () -> Unit, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth()
            .pressScale(interaction)
            .softShadow(if (darkTheme()) Color.Black.copy(alpha = 0.35f) else Color(0xFF1C1917).copy(alpha = 0.07f), blur = 12.dp, shape = shape, offsetY = 4.dp)
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.08f).compositeOver(surface) else surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) primary.copy(alpha = 0.6f) else Agora.colors.hairline, shape)
            .combinedClickable(interactionSource = interaction, indication = ripple(), onClick = onOpen, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSelect() })
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaintedField(MediaField, seedOf(folder.name), RoundedCornerShape(14.dp), Modifier.size(56.dp)) { MotifIcon(Motif.Folder, 36.dp) }
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            Text(folder.name, style = MaterialTheme.typography.titleSmall, color = Agora.colors.heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = entryMeta(folder.size, folder.modified)
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        if (selecting || selected) SelectMark(selected, Modifier.padding(end = 8.dp))
        else Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.padding(end = 8.dp).size(18.dp), tint = MediaAccent.copy(alpha = 0.8f))
    }
}

/** The type of a file on its tile ("PDF"), in the colour of its kind. */
@Composable
private fun TypePill(label: String, color: Color, modifier: Modifier = Modifier) {
    if (label.isEmpty()) return
    Text(
        label, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp, color = Color.White, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.92f)).padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

@Composable
private fun PlayBadge() = Box(
    Modifier.size(34.dp)
        .softShadow(Color.Black.copy(alpha = 0.25f), blur = 8.dp, shape = CircleShape, offsetY = 2.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.9f)),
    contentAlignment = Alignment.Center
) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(21.dp), tint = Color(0xFF2A2830)) }

/**
 * What a tile shows of a file: its preview (a play button on videos) over the painted field of its kind, which shows
 * through while it loads - or, without a preview, the kind's motif with its type.
 */
@Composable
private fun FileFace(file: MediaFile, previewUrl: String?, shape: Shape, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = fileField(file)
    PaintedField(colors, seedOf(file.name), shape, modifier) {
        if (previewUrl != null) {
            MotifIcon(fileMotif(file), 34.dp, Modifier.fillMaxSize(0.32f), accent = colors.deep)
            AsyncImage(
                model = remember(previewUrl) { ImageRequest.Builder(context).data(previewUrl).crossfade(true).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            if (file.kind == "video") PlayBadge()
        } else {
            // As large as the tile allows: a third of a phone's row, more on tablets
            MotifIcon(fileMotif(file), 46.dp, Modifier.fillMaxSize(0.42f), accent = colors.deep)
            TypePill(typeLabel(file.name), colors.deep, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
    }
}

/** A file: its square, its name (without the type its tile shows) and size under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTile(file: MediaFile, previewUrl: String?, selected: Boolean, selecting: Boolean, onOpen: () -> Unit, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    val chosen by animateFloatAsState(if (selected) 0.9f else 1f, label = "chosen")
    Column(
        modifier.pressScale(interaction)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onOpen, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSelect() })
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Box(Modifier.matchParentSize().graphicsLayer { scaleX = chosen; scaleY = chosen }) {
                FileFace(file, previewUrl, shape, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().clip(shape).indication(interaction, ripple()))
                if (selected) Box(Modifier.matchParentSize().border(2.5.dp, MaterialTheme.colorScheme.primary, shape))
            }
            if (selecting || selected) SelectMark(selected, Modifier.align(Alignment.TopEnd).padding(6.dp), onImage = true)
        }
        Text(
            shownName(file.name), style = MaterialTheme.typography.labelMedium, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp)
        )
        val size = formatSize(file.size, Dates.locale(LocalContext.current))
        if (size.isNotBlank()) Text(size, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
private fun SectionLabel(text: String, count: Int) {
    Row(Modifier.padding(start = 2.dp, top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        CapsLabel(text)
        Text(
            count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** What the member may do in the open folder: only look, or also add (managers know it). */
@Composable
private fun AccessNote(access: String) {
    val read = access == "read"
    val color = if (read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(CircleShape).background(if (read) Agora.colors.surfaceAlt else color.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (read) Icons.Outlined.Visibility else Icons.Outlined.UploadFile, null, Modifier.size(13.dp), tint = color)
            Text(
                stringResource(if (read) R.string.media_access_read else R.string.media_access_write),
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** An empty folder or one that could not be loaded: the folder motif with a word, and what can be done. */
@Composable
private fun FolderMessage(title: String, subtitle: String? = null, action: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MotifIcon(Motif.Folder, 76.dp, Modifier.padding(bottom = 6.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Agora.colors.heading, textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action()
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
