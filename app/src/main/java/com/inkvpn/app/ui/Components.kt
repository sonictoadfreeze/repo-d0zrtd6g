package com.inkvpn.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkvpn.app.ui.theme.InkDanger
import com.inkvpn.app.ui.theme.InkSecondary
import com.inkvpn.app.ui.theme.InkSuccess
import com.inkvpn.app.ui.theme.InkSurface
import com.inkvpn.app.ui.theme.InkWarning

/** Glassmorphism container: translucent surface + subtle border + rounded corners. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    var m = modifier
        .clip(shape)
        .background(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.02f))
            )
        )
        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape)
    if (onClick != null) m = m.clickable { onClick() }
    Box(modifier = m.padding(padding)) { content() }
}

/** Colored ping badge: <=100 green, <=200 amber, else red. */
@Composable
fun PingBadge(pingMs: Int?) {
    val (color, label) = when {
        pingMs == null -> InkWarning to "—"
        pingMs <= 100 -> InkSuccess to "${pingMs}ms"
        pingMs <= 200 -> InkWarning to "${pingMs}ms"
        else -> InkDanger to "${pingMs}ms"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Protocol badge (VLESS / VMess / Trojan / etc). */
@Composable
fun ProtocolBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(InkSecondary.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = InkSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Pulsing ring used behind the power button while connected. */
@Composable
fun PulseRing(size: Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "scale"
    )
    Box(
        modifier = Modifier
            .size(size * scale)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.10f))
    )
}

val InkSurfaceColor: Color = InkSurface
