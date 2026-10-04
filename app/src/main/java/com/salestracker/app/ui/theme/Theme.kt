package com.salestracker.app.ui.theme

import com.salestracker.app.R
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Light / dark preference chosen in the Appearance dialog. */
enum class DarkMode(@StringRes val label: Int) { SYSTEM(R.string.mode_auto), LIGHT(R.string.mode_light), DARK(R.string.mode_dark) }

/**
 * The hand-picked colors for one palette in one mode. Every other Material color
 * (cards, nav bar, dividers…) is derived from these so the whole app stays in tune.
 */
private class Tones(
    val primary: Long, val onPrimary: Long, val primaryContainer: Long, val onPrimaryContainer: Long,
    val secondary: Long, val onSecondary: Long, val secondaryContainer: Long, val onSecondaryContainer: Long,
    val tertiary: Long,
    val background: Long, val onSurface: Long,
    val surfaceVariant: Long, val onSurfaceVariant: Long, val outline: Long,
    /** How much of the primary color tints the card/nav backgrounds (0 = neutral gray). */
    val tint: Float = 0.05f,
)

private fun Tones.toScheme(dark: Boolean): ColorScheme {
    val bg = Color(background)
    val on = Color(onSurface)
    val tinted = lerp(bg, Color(primary), tint)
    // Each container step moves a little further from the background toward the text color.
    fun level(f: Float) = lerp(tinted, on, f)
    val steps = if (dark) listOf(0.0f, 0.03f, 0.06f, 0.09f, 0.13f) else listOf(0.0f, 0.02f, 0.04f, 0.06f, 0.09f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(primary), onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(primaryContainer),
        secondary = Color(secondary), onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer), onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary), onTertiary = Color(onPrimary),
        tertiaryContainer = Color(secondaryContainer), onTertiaryContainer = Color(onSecondaryContainer),
        background = bg, onBackground = on,
        surface = bg, onSurface = on,
        surfaceVariant = Color(surfaceVariant), onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(primary),
        inverseSurface = on, inverseOnSurface = bg,
        outline = Color(outline), outlineVariant = Color(surfaceVariant),
        surfaceBright = level(if (dark) 0.16f else 0f),
        surfaceDim = level(if (dark) 0f else 0.11f),
        surfaceContainerLowest = if (dark) lerp(bg, Color.Black, 0.3f) else Color.White,
        surfaceContainerLow = level(steps[1]),
        surfaceContainer = level(steps[2]),
        surfaceContainerHigh = level(steps[3]),
        surfaceContainerHighest = level(steps[4]),
    )
}

