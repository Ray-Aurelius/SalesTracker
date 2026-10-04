@file:OptIn(ExperimentalTextApi::class)

package com.salestracker.app.ui.theme

import android.graphics.Typeface
import androidx.annotation.StringRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.salestracker.app.R

/** Builds a family from a variable-weight font file, so bold and medium text look right. */
private fun variable(res: Int) = FontFamily(
    listOf(300, 400, 500, 600, 700).map { w ->
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

/**
 * Font styles. All are built into the app (no downloading — the app has no internet access) or come with
 * the phone. Letters a font doesn't cover (e.g. Chinese or Arabic) automatically use the phone's own font.
 */
enum class AppFont(@StringRes val label: Int, @StringRes val description: Int, val family: () -> FontFamily) {
    STANDARD(R.string.font_standard, R.string.font_standard_desc, { FontFamily.Default }),
    READABLE(R.string.font_readable, R.string.font_readable_desc, {
        FontFamily(Font(R.font.atkinson_regular, FontWeight.Normal), Font(R.font.atkinson_bold, FontWeight.Bold))
    }),
    MODERN(R.string.font_modern, R.string.font_modern_desc, { variable(R.font.lexend_variable) }),
    ROUNDED(R.string.font_rounded, R.string.font_rounded_desc, { variable(R.font.nunito_variable) }),
    CLASSIC(R.string.font_classic, R.string.font_classic_desc, { FontFamily.Serif }),
    COMPACT(R.string.font_compact, R.string.font_compact_desc, { FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL)) }),
}

/** Text size choices, as a multiple of the phone's own text size setting. */
val TEXT_SCALES = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)

/** The standard Material type scale with every style switched to [family]. */
fun typographyFor(family: FontFamily): Typography {
    val t = Typography()
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}
