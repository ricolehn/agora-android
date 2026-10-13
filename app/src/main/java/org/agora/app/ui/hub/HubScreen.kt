package org.agora.app.ui.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.agora.app.R
import org.agora.app.ui.components.pagePadding
import org.agora.app.ui.theme.Agora

/** A tile of the Extras hub (web: HUB_APPS). */
data class HubApp(val route: String, val icon: ImageVector, val color: Color, val title: Int, val desc: Int)

/** The apps of the hub; the AI chat only when it is switched on and the person may use it. Polls stay off (v3.1). */
fun hubApps(aiAvailable: Boolean): List<HubApp> = buildList {
    if (aiAvailable) add(HubApp("ai", Icons.Outlined.AutoAwesome, Color(0xFF7C3AED), R.string.hub_ai_title, R.string.hub_ai_desc))
    add(HubApp("songbook", Icons.Outlined.MusicNote, Color(0xFF0891B2), R.string.hub_songbook_title, R.string.hub_songbook_desc))
    add(HubApp("prayers", Icons.Outlined.FavoriteBorder, Color(0xFFE11D48), R.string.hub_prayers_title, R.string.hub_prayers_desc))
}

/** Extras: one permanent nav entry for further apps of the community (web: #hub). */
@Composable
fun HubScreen(apps: List<HubApp>, contentPadding: PaddingValues, onOpen: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = pagePadding(contentPadding.calculateTopPadding() + 4.dp, contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(bottom = 6.dp)) {
                Text(stringResource(R.string.nav_hub), style = MaterialTheme.typography.headlineLarge, color = Agora.colors.heading)
                Text(stringResource(R.string.hub_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
        items(apps, key = { it.route }) { app -> HubTile(app) { onOpen(app.route) } }
    }
}

@Composable
private fun HubTile(app: HubApp, onClick: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        border = BorderStroke(1.dp, Agora.colors.hairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(app.color.copy(alpha = 0.13f).compositeOver(surface)),
                contentAlignment = Alignment.Center
            ) { Icon(app.icon, null, Modifier.size(24.dp), tint = app.color) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(stringResource(app.title), style = MaterialTheme.typography.titleMedium, color = Agora.colors.heading)
                Text(stringResource(app.desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}
