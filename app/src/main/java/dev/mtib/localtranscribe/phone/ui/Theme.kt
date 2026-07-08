package dev.mtib.localtranscribe.phone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Black = Color(0xFF000000)
private val Panel = Color(0xFF141A22)
private val Mint = Color(0xFF75E3BE)
private val Violet = Color(0xFFAFA2FF)

private val Scheme = darkColorScheme(
    primary = Mint,
    onPrimary = Black,
    secondary = Violet,
    onSecondary = Black,
    background = Black,
    onBackground = Color(0xFFE6ECF2),
    surface = Black,
    onSurface = Color(0xFFE6ECF2),
    surfaceVariant = Panel,
    onSurfaceVariant = Color(0xFFA8B3BF),
    error = Color(0xFFFF6B6B),
)

@Composable
fun LocalTranscribeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
