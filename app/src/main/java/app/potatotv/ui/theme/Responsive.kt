package app.potatotv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * The whole UI is laid out for a 960 × 540 dp TV screen. On a phone in
 * landscape the available box is often only ~640 × 360 dp, so a uniform
 * density scale keeps every row, panel and poster proportional instead of
 * clipping. TV screens are untouched (scale 1.0).
 */
@Composable
fun ResponsiveTvUi(content: @Composable () -> Unit) {
    val current = LocalDensity.current
    val config = LocalConfiguration.current
    val widthFactor = config.screenWidthDp / 960f
    val heightFactor = config.screenHeightDp / 540f
    val scale = minOf(widthFactor, heightFactor).coerceIn(0.62f, 1f)
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = current.density * scale,
            fontScale = current.fontScale * scale
        ),
        content = content
    )
}
