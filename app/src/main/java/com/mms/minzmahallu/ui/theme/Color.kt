package com.mms.minzmahallu.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Custom design tokens — NO Material Design colors. Mirrors Electron globals.css. */
@Immutable
data class MmsColors(
    val bg: Color,
    val bodyBg: Color,
    val panel: Color,
    val panel2: Color,
    val head: Color,
    val line: Color,
    val line2: Color,
    val tx: Color,
    val mut: Color,
    val fnt: Color,
    val em: Color,
    val emd: Color,
    val emdd: Color,
    val selBg: Color,
    val roseBg: Color,
    val roseLine: Color,
    val cEm: Color,
    val cGold: Color,
    val goldDeep: Color,
    val cSky: Color,
    val cRose: Color,
    val isDark: Boolean,
)

val LightColors = MmsColors(
    bg = Color(0xFFF6F8FA),
    bodyBg = Color(0xFFF0F3F6),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFF8FAFC),
    head = Color(0xFFFAFBFD),
    line = Color(0xFFE7ECF1),
    line2 = Color(0xFFD4DCE4),
    tx = Color(0xFF182230),
    mut = Color(0xFF55657A),
    fnt = Color(0xFF93A1B3),
    em = Color(0xFF0D9488),
    emd = Color(0xFF0B7E74),
    emdd = Color(0xFF0A6B63),
    selBg = Color(0xFFE7F6F3),
    roseBg = Color(0xFFFDEEF0),
    roseLine = Color(0xFFF5C6CC),
    cEm = Color(0xFF0D9488),
    cGold = Color(0xFFD9930A),
    goldDeep = Color(0xFF9A6B00),
    cSky = Color(0xFF2563EB),
    cRose = Color(0xFFE11D48),
    isDark = false,
)

val LightColorsFixed = LightColors

val DarkColors = MmsColors(
    bg = Color(0xFF0E131A),
    bodyBg = Color(0xFF0A0E14),
    panel = Color(0xFF151C25),
    panel2 = Color(0xFF10161E),
    head = Color(0xFF182130),
    line = Color(0xFF223040),
    line2 = Color(0xFF2F4052),
    tx = Color(0xFFE8EEF5),
    mut = Color(0xFFA9B8C9),
    fnt = Color(0xFF77889C),
    em = Color(0xFF2DD4BF),
    emd = Color(0xFF1FB3A1),
    emdd = Color(0xFF178C7E),
    selBg = Color(0xFF10342F),
    roseBg = Color(0xFF33121C),
    roseLine = Color(0xFF6A2133),
    cEm = Color(0xFF2DD4BF),
    cGold = Color(0xFFF2B83D),
    goldDeep = Color(0xFFE0AA3E),
    cSky = Color(0xFF6EA8FF),
    cRose = Color(0xFFFB7185),
    isDark = true,
)

@Immutable
data class ModuleTint(
    val sc: Color,
    val sb: Color,
    val st: Color,
    val sl: Color,
)

object Tints {
    fun light(id: String): ModuleTint = when (id) {
        "em" -> ModuleTint(Color(0xFF0D9488), Color(0xFFE6F6F3), Color(0xFF0B6E64), Color(0xFFBDEAE3))
        "teal" -> ModuleTint(Color(0xFF14A89B), Color(0xFFDEF5F2), Color(0xFF0B6E64), Color(0xFFB5E6DF))
        "sky" -> ModuleTint(Color(0xFF2563EB), Color(0xFFE7EFFE), Color(0xFF1D4ED8), Color(0xFFC4D8FB))
        "cyan" -> ModuleTint(Color(0xFF0AA2C0), Color(0xFFE0F5FA), Color(0xFF0B6F86), Color(0xFFB5E6F2))
        "blue" -> ModuleTint(Color(0xFF4C7CE8), Color(0xFFE8EEFD), Color(0xFF2B4FAE), Color(0xFFC0D0F8))
        "vio" -> ModuleTint(Color(0xFF8262E8), Color(0xFFEDE8FD), Color(0xFF5C3FC0), Color(0xFFD0C4F8))
        "pink" -> ModuleTint(Color(0xFFE5609A), Color(0xFFFDE7F0), Color(0xFFB0356A), Color(0xFFF6C4DB))
        "rose" -> ModuleTint(Color(0xFFE11D48), Color(0xFFFDEEF1), Color(0xFFB31136), Color(0xFFF5C6CD))
        "orange" -> ModuleTint(Color(0xFFEA6F2D), Color(0xFFFDEEE3), Color(0xFFB04F15), Color(0xFFF8D3B6))
        "gold" -> ModuleTint(Color(0xFFD9930A), Color(0xFFFDF5DD), Color(0xFF96640A), Color(0xFFF2E2A8))
        "slate" -> ModuleTint(Color(0xFF66788F), Color(0xFFEAEEF3), Color(0xFF46586E), Color(0xFFCDD6E0))
        else -> ModuleTint(Color(0xFF0D9488), Color(0xFFE6F6F3), Color(0xFF0B6E64), Color(0xFFBDEAE3))
    }
    fun dark(id: String): ModuleTint = when (id) {
        "em" -> ModuleTint(Color(0xFF2DD4BF), Color(0xFF0F2F2A), Color(0xFF7CE8DA), Color(0xFF1D4D44))
        "teal" -> ModuleTint(Color(0xFF35C4B4), Color(0xFF0D2B28), Color(0xFF7BE8DB), Color(0xFF1A4A44))
        "sky" -> ModuleTint(Color(0xFF6EA8FF), Color(0xFF131F38), Color(0xFFA9C6FF), Color(0xFF2A4070))
        "cyan" -> ModuleTint(Color(0xFF38C3DD), Color(0xFF0C2831), Color(0xFF85E2F4), Color(0xFF144A59))
        "blue" -> ModuleTint(Color(0xFF7EA3F5), Color(0xFF152240), Color(0xFFA9C1FB), Color(0xFF274070))
        "vio" -> ModuleTint(Color(0xFFA48AF2), Color(0xFF221A41), Color(0xFFC8B8FB), Color(0xFF3B2F6E))
        "pink" -> ModuleTint(Color(0xFFEF87B4), Color(0xFF361226), Color(0xFFF6A9CB), Color(0xFF5C2340))
        "rose" -> ModuleTint(Color(0xFFFB7185), Color(0xFF381220), Color(0xFFFDA4AF), Color(0xFF632438))
        "orange" -> ModuleTint(Color(0xFFF59D5E), Color(0xFF331C0C), Color(0xFFF9BD90), Color(0xFF5C3616))
        "gold" -> ModuleTint(Color(0xFFF2C14E), Color(0xFF322609), Color(0xFFF7D98D), Color(0xFF5C4716))
        "slate" -> ModuleTint(Color(0xFF93A7C4), Color(0xFF1A2230), Color(0xFFC0CDE2), Color(0xFF31405A))
        else -> ModuleTint(Color(0xFF2DD4BF), Color(0xFF0F2F2A), Color(0xFF7CE8DA), Color(0xFF1D4D44))
    }
    fun of(id: String, isDark: Boolean) = if (isDark) dark(id) else light(id)
}

val LocalMmsColors = staticCompositionLocalOf { LightColorsFixed }
val LocalTint = staticCompositionLocalOf { Tints.light("em") }
