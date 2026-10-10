package org.agora.app.ui.components

import org.agora.app.data.model.User
import coil3.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.agora.app.AppContainer
import org.agora.app.R
import org.agora.app.data.remote.ApiException

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun ProvideAppLocals(container: AppContainer, snackbar: SnackbarHostState, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContainer provides container, LocalSnackbar provides snackbar, content = content)

/** Runs a suspending action, tracks `busy` and reports errors (German server messages) as snackbar. */
class ActionRunner(private val scope: CoroutineScope, private val snackbar: SnackbarHostState, private val fallbackError: String) {
    var busy by mutableStateOf(false)
        private set

    fun run(successMessage: String? = null, onError: (String) -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                block()
                successMessage?.let { snackbar.showSnackbar(it) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = (e as? ApiException)?.message?.takeIf { it.isNotBlank() } ?: fallbackError
                onError(message)
                snackbar.showSnackbar(message)
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberActionRunner(): ActionRunner {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val fallback = stringResource(R.string.error_generic)
    return remember(scope, snackbar) { ActionRunner(scope, snackbar, fallback) }
}

/** `.card`: white surface floating on the page: 20px radius, hairline border and a soft, slightly deep shadow. */
@Composable
fun AgoraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isSystemDark()) 0.8f else 1f)),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = MaterialTheme.shapes.medium,
    elevation: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    // Wide, faint shadow like the PWA's `box-shadow: 0 4px 12px rgba(15,23,42,.06)`; its size is fixed, so it stays
    // within the 12dp gap to the next card ([elevation] scales it, 0 = flat)
    val shadow = Modifier.softShadow(Color(0xFF0F172A).copy(alpha = 0.10f), blur = elevation * 2, shape = shape, offsetY = elevation / 2)
    val inner: @Composable ColumnScope.() -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth().then(shadow), colors = colors, border = border, shape = shape, content = inner)
    else Card(modifier = modifier.fillMaxWidth().then(shadow), colors = colors, border = border, shape = shape, content = inner)
}

/** Whether the dark scheme is active (the PWA uses weaker borders there). */
@Composable
private fun isSystemDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.2f

/** Card tinted with a status colour (like the PWA's coloured status cards). */
@Composable
fun TintedCard(color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, contentPadding: PaddingValues = PaddingValues(18.dp), content: @Composable ColumnScope.() -> Unit) =
    AgoraCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = color.copy(alpha = 0.07f).compositeOver(MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, color.copy(alpha = 0.28f)),
        contentPadding = contentPadding,
        content = content
    )

/** `.hero-card`: diagonal gradient with the two translucent decoration circles, centred content. */
@Composable
fun GradientCard(brush: Brush, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .softShadow(Color(0xFF06B6D4).copy(alpha = 0.28f), blur = 14.dp, shape = MaterialTheme.shapes.large, offsetY = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(brush)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.62f, center = Offset(size.width * 0.07f, size.height * 0.06f))
                drawCircle(Color.White.copy(alpha = 0.14f), radius = size.minDimension * 0.52f, center = Offset(size.width * 1.0f, size.height * 1.0f))
            }
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content) }
}

/** Uppercase section header with a trailing hairline (`.events-month-header`). */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (count != null) Text(
            "($count)", style = MaterialTheme.typography.labelLarge, color = color.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 8.dp)
        )
        Box(Modifier.weight(1f).padding(start = 10.dp, end = 4.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        trailing()
    }
}

/** Status badge: tinted, thin border, bold (`.event-badge`, `.request-status`). */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, caps: Boolean = false) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                if (caps) text.uppercase() else text,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = if (caps) 0.8.sp else 0.sp),
                fontWeight = if (caps) FontWeight.ExtraBold else FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

fun initials(name: String): String =
    name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

/** Role ring around a picture, like the PWA's `.avatar-ring-*`: gradient plus a glow in the same colour. */
data class AvatarRing(val brush: Brush, val glow: Color)

/** Ring colours: members cyan-green, approved mentors purple, mentoring managers gold-red. */
object AvatarRings {
    val Standard = AvatarRing(Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF10B981))), Color(0xFF06B6D4))
    val Mentor = AvatarRing(Brush.linearGradient(listOf(Color(0xFFC084FC), Color(0xFF7C3AED))), Color(0xFF7C3AED))
    val Manager = AvatarRing(Brush.linearGradient(listOf(Color(0xFFFACC15), Color(0xFFEF4444))), Color(0xFFEF4444))

    fun of(user: User): AvatarRing = when {
        user.managesMentoring -> Manager
        user.isApprovedMentor || user.mentorStatus == "approved" -> Mentor
        else -> Standard
    }
}

/**
 * Round avatar: server profile picture (needs the token, 204 = none) with initials fallback. With a [ring] it gets
 * a 3dp gradient ring, a 2dp gap in the surface colour and a soft glow, so the role colour stands out.
 */
@Composable
fun UserAvatar(uid: String?, name: String, size: Dp = 40.dp, ring: AvatarRing? = null, modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val outer = modifier
        .size(size)
        .then(
            if (ring != null) Modifier
                .softShadow(ring.glow.copy(alpha = 0.38f), blur = 10.dp, shape = CircleShape, offsetY = 2.dp)
                .clip(CircleShape)
                .background(ring.brush)
                .padding(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(2.dp)
            else Modifier
        )
        .clip(CircleShape)
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(
                initials(name),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp
            )
        }
    }
    // Initials underneath, the picture on top once it is there. (No SubcomposeAsyncImage: it subcomposes per
    // avatar, which is slow in lists; members without a picture simply keep their initials.)
    Box(outer) {
        fallback()
        if (!uid.isNullOrBlank()) AsyncImage(
            model = remember(uid) { ImageRequest.Builder(context).data(container.repo.profilePictureUrl(uid)).crossfade(true).build() },
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Round tinted icon like the PWA's empty states
        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action()
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ButtonProgress() = CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)

@Composable
fun VerticalSpace(height: Dp = 12.dp) = Spacer(Modifier.height(height))
