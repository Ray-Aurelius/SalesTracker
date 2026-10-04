package com.salestracker.app.data

import androidx.annotation.StringRes
import com.salestracker.app.R

/**
 * Colors for marking important days on the calendar. Mid-tone shades that stand out on both light
 * and dark backgrounds; the day number on top switches between black and white to stay readable.
 */
enum class HighlightColor(val argb: Long, @StringRes val label: Int, val symbol: String) {
    // Each color also has its own symbol, shown when "Color-blind-friendly highlights" is on,
    // so highlights can be told apart without relying on color.
    RED(0xFFE53935, R.string.color_red, "!"),
    ORANGE(0xFFFB8C00, R.string.color_orange, "▲"),
    YELLOW(0xFFFDD835, R.string.color_yellow, "★"),
    GREEN(0xFF43A047, R.string.color_green, "✓"),
    BLUE(0xFF1E88E5, R.string.color_blue, "■"),
    PURPLE(0xFF8E24AA, R.string.color_purple, "◆"),
    PINK(0xFFD81B60, R.string.color_pink, "♥"),
}