enum class AppPalette(@StringRes val label: Int, @StringRes val description: Int, private val light: Tones, private val dark: Tones) {
    TEAL(
        R.string.palette_teal, R.string.palette_teal_desc,
        light = Tones(
            0xFF1B5E5A, 0xFFFFFFFF, 0xFFBCEBE5, 0xFF00201E,
            0xFFB4651A, 0xFFFFFFFF, 0xFFFFDCC0, 0xFF2E1500,
            0xFF3A5BA0,
            0xFFF7FAF9, 0xFF171D1C,
            0xFFDAE5E2, 0xFF3F4947, 0xFF6F7977,
        ),
        dark = Tones(
            0xFF7FD3CB, 0xFF003734, 0xFF00504B, 0xFFBCEBE5,
            0xFFF2B36B, 0xFF4B2800, 0xFF6B3B00, 0xFFFFDCC0,
            0xFFA8C0F0,
            0xFF101413, 0xFFDEE4E2,
            0xFF3F4947, 0xFFBEC9C6, 0xFF899390,
        ),
    ),
    GRAYSCALE(
        R.string.palette_gray, R.string.palette_gray_desc,
        light = Tones(
            0xFF333333, 0xFFFFFFFF, 0xFFE0E0E0, 0xFF1A1A1A,
            0xFF5C5C5C, 0xFFFFFFFF, 0xFFE8E8E8, 0xFF1F1F1F,
            0xFF474747,
            0xFFF7F7F7, 0xFF1B1B1B,
            0xFFE2E2E2, 0xFF474747, 0xFF777777,
            tint = 0f,
        ),
        dark = Tones(
            0xFFE2E2E2, 0xFF1F1F1F, 0xFF474747, 0xFFEDEDED,
            0xFFBDBDBD, 0xFF262626, 0xFF3D3D3D, 0xFFE3E3E3,
            0xFFCFCFCF,
            0xFF121212, 0xFFE6E6E6,
            0xFF444444, 0xFFC4C4C4, 0xFF8E8E8E,
            tint = 0f,
        ),
    ),
    SUNSET(
        R.string.palette_sunset, R.string.palette_sunset_desc,
        light = Tones(
            0xFFA0521F, 0xFFFFFFFF, 0xFFFFDBC8, 0xFF361400,
            0xFF8F6342, 0xFFFFFFFF, 0xFFFBE2CC, 0xFF2E1704,
            0xFF6B7536,
            0xFFFFF8F3, 0xFF241A14,
            0xFFF3DFD2, 0xFF54443A, 0xFF867469,
        ),
        dark = Tones(
            0xFFFFB68C, 0xFF552100, 0xFF773512, 0xFFFFDBC8,
            0xFFE6C0A0, 0xFF432B16, 0xFF5C412A, 0xFFFBE2CC,
            0xFFC8D18F,
            0xFF1C1510, 0xFFF0DFD6,
            0xFF53443A, 0xFFD8C2B6, 0xFFA08D82,
        ),
    ),
    ROSE(
        R.string.palette_rose, R.string.palette_rose_desc,
        light = Tones(
            0xFF9C3D49, 0xFFFFFFFF, 0xFFFFD9DC, 0xFF3F0012,
            0xFF8A5A5E, 0xFFFFFFFF, 0xFFFFDADC, 0xFF32171A,
            0xFF7A5A2E,
            0xFFFFF8F7, 0xFF221A1A,
            0xFFF4DDDD, 0xFF524344, 0xFF857374,
        ),
        dark = Tones(
            0xFFFFB2B8, 0xFF5F1123, 0xFF7E2834, 0xFFFFD9DC,
            0xFFE6BDC0, 0xFF44292C, 0xFF5D3F42, 0xFFFFDADC,
            0xFFEBC08A,
            0xFF1B1314, 0xFFF0DEDE,
            0xFF524344, 0xFFD7C1C2, 0xFFA08C8D,
        ),
    ),
    SAGE(
        R.string.palette_sage, R.string.palette_sage_desc,
        light = Tones(
            0xFF4A6741, 0xFFFFFFFF, 0xFFCCEBBF, 0xFF072100,
            0xFF6E6A3E, 0xFFFFFFFF, 0xFFF0EAB8, 0xFF201D00,
            0xFF3A6668,
            0xFFF8FAF3, 0xFF1A1C18,
            0xFFDFE4D7, 0xFF43483F, 0xFF74796D,
        ),
        dark = Tones(
            0xFFB1D1A4, 0xFF1D3716, 0xFF334E2B, 0xFFCCEBBF,
            0xFFD4CEA0, 0xFF363214, 0xFF4D4928, 0xFFF0EAB8,
            0xFFA2CED0,
            0xFF11140F, 0xFFE2E3DC,
            0xFF43483F, 0xFFC3C8BB, 0xFF8D9286,
        ),
    ),
    ;

    fun scheme(dark: Boolean): ColorScheme = if (dark) this.dark.toScheme(true) else light.toScheme(false)

    /** Three colors shown as dots in the palette picker. */
    fun swatch(dark: Boolean): List<Color> {
        val t = if (dark) this.dark else light
        return listOf(Color(t.primary), Color(t.secondary), Color(t.primaryContainer))
    }
}

@Composable
fun isDarkTheme(mode: DarkMode): Boolean = when (mode) {
    DarkMode.SYSTEM -> isSystemInDarkTheme()
    DarkMode.LIGHT -> false
    DarkMode.DARK -> true
}

@Composable
fun SalesTrackerTheme(
    palette: AppPalette = AppPalette.TEAL,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = palette.scheme(dark), content = content)
}
