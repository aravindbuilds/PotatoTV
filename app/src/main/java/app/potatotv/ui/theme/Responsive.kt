package app.potatotv.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** True for phone-portrait layout, where the TV chrome is replaced by a bottom bar. */
@Composable
fun isCompactScreen(): Boolean {
    val config = LocalConfiguration.current
    return config.orientation == Configuration.ORIENTATION_PORTRAIT ||
        config.screenWidthDp < 600
}

/**
 * The TV layout is designed for a 960 × 540 dp screen. On a phone in landscape
 * we scale it down uniformly so nothing clips. In portrait we leave density
 * alone and let the dedicated narrow-screen layout take over.
 */
@Composable
fun ResponsiveTvUi(content: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
        content()
        return
    }
    val current = LocalDensity.current
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
