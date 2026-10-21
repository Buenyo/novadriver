package app.novadrive.launcher.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.novadrive.launcher.apps.AppEntry
import app.novadrive.launcher.apps.AppsRepository
import app.novadrive.launcher.ui.theme.LocalOnWallpaper

/** Alvo mínimo de toque no carro (diretriz do projeto: ≥ 64 dp). */
val TouchMin = 64.dp

val CardShape = RoundedCornerShape(22.dp)

/** Cartão "vidro escuro": cor sólida translúcida, sem blur (blur não existe na API 24 e pesa na Mali). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    content: @Composable () -> Unit,
) {
    val onWall = LocalOnWallpaper.current
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (onWall) cs.surface.copy(alpha = 0.58f) else cs.surfaceVariant,
        contentColor = cs.onSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (onWall) 0.10f else 0.04f)),
        content = content,
    )
}

/** Área clicável com toque longo opcional, papel de botão para acessibilidade. */
fun Modifier.tap(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier =
    combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)

@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = TouchMin,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) cs.primary else Color.White.copy(alpha = 0.08f))
            .then(if (enabled) Modifier.tap(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            tint = (if (filled) cs.onPrimary else cs.onSurface).copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    primary: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .defaultMinSize(minHeight = TouchMin)
            .clip(RoundedCornerShape(50))
            .background(if (primary) cs.primary else cs.onSurface.copy(alpha = 0.10f))
            .tap(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = if (primary) cs.onPrimary else cs.onSurface, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (primary) cs.onPrimary else cs.onSurface, maxLines = 1)
    }
}

/** Ícone do app carregado sob demanda (fora da thread principal) e guardado no cache LRU. */
@Composable
fun AppIcon(repo: AppsRepository, app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon: ImageBitmap? by produceState<ImageBitmap?>(null, app.component, px) { value = repo.icon(app.component, px) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

@Composable
fun AppTile(repo: AppsRepository, app: AppEntry, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 116.dp)
            .clip(RoundedCornerShape(18.dp))
            .tap(onLongClick = onLongClick, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(repo, app, 56.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(app.novadrive.launcher.R.drawable.ic_back, "Voltar", onBack)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1)
        trailing()
    }
}
