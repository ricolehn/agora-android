package org.agora.app.ui.hub

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.agora.app.R
import org.agora.app.ui.ai.AiOrb
import org.agora.app.ui.ai.AiOrbFrame
import org.agora.app.ui.components.columnCount
import org.agora.app.ui.components.pagePadding
import org.agora.app.ui.components.softShadow
import org.agora.app.ui.theme.Agora

/**
 * An app of the Extras hub (web: HUB_APPS). [light] and [deep] are the two tones of its colour: the deep one draws its
 * arrow in light mode, the light one in dark mode, and both paint the field behind its [motif] (HubArt.kt), [hint] a
 * breath of a neighbouring hue in that field. [featured]: across the full width on top, with the assistant's sign
 * instead of a motif.
 */
data class HubApp(
    val route: String,
    val light: Color,
    val deep: Color,
    val title: Int,
    val desc: Int,
    val motif: HubMotif? = null,
    val hint: Color = light,
    val featured: Boolean = false
)

/**
 * The apps of the hub in the web's order (3.3 BA SING SE): the AI chat across the full width on top (when it is on and
 * the person may use it; in the sand and lagoon of the assistant's sign, which its tile shows), then Media (only with
 * access to the library) with a picture, Songbook with two notes and Prayer requests with a heart. Polls stay off.
 */
fun hubApps(aiAvailable: Boolean, mediaAvailable: Boolean = false): List<HubApp> = buildList {
    if (aiAvailable) add(HubApp("ai", Color(0xFFEBD3AE), Color(0xFF2D625B), R.string.hub_ai_title, R.string.hub_ai_desc, featured = true))
    if (mediaAvailable) add(HubApp("media", Color(0xFFF6C7A4), Color(0xFFE0874F), R.string.hub_media_title, R.string.hub_media_desc, HubMotif.Picture, Color(0xFFF9DA9E)))
    add(HubApp("songbook", Color(0xFFAED6EC), Color(0xFF4A98C4), R.string.hub_songbook_title, R.string.hub_songbook_desc, HubMotif.Notes, Color(0xFFC9C6F3)))
    add(HubApp("prayers", Color(0xFFF2B5C0), Color(0xFFD6667C), R.string.hub_prayers_title, R.string.hub_prayers_desc, HubMotif.Heart, Color(0xFFF8C6AE)))
}

/** Extras: one permanent nav entry for further apps of the community (web: #hub). */
@Composable
fun HubScreen(apps: List<HubApp>, contentPadding: PaddingValues, onOpen: (String) -> Unit) {
    // Tablets: the tiles side by side (web: grid of tiles at least 300px wide), the featured one across
    val columns = columnCount(300.dp, gap = 14.dp)
    val (featured, others) = apps.partition { it.featured }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = pagePadding(contentPadding.calculateTopPadding() + 4.dp, contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "head") {
            Column(Modifier.padding(bottom = 6.dp)) {
                Text(stringResource(R.string.nav_hub), style = MaterialTheme.typography.headlineLarge, color = Agora.colors.heading)
                Text(stringResource(R.string.hub_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
        featured.forEachIndexed { index, app -> item(key = app.route) { HubTile(app, index) { onOpen(app.route) } } }
        others.chunked(columns).forEachIndexed { row, chunk ->
            item(key = chunk.joinToString { it.route }) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    chunk.forEachIndexed { column, app ->
                        HubTile(app, featured.size + row * columns + column, Modifier.weight(1f)) { onOpen(app.route) }
                    }
                    repeat(columns - chunk.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * A tile of the hub on the surface with a hairline edge and a neutral soft shadow; tiles rise in one after another.
 * The AI tile: a breath of sand from the upper right, the assistant's sign (still and faint once more in its corner)
 * and, on tablets, "Start chat". Every other app: a row with its motif in its painted field, its name and what it
 * offers, an arrow in its colour - nothing in it moves.
 */
@Composable
private fun HubTile(app: HubApp, index: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.luminance() < 0.5f
    val tone = if (dark) app.light else app.deep
    val shape = RoundedCornerShape(22.dp)
    val appear = remember(app.route) { Animatable(0f) }
    LaunchedEffect(app.route) {
        kotlinx.coroutines.delay(index * 55L)
        appear.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = (1f - appear.value) * 10.dp.toPx()
                val scale = 0.985f + 0.015f * appear.value
                scaleX = scale
                scaleY = scale
            }
            // The shadow fades in on its own, outside of the alpha layer: that one is drawn offscreen and cut to the
            // tile's bounds, so the shadow would only pop up once the tile has fully appeared
            .softShadow(
                if (dark) Color.Black.copy(alpha = 0.45f) else Color(0xFF1C1917).copy(alpha = 0.10f),
                blur = 14.dp, shape = shape, offsetY = 6.dp, alpha = { appear.value }
            )
            .graphicsLayer { alpha = appear.value }
            .clip(shape)
            .background(surface)
            // The AI tile: only a breath of sand from the upper right corner, fading out past the middle of the tile
            // (web: radial-gradient(120% 130% at 100% 0%, app-light 14%, transparent 50%))
            .then(
                if (app.featured) Modifier.drawBehind {
                    val tint = (if (dark) app.deep else app.light).copy(alpha = if (dark) 0.14f else 0.20f)
                    drawRect(Brush.radialGradient(listOf(tint, Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.6f))
                } else Modifier
            )
            .border(1.dp, Agora.colors.hairline, shape)
            .clickable(onClick = onClick)
    ) {
        if (app.featured) AiTileContent(app, tone, dark, surface)
        else Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            app.motif?.let { HubMotifField(app, it) }
            TileTexts(app, Modifier.weight(1f).padding(start = 14.dp, end = 8.dp))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.padding(end = 8.dp).size(18.dp), tint = tone.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun BoxScope.AiTileContent(app: HubApp, tone: Color, dark: Boolean, surface: Color) {
    // The assistant's sign once more, still and faint in the corner. It lies in the tile's own size and spills over
    // its edge, so it never makes the tile taller than its text
    Box(Modifier.matchParentSize()) {
        AiOrbFrame(
            132.dp, time = { 9f }, glow = false,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 38.dp, y = (-58).dp).requiredSize(132.dp).graphicsLayer { alpha = if (dark) 0.07f else 0.08f }
        )
    }
    Row(Modifier.padding(start = 16.dp, end = 14.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { AiOrb(42.dp) }
        TileTexts(app, Modifier.weight(1f).padding(horizontal = 14.dp))
        if (columnCount(300.dp, gap = 14.dp) > 1) Row(
            Modifier.clip(CircleShape).background(app.light.copy(alpha = if (dark) 0.16f else 0.22f).compositeOver(surface))
                .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.hub_ai_cta), style = MaterialTheme.typography.labelMedium, color = tone)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(16.dp), tint = tone)
        }
        else Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = tone.copy(alpha = 0.75f))
    }
}

@Composable
private fun TileTexts(app: HubApp, modifier: Modifier) = Column(modifier) {
    Text(stringResource(app.title), style = MaterialTheme.typography.titleMedium, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(stringResource(app.desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 3.dp))
}
