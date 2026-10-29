package org.agora.app.ui.hub

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.agora.app.R
import org.agora.app.ui.components.columnCount
import org.agora.app.ui.components.pagePadding
import org.agora.app.ui.components.softShadow
import org.agora.app.ui.theme.Agora

/**
 * A tile of the Extras hub (web: HUB_APPS). [light] and [deep] are the two tones of the app's colour: the deep
 * one draws the icon in light mode, the light one in dark mode. [featured]: across the full width on top.
 */
data class HubApp(
    val route: String,
    val icon: ImageVector,
    val light: Color,
    val deep: Color,
    val title: Int,
    val desc: Int,
    val featured: Boolean = false
)

/**
 * The apps of the hub in the web's order (3.3 BA SING SE): the AI chat across the full width on top (when it is on
 * and the person may use it), then Media (only with access to the library), Songbook and Prayer requests. Polls
 * stay off.
 */
fun hubApps(aiAvailable: Boolean, mediaAvailable: Boolean = false): List<HubApp> = buildList {
    if (aiAvailable) add(HubApp("ai", Icons.Outlined.AutoAwesome, Color(0xFFC4B8F5), Color(0xFF7C6FD6), R.string.hub_ai_title, R.string.hub_ai_desc, featured = true))
    if (mediaAvailable) add(HubApp("media", Icons.Outlined.Folder, Color(0xFFF6C7A4), Color(0xFFE0874F), R.string.hub_media_title, R.string.hub_media_desc))
    add(HubApp("songbook", Icons.Outlined.MusicNote, Color(0xFFAED6EC), Color(0xFF4A98C4), R.string.hub_songbook_title, R.string.hub_songbook_desc))
    add(HubApp("prayers", Icons.Outlined.FavoriteBorder, Color(0xFFF2B5C0), Color(0xFFD6667C), R.string.hub_prayers_title, R.string.hub_prayers_desc))
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
 * A tile like the web's `.hub-app`: a faint wash of the app's colour from the upper right, the icon as a flat tinted
 * square with a thin border, the app's sign once more large and faint in the corner, a neutral soft shadow. The
 * featured AI tile gets a little more tint, quiet sparkles and "Start chat". Tiles rise in one after another.
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
                alpha = appear.value
                translationY = (1f - appear.value) * 10.dp.toPx()
                val scale = 0.985f + 0.015f * appear.value
                scaleX = scale
                scaleY = scale
            }
            .softShadow(if (dark) Color.Black.copy(alpha = 0.45f) else Color(0xFF1C1917).copy(alpha = 0.10f), blur = 14.dp, shape = shape, offsetY = 6.dp)
            .clip(shape)
            .background(surface)
            // Only a breath of the app's colour from the upper right corner, fading out by the middle of the tile
            // (web: radial-gradient(120% 130% at 100% 0%, app-light 14%, transparent 50%)); the AI tile a little more
            .drawBehind {
                val tint = (if (dark) app.deep else app.light).copy(alpha = if (app.featured) (if (dark) 0.14f else 0.20f) else if (dark) 0.08f else 0.10f)
                drawRect(Brush.radialGradient(listOf(tint, Color.Transparent), center = Offset(size.width, 0f), radius = size.width * if (app.featured) 0.6f else 0.5f))
            }
            .border(1.dp, Agora.colors.hairline, shape)
            .clickable(onClick = onClick)
    ) {
        // The app's sign once more, large and faint in the corner (the AI tile has its sparkles instead). It lies in
        // the tile's own size and spills over its edge, so it never makes the tile taller than its text
        if (!app.featured) Box(Modifier.matchParentSize()) {
            Icon(
                app.icon, null,
                Modifier.align(Alignment.BottomEnd).offset(x = 22.dp, y = 30.dp).requiredSize(96.dp).rotate(-14f),
                tint = tone.copy(alpha = if (dark) 0.06f else 0.05f)
            )
        }
        else Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 40.dp).size(width = 60.dp, height = 44.dp)) {
            val sparkle = tone.copy(alpha = if (dark) 0.25f else 0.3f)
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.align(Alignment.TopEnd).size(24.dp), tint = sparkle)
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.align(Alignment.TopEnd).offset(x = (-28).dp, y = 20.dp).size(14.dp), tint = sparkle.copy(alpha = sparkle.alpha * 0.7f))
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.align(Alignment.TopEnd).offset(x = (-36).dp).size(10.dp), tint = sparkle.copy(alpha = sparkle.alpha * 0.5f))
        }
        Row(Modifier.padding(start = 16.dp, end = 14.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            val iconShape = RoundedCornerShape(14.dp)
            Box(
                Modifier.size(46.dp).clip(iconShape)
                    .background(app.deep.copy(alpha = if (dark) 0.18f else 0.12f).compositeOver(surface))
                    .border(1.dp, app.deep.copy(alpha = if (dark) 0.30f else 0.22f), iconShape),
                contentAlignment = Alignment.Center
            ) { Icon(app.icon, null, Modifier.size(22.dp), tint = tone) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(stringResource(app.title), style = MaterialTheme.typography.titleMedium, color = Agora.colors.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(app.desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp))
            }
            if (app.featured && columnCount(300.dp, gap = 14.dp) > 1) Row(
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
}
