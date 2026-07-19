package com.beharsh.mobile.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Canvas      = Color(0xFFF4F6FA)
val CardBg      = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1E293B)
val Accent      = Color(0xFF2563EB)
val TextSub     = Color(0xFF64748B)

private val BEHarshColors = lightColorScheme(
    primary          = Accent,
    onPrimary        = Color.White,
    background       = Canvas,
    onBackground     = TextPrimary,
    surface          = CardBg,
    onSurface        = TextPrimary,
    surfaceVariant   = Color(0xFFE2E8F0),
    onSurfaceVariant = TextSub,
    secondary        = Accent,
    onSecondary      = Color.White
)

private val BEHarshShapes = Shapes(
    small  = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large  = RoundedCornerShape(16.dp)
)

@Composable
fun BEHarshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BEHarshColors,
        shapes      = BEHarshShapes,
        content     = content
    )
}
