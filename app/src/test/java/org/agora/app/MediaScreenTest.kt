package org.agora.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import coil3.request.allowHardware
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.serialization.json.Json
import org.agora.app.data.MediaFile
import org.agora.app.data.MediaFolder
import org.agora.app.data.MediaListing
import org.agora.app.data.local.ThemeMode
import org.agora.app.ui.components.LocalSharedTransition
import org.agora.app.ui.components.ProvideAppLocals
import org.agora.app.ui.media.MediaScreen
import org.agora.app.ui.media.pathSteps
import org.agora.app.ui.theme.AgoraTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The media library (MediaScreen.kt) against a [FakeServer] with folders, pictures, a video, music and documents: a
 * folder opens with a tap and back goes up again, and it draws light, dark and on a tablet. With
 * -PscreenshotsOut=<dir> the drawings are also saved there as pictures.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "de-w390dp-h844dp-xxhdpi")
class MediaScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val out = System.getProperty("agora.screenshots.out").orEmpty()
    private val server = FakeServer()
    private var theme by mutableStateOf(ThemeMode.LIGHT)
    /** The folders the server knows. */
    private var library = LIBRARY
    /** Folders whose answer waits until the test lets it go (as on a slow connection). */
    private val held = ConcurrentHashMap<String, CountDownLatch>()

    @Before
    fun serve() {
        server.on("GET", "/api/media/list") { query ->
            val path = query["path"].orEmpty()
            held[path]?.await(20, TimeUnit.SECONDS)
            library[path]?.let { 200 to Json.encodeToString(MediaListing.serializer(), it) } ?: (404 to """{"error":"Not found"}""")
        }
        server.onBytes("GET", "/api/media/preview") { query -> "image/png" to photo(query["path"].orEmpty(), query["size"]?.toIntOrNull() ?: 256) }
    }

    @After
    fun stop() {
        held.values.forEach { it.countDown() }
        server.close()
    }

    /** [lookahead]: inside a SharedTransitionLayout like in the app (AgoraApp), where layout runs ahead and then follows. */
    private fun show(lookahead: Boolean = false) {
        val container = AppContainer(context).apply {
            api.baseUrl = server.base
            api.token = "token"
        }
        // Like AgoraApplication, but decoded with BitmapFactory into software bitmaps (Robolectric has no ImageDecoder)
        SingletonImageLoader.setUnsafe { ctx ->
            coil3.ImageLoader.Builder(ctx).components {
                add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = { container.api.http }))
                add(coil3.decode.BitmapFactoryDecoder.Factory())
            }.allowHardware(false).build()
        }
        compose.setContent {
            AgoraTheme(theme) {
                ProvideAppLocals(container, SnackbarHostState()) {
                    if (lookahead) SharedTransitionLayout {
                        CompositionLocalProvider(LocalSharedTransition provides this) { MediaScreen(onBack = {}) }
                    } else MediaScreen(onBack = {})
                }
            }
        }
        settle()
    }

    /** Lets what loads on its own threads (listings, pictures) arrive. */
    private fun settle(rounds: Int = 8) = repeat(rounds) {
        Thread.sleep(200)
        compose.waitForIdle()
    }

    /**
     * Draws the screen, and saves it as [name] with -PscreenshotsOut. Robolectric draws only here: the pictures fade in
     * from their first drawing on, so that one comes first and the saved one a moment later.
     */
    private fun draw(name: String) {
        compose.onRoot().captureToImage()
        Thread.sleep(400)
        compose.waitForIdle()
        if (out.isBlank()) compose.onRoot().captureToImage() else compose.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test
    fun aFolderOpensAndBackGoesUp() {
        show()
        compose.onNodeWithText("Gottesdienste").assertIsDisplayed()
        compose.onAllNodesWithText("Fotos").onFirst().performClick()
        settle()
        compose.onNodeWithText("Sommerfest 2026").assertIsDisplayed()
        compose.onNodeWithText("Gottesdienste").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        settle()
        compose.onNodeWithText("Gottesdienste").assertIsDisplayed()
        compose.onNodeWithText("Sommerfest 2026").assertDoesNotExist()
    }

    @Test
    fun drawsLightDarkAndInAFolder() {
        show()
        draw("media")
        compose.onAllNodesWithText("Fotos").onFirst().performClick()
        settle()
        draw("media-folder")
        compose.runOnIdle { theme = ThemeMode.DARK }
        settle()
        draw("media-folder-dark")
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        settle()
        draw("media-dark")
    }

    @Test
    fun managersSeeNoMarksOfRightsOnFolders() {
        show()
        // Folders with rights of their own and hidden for some look like any other; the rights are behind the
        // header's shield (the open folder) and the list of all folder rights
        compose.onNodeWithText("Jugend").assertIsDisplayed()
        compose.onAllNodesWithContentDescription(context.getString(R.string.media_rights_title)).assertCountEquals(1)
        compose.onAllNodesWithContentDescription(context.getString(R.string.media_rules_overview)).assertCountEquals(1)
    }

    @Test
    fun theFolderRisesInAndGoesBackDown() {
        show()
        // Seen once, as when coming back: the frames show the folder and its pictures from the caches
        compose.onAllNodesWithText("Fotos").onFirst().performClick()
        settle()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        settle()
        compose.onRoot().captureToImage()
        Thread.sleep(400)
        compose.mainClock.autoAdvance = false
        compose.onAllNodesWithText("Fotos").onFirst().performClick()
        frames("in") { i ->
            // On its way the folder before and the one opening are both there
            if (i == 4) {
                compose.onNodeWithText("Gottesdienste").assertExists()
                compose.onNodeWithText("Sommerfest 2026").assertExists()
            }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Gottesdienste").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        frames("out") { i ->
            if (i == 4) compose.onNodeWithText("Sommerfest 2026").assertExists()
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Sommerfest 2026").assertDoesNotExist()
        compose.onNodeWithText("Gottesdienste").assertExists()
        compose.mainClock.autoAdvance = true
    }

    /** Fourteen steps of 30 ms, saved as pictures with -PscreenshotsOut (for looking at the movement). */
    private fun frames(name: String, check: (Int) -> Unit) = repeat(14) { i ->
        compose.mainClock.advanceTimeBy(30)
        check(i)
        if (out.isNotBlank()) compose.onRoot().captureRoboImage("$out/frames/$name-%02d.png".format(i))
    }

    @Test
    fun sevenFoldersDown() {
        show()
        listOf("Fotos", "2026", "Sommerfest", "Samstag", "Nachmittag", "Bühne", "Band").forEachIndexed { level, name ->
            compose.onAllNodesWithText(name).onFirst().performClick()
            settle(4)
            if (level == 2) draw("media-depth3")
        }
        settle()
        // The way home (the base folder's sign), the folder above and the open one - the five between folded into "…"
        compose.onNodeWithContentDescription(context.getString(R.string.media_title)).assertIsDisplayed()
        compose.onNodeWithText("Bühne").assertIsDisplayed()
        compose.onNodeWithText("Samstag").assertDoesNotExist()
        draw("media-deep")
        compose.onNodeWithContentDescription(context.getString(R.string.media_path_between)).performClick()
        settle(3)
        if (out.isNotBlank()) captureScreenRoboImage("$out/media-deep-menu.png")
        // The menu has every folder between, and goes there
        listOf("Fotos", "Sommerfest", "Samstag", "Nachmittag").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("2026").performClick()
        settle()
        compose.onNodeWithText("Sommerfest").assertIsDisplayed()
        compose.onNodeWithText("Fotos").assertIsDisplayed()
        compose.onNodeWithText("Band").assertDoesNotExist()
        // Back down again, and in the dark
        listOf("Sommerfest", "Samstag", "Nachmittag", "Bühne", "Band").forEach { name ->
            compose.onAllNodesWithText(name).onFirst().performClick()
            settle(4)
        }
        compose.runOnIdle { theme = ThemeMode.DARK }
        settle()
        draw("media-deep-dark")
    }

    /**
     * Going back the path loses its last pill at once and the folder above becomes the open one: in every frame exactly
     * the pills of the new path, and one painted as open. (The pills were a LazyRow with animated items: the pill going
     * away faded on its own while the row jumped to the open pill, which stopped the fading - on a phone it then stayed
     * half behind the open pill until the next folder.)
     */
    @Test
    fun goingBackThePathHasOneOpenFolder() {
        show(lookahead = true)
        val way = listOf("Fotos", "2026", "Sommerfest", "Samstag", "Nachmittag")
        way.forEach { name ->
            compose.onAllNodesWithText(name).onFirst().performClick()
            settle(4)
        }
        compose.mainClock.autoAdvance = false
        for (level in way.size - 1 downTo 0) {
            val above = way.take(level).joinToString("/")
            compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            repeat(20) { frame ->
                compose.mainClock.advanceTimeBy(16)
                assertEquals("frame $frame, back to \"$above\"", pathOf(above), path())
                if (frame < 6 || frame % 4 == 0) assertEquals("open pills drawn, frame $frame, back to \"$above\"", 1, openPillsDrawn())
            }
            compose.mainClock.advanceTimeBy(1000)
        }
        compose.mainClock.autoAdvance = true
    }

    /** While a folder seen for the first time loads, the header keeps the shield (its rights) and the list of all rights. */
    @Test
    fun theHeaderKeepsItsIconsWhileAFolderLoads() {
        show(lookahead = true)
        held["Fotos"] = CountDownLatch(1)
        compose.onAllNodesWithText("Fotos").onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sommerfest 2026").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.media_rights_title)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.media_rules_overview)).assertExists()
        held.getValue("Fotos").countDown()
        settle()
        compose.onNodeWithText("Sommerfest 2026").assertIsDisplayed()
    }

    /**
     * The title keeps its place when the header's room shrinks while it moves - here the rights come only with the opened
     * folder's answer, so the shield and the list appear while the long name still rises. (Its width was animated:
     * wider than the header then allowed, it was centred - onto the back button - and slid back.)
     */
    @Test
    fun theTitleStaysInPlaceWhenTheHeaderNarrowsWhileItMoves() {
        library = mapOf(
            "" to MediaListing(access = "write", canManage = false, folders = listOf(folder(LONG, "2026-08-23", 120_000_000))),
            LONG to MediaListing(path = LONG, name = LONG, access = "write", canManage = true, files = pictures(LONG, "Bild", 3, "2026-08-23"))
        )
        show(lookahead = true)
        compose.onNodeWithContentDescription(context.getString(R.string.media_rights_title)).assertDoesNotExist()
        held[LONG] = CountDownLatch(1)
        compose.mainClock.autoAdvance = false
        compose.onAllNodesWithText(LONG).onFirst().performClick()
        val lefts = mutableListOf<Float>()
        repeat(40) { frame ->
            // The answer comes while the title is on its way
            if (frame == 12) {
                held.getValue(LONG).countDown()
                Thread.sleep(400)
            }
            compose.mainClock.advanceTimeBy(16)
            lefts += title(LONG).boundsInRoot.left
        }
        compose.mainClock.autoAdvance = true
        settle()
        compose.onNodeWithContentDescription(context.getString(R.string.media_rights_title)).assertExists()
        val place = title(LONG).boundsInRoot.left
        assertTrue("the title left its place ($place): $lefts", lefts.all { it >= place - 1f })
    }

    /** What lies in the path under the title (the one row that scrolls sideways). */
    private val inPath = hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))

    /** The pills of the path as they stand, left to right: their names, the base folder's sign and "…" by what they say. */
    private fun path(): List<String> = pathNodes().map { it.second }

    private fun pathNodes(): List<Pair<SemanticsNode, String>> =
        compose.onAllNodes(inPath).fetchSemanticsNodes().mapNotNull { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            val said = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            (text?.takeIf { it.isNotEmpty() } ?: said?.takeIf { it.isNotEmpty() })?.let { node to it }
        }.sortedBy { it.first.boundsInRoot.left }

    /** The pills [path] should have. */
    private fun pathOf(path: String): List<String> = pathSteps(path, context.getString(R.string.media_title)).map { step ->
        if (step.folded.isNotEmpty()) context.getString(R.string.media_path_between) else step.name
    }

    /**
     * How many pills are painted as the open folder (its warm fill), along the line through the middle of the path - in
     * the picture as drawn, where a pill on its way out still shows.
     */
    private fun openPillsDrawn(): Int {
        val middle = pathNodes().first().first.boundsInRoot.center.y.toInt()
        val picture = compose.onRoot().captureToImage().toPixelMap()
        var pills = 0
        var warm = 0
        var gap = Int.MAX_VALUE
        for (x in 0 until picture.width) {
            if (isOpenFill(picture[x, middle])) {
                // Letters interrupt a pill's fill briefly; the arrow between two pills is wider
                if (gap > 40 && warm > 0) {
                    if (warm >= 30) pills++
                    warm = 0
                }
                warm++
                gap = 0
            } else gap++
        }
        if (warm >= 30) pills++
        return pills
    }

    /** The open pill's fill in the light theme: F6C7A4 brought 12 to 38 % towards white (MediaScreen.openStep). */
    private fun isOpenFill(color: Color): Boolean {
        val r = color.red * 255
        val g = color.green * 255
        val b = color.blue * 255
        return r >= 243 && g in 200f..225f && b in 168f..205f && r - b >= 40
    }

    /** The open folder's name in the header - not its pill in the path, not its row in the list. */
    private fun title(name: String): SemanticsNode =
        compose.onAllNodes(hasText(name) and !hasClickAction() and !inPath).fetchSemanticsNodes().single()

    @Test
    @Config(qualifiers = "de-w800dp-h1280dp-xhdpi")
    fun drawsOnTablets() {
        show()
        draw("media-tablet")
    }

    private companion object {
        /** A name too long for the header, cut with the icons beside it and without. */
        const val LONG = "Gemeindefreizeit am See, Samstagabend"

        fun folder(path: String, modified: String, size: Long, rules: Boolean = false, hiddenFor: List<String> = emptyList()) = MediaFolder(
            name = path.substringAfterLast('/'), path = path, size = size, modified = "${modified}T10:00:00.000Z",
            access = "write", canDelete = true, hasRules = rules, hiddenFor = hiddenFor
        )

        fun file(path: String, kind: String, size: Long, modified: String, type: String, preview: Boolean = kind == "image" || kind == "video") = MediaFile(
            name = path.substringAfterLast('/'), path = path, size = size, modified = "${modified}T10:00:00.000Z",
            contentType = type, etag = "e" + path.hashCode(), hasPreview = preview, kind = kind
        )

        fun pictures(folder: String, name: String, count: Int, modified: String) =
            (1..count).map { file("$folder/$name-%02d.jpg".format(it).removePrefix("/"), "image", 2_400_000L + it * 91_000, modified, "image/jpeg") }

        /** A manager's library: some folders have rights of their own or are hidden for some. */
        val LIBRARY = mapOf(
            "" to MediaListing(
                access = "write", canManage = true,
                folders = listOf(
                    folder("Fotos", "2026-11-12", 2_400_000_000),
                    folder("Gottesdienste", "2026-11-16", 860_000_000, rules = true),
                    folder("Gemeindebrief", "2026-11-01", 54_000_000),
                    folder("Jugend", "2026-10-30", 310_000_000, rules = true, hiddenFor = listOf("Gäste")),
                    folder("Logos & Grafiken", "2026-09-14", 12_000_000),
                    folder("Predigten", "2026-11-17", 1_200_000_000)
                ),
                files = listOf(
                    file("Gemeindebrief November.pdf", "pdf", 2_300_000, "2026-11-01", "application/pdf"),
                    file("Jahresplanung 2027.docx", "other", 84_000, "2026-10-20", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                    file("Lobpreis Probe.mp3", "audio", 6_100_000, "2026-11-08", "audio/mpeg"),
                    file("Willkommen.mp4", "video", 48_000_000, "2026-11-02", "video/mp4")
                ) + pictures("", "Sommerfest", 5, "2026-08-23")
            ),
            "Fotos" to MediaListing(
                path = "Fotos", name = "Fotos", access = "write", canManage = true,
                folders = listOf(
                    folder("Fotos/2025", "2025-12-21", 940_000_000),
                    folder("Fotos/2026", "2026-11-12", 1_100_000_000),
                    folder("Fotos/Sommerfest 2026", "2026-08-24", 380_000_000, rules = true),
                    folder("Fotos/Taufe im See", "2026-06-14", 120_000_000)
                ),
                files = pictures("Fotos", "Gemeinde", 12, "2026-11-12")
            )
        ) + deepDown()

        /** Seven folders down: Fotos › 2026 › Sommerfest › Samstag › Nachmittag › Bühne › Band. */
        private fun deepDown(): Map<String, MediaListing> {
            val chain = listOf("Fotos", "2026", "Sommerfest", "Samstag", "Nachmittag", "Bühne", "Band")
            val besides = listOf("", "", "Gemeindefreizeit", "Sonntag", "Abend", "Kinderprogramm", "Theater")
            return (2..chain.size).associate { level ->
                val path = chain.take(level).joinToString("/")
                val next = chain.getOrNull(level)
                val folders = if (next == null) emptyList() else listOfNotNull(
                    folder("$path/$next", "2026-08-23", 120_000_000L / level),
                    besides.getOrNull(level)?.takeIf { it.isNotEmpty() }?.let { folder("$path/$it", "2026-08-24", 40_000_000L / level) }
                )
                path to MediaListing(
                    path = path, name = chain[level - 1], access = "write", canManage = true,
                    folders = folders,
                    files = pictures(path, chain[level - 1], if (next == null) 9 else 3, "2026-08-23")
                )
            }.filterKeys { it != "Fotos/2026" } + ("Fotos/2026" to MediaListing(
                path = "Fotos/2026", name = "2026", access = "write", canManage = true,
                folders = listOf(folder("Fotos/2026/Sommerfest", "2026-08-23", 380_000_000), folder("Fotos/2026/Gemeindefreizeit", "2026-05-12", 210_000_000)),
                files = pictures("Fotos/2026", "Gottesdienst", 4, "2026-11-12")
            ))
        }

        /** Seven moods of a landscape and one of lights, chosen by the file's path. */
        private val SCENES = listOf(
            longArrayOf(0xFFF4B9A0, 0xFFFBE4CC, 0xFFFFF4D8, 0xFFCB8C8E, 0xFF8C5D74),
            longArrayOf(0xFF86C3EE, 0xFFDDF0FB, 0xFFFFFBEA, 0xFF9DCB8B, 0xFF5B9B5A),
            longArrayOf(0xFF2E3A66, 0xFFEA8E6C, 0xFFFFDBA6, 0xFF453858, 0xFF251F35),
            longArrayOf(0xFFBDE2F2, 0xFFEFF8FB, 0xFFFFFFFF, 0xFF66AFCC, 0xFF2D7DA5),
            longArrayOf(0xFFE3EFD5, 0xFFF7F3E6, 0xFFFFF8E2, 0xFF72A26E, 0xFF3D684A),
            longArrayOf(0xFFE7D9F4, 0xFFF9EEF3, 0xFFFFF6EC, 0xFFB79CD8, 0xFF7A62A6),
            longArrayOf(0xFFF2D6AC, 0xFFFDF1DC, 0xFFFFF9EC, 0xFFDA9250, 0xFFA35837)
        )

        /** A small picture for a preview: sky, sun and two hills - or lights in the dark - always the same for a path. */
        fun photo(path: String, size: Int): ByteArray {
            val s = size.coerceIn(64, 320)
            val f = s.toFloat()
            val bitmap = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val random = Random(path.hashCode().toLong())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val pick = Math.floorMod(path.hashCode(), SCENES.size + 1)
            if (pick == SCENES.size) {
                paint.shader = LinearGradient(0f, 0f, 0f, f, 0xFF2A2036.toInt(), 0xFF4A2E3E.toInt(), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, f, f, paint)
                val lights = intArrayOf(0xFFFFC47A.toInt(), 0xFFFF8FA3.toInt(), 0xFF9AD0FF.toInt(), 0xFFFFE9B0.toInt())
                repeat(14) {
                    val x = random.nextFloat() * f
                    val y = random.nextFloat() * f
                    val r = f * (0.05f + random.nextFloat() * 0.12f)
                    val color = lights[random.nextInt(lights.size)]
                    paint.shader = RadialGradient(x, y, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
                    canvas.drawCircle(x, y, r, paint)
                }
            } else {
                val (top, bottom, sun, back, front) = SCENES[pick].map { it.toInt() }
                paint.shader = LinearGradient(0f, 0f, 0f, f * 0.75f, top, bottom, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, f, f, paint)
                val sx = f * (0.25f + random.nextFloat() * 0.5f)
                val sy = f * (0.22f + random.nextFloat() * 0.18f)
                paint.shader = RadialGradient(sx, sy, f * 0.22f, sun, sun and 0x00FFFFFF, Shader.TileMode.CLAMP)
                canvas.drawCircle(sx, sy, f * 0.22f, paint)
                paint.shader = null
                paint.color = sun
                canvas.drawCircle(sx, sy, f * 0.075f, paint)
                fun hill(color: Int, level: Float) {
                    val path = Path().apply {
                        moveTo(0f, f * (level + random.nextFloat() * 0.08f))
                        cubicTo(f * 0.3f, f * (level - 0.12f + random.nextFloat() * 0.1f), f * 0.62f, f * (level + 0.06f + random.nextFloat() * 0.08f), f, f * (level - 0.04f + random.nextFloat() * 0.08f))
                        lineTo(f, f)
                        lineTo(0f, f)
                        close()
                    }
                    paint.color = color
                    canvas.drawPath(path, paint)
                }
                hill(back, 0.58f)
                hill(front, 0.72f)
            }
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
    }
}
