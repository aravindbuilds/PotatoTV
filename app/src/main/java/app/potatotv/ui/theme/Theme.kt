package app.potatotv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PotatoColors = darkColorScheme(
    primary = PotatoAccent,
    onPrimary = PotatoOnAccent,
    primaryContainer = PotatoAccentDeep,
    onPrimaryContainer = PotatoOnAccent,
    secondary = PotatoRating,
    onSecondary = Color(0xFF00210D),
    tertiary = PotatoDanger,
    background = PotatoBg,
    onBackground = PotatoText,
    surface = PotatoBgElevated,
    onSurface = PotatoText,
    surfaceVariant = PotatoSurface,
    onSurfaceVariant = PotatoTextDim,
    outline = PotatoLine,
    error = PotatoDanger,
    onError = Color.White
)

private val PotatoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PotatoTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PotatoColors,
        typography = PotatoTypography,
        shapes = PotatoShapes,
        content = content
    )
}

/** Shared layout rhythm. All home-screen spacing routes through these tokens. */
object PotatoDimens {
    val screenPadding = 48.dp
    val screenPaddingMedium = 32.dp
    val screenPaddingCompact = 20.dp

    val sectionGap = 36.dp
    val cardGap = 16.dp
    val compactCardGap = 12.dp

    val posterWidth = 176.dp
    val posterHeight = 264.dp
    val posterWidthCompact = 132.dp
    val posterHeightCompact = 198.dp
    val cardRadius = 14.dp

    /** Keeps rails optically centred on very wide displays. */
    val maxContentWidth = 1600.dp
    val heroMaxWidth = 980.dp
}
