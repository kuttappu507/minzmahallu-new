package com.mms.minzmahallu.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Modern Fluidic Design System - Premium UI Colors with Gradients & Shadows */

// Primary Brand Palette with Gradient Variants
val Emerald = Color(0xFF10B981)
val EmeraldLight = Color(0xFF34D399)
val EmeraldDark = Color(0xFF059669)
val EmeraldDarker = Color(0xFF047857)

val Sky = Color(0xFF0EA5E9)
val SkyLight = Color(0xFF38BDF8)
val SkyDark = Color(0xFF0284C7)

val Rose = Color(0xFFF43F5E)
val RoseLight = Color(0xFFFB7185)
val RoseDark = Color(0xFFE11D48)

val Amber = Color(0xFFF59E0B)
val AmberLight = Color(0xFFFBBF24)
val AmberDark = Color(0xFFD97706)

val Violet = Color(0xFF8B5CF6)
val VioletLight = Color(0xFFA78BFA)
val VioletDark = Color(0xFF7C3AED)

val Teal = Color(0xFF14B8A6)
val TealLight = Color(0xFF2DD4BF)
val TealDark = Color(0xFF0D9488)

val Indigo = Color(0xFF6366F1)
val IndigoLight = Color(0xFF818CF8)
val IndigoDark = Color(0xFF4F46E5)

val Cyan = Color(0xFF06B6D4)
val CyanLight = Color(0xFF22D3EE)
val CyanDark = Color(0xFF0891B2)

// Modern Light Theme - Clean & Airy
val LightBg = Color(0xFFF8FAFC)
val LightBgGradient = Color(0xFFF1F5F9)
val LightPanel = Color(0xFFFFFFFF)
val LightPanel2 = Color(0xFFF8FAFC)
val LightPanel3 = Color(0xFFF1F5F9)
val LightHead = Color(0xFFFAFBFD)
val LightLine = Color(0xFFE2E8F0)
val LightLine2 = Color(0xFFCBD5E1)
val LightTx = Color(0xFF0F172A)
val LightTxSec = Color(0xFF1E293B)
val LightMut = Color(0xFF64748B)
val LightFnt = Color(0xFF94A3B8)

// Modern Dark Theme - Deep & Premium
val DarkBg = Color(0xFF0B1221)
val DarkBgGradient = Color(0xFF0F172A)
val DarkPanel = Color(0xFF1E293B)
val DarkPanel2 = Color(0xFF1E293B)
val DarkPanel3 = Color(0xFF2D3748)
val DarkHead = Color(0xFF1A2332)
val DarkLine = Color(0xFF2D3748)
val DarkLine2 = Color(0xFF3D4C5F)
val DarkTx = Color(0xFFF1F5F9)
val DarkTxSec = Color(0xFFE2E8F0)
val DarkMut = Color(0xFF94A3B8)
val DarkFnt = Color(0xFF64748B)

// Elevation & Glow Effects
val ShadowSm = Color(0x0A000000)
val ShadowMd = Color(0x14000000)
val ShadowLg = Color(0x1F000000)
val GlowEmerald = Color(0x4010B981)
val GlowSky = Color(0x400EA5E9)
val GlowRose = Color(0x40F43F5E)
val GlowAmber = Color(0x40F59E0B)

/** Custom design tokens — Modern fluidic design with gradients and elevations. */
@Immutable
data class MmsColors(
    val bg: Color,
    val bodyBg: Color,
    val bodyBgGradient: Color,
    val panel: Color,
    val panel2: Color,
    val panel3: Color,
    val head: Color,
    val line: Color,
    val line2: Color,
    val tx: Color,
    val txSec: Color,
    val mut: Color,
    val fnt: Color,
    val em: Color,
    val emLight: Color,
    val emd: Color,
    val emdd: Color,
    val sky: Color,
    val skyLight: Color,
    val skyd: Color,
    val rose: Color,
    val roseLight: Color,
    val rosd: Color,
    val amber: Color,
    val amberLight: Color,
    val ambd: Color,
    val violet: Color,
    val violetLight: Color,
    val vold: Color,
    val teal: Color,
    val tealLight: Color,
    val tead: Color,
    val indigo: Color,
    val indigoLight: Color,
    val indigoDark: Color,
    val cyan: Color,
    val cyanLight: Color,
    val cyand: Color,
    val cEm: Color,
    val cGold: Color,
    val goldDeep: Color,
    val cSky: Color,
    val cRose: Color,
    val cIndigo: Color,
    val selBg: Color,
    val roseBg: Color,
    val roseLine: Color,
    val shadowSm: Color,
    val shadowMd: Color,
    val shadowLg: Color,
    val glowEmerald: Color,
    val glowSky: Color,
    val glowRose: Color,
    val glowAmber: Color,
    val isDark: Boolean,
)

val LightColors = MmsColors(
    bg = LightBg,
    bodyBg = LightBg,
    bodyBgGradient = LightBgGradient,
    panel = LightPanel,
    panel2 = LightPanel2,
    panel3 = LightPanel3,
    head = LightHead,
    line = LightLine,
    line2 = LightLine2,
    tx = LightTx,
    txSec = LightTxSec,
    mut = LightMut,
    fnt = LightFnt,
    em = Emerald,
    emLight = EmeraldLight,
    emd = EmeraldDark,
    emdd = EmeraldDarker,
    sky = Sky,
    skyLight = SkyLight,
    skyd = SkyDark,
    rose = Rose,
    roseLight = RoseLight,
    rosd = RoseDark,
    amber = Amber,
    amberLight = AmberLight,
    ambd = AmberDark,
    violet = Violet,
    violetLight = VioletLight,
    vold = VioletDark,
    teal = Teal,
    tealLight = TealLight,
    tead = TealDark,
    indigo = Indigo,
    indigoLight = IndigoLight,
    indigoDark = IndigoDark,
    cyan = Cyan,
    cyanLight = CyanLight,
    cyand = CyanDark,
    cEm = Emerald,
    cGold = Amber,
    goldDeep = AmberDark,
    cSky = Sky,
    cRose = Rose,
    cIndigo = Indigo,
    selBg = Emerald.copy(0.08f),
    roseBg = Rose.copy(0.08f),
    roseLine = Rose.copy(0.3f),
    shadowSm = ShadowSm,
    shadowMd = ShadowMd,
    shadowLg = ShadowLg,
    glowEmerald = GlowEmerald,
    glowSky = GlowSky,
    glowRose = GlowRose,
    glowAmber = GlowAmber,
    isDark = false,
)

val LightColorsFixed = LightColors

val DarkColors = MmsColors(
    bg = DarkBg,
    bodyBg = DarkBg,
    bodyBgGradient = DarkBgGradient,
    panel = DarkPanel,
    panel2 = DarkPanel2,
    panel3 = DarkPanel3,
    head = DarkHead,
    line = DarkLine,
    line2 = DarkLine2,
    tx = DarkTx,
    txSec = DarkTxSec,
    mut = DarkMut,
    fnt = DarkFnt,
    em = Emerald,
    emLight = EmeraldLight,
    emd = EmeraldDark,
    emdd = EmeraldDarker,
    sky = Sky,
    skyLight = SkyLight,
    skyd = SkyDark,
    rose = Rose,
    roseLight = RoseLight,
    rosd = RoseDark,
    amber = Amber,
    amberLight = AmberLight,
    ambd = AmberDark,
    violet = Violet,
    violetLight = VioletLight,
    vold = VioletDark,
    teal = Teal,
    tealLight = TealLight,
    tead = TealDark,
    indigo = Indigo,
    indigoLight = IndigoLight,
    indigoDark = IndigoDark,
    cyan = Cyan,
    cyanLight = CyanLight,
    cyand = CyanDark,
    cEm = Emerald,
    cGold = Amber,
    goldDeep = AmberDark,
    cSky = Sky,
    cRose = Rose,
    cIndigo = Indigo,
    selBg = Emerald.copy(0.12f),
    roseBg = Rose.copy(0.12f),
    roseLine = Rose.copy(0.3f),
    shadowSm = ShadowSm,
    shadowMd = ShadowMd,
    shadowLg = ShadowLg,
    glowEmerald = GlowEmerald,
    glowSky = GlowSky,
    glowRose = GlowRose,
    glowAmber = GlowAmber,
    isDark = true,
)

@Immutable
data class ModuleTint(
    val sc: Color,      // Primary color
    val sb: Color,      // Background tint
    val st: Color,      // Text color
    val sl: Color,      // Border/Line color
)

object Tints {
    fun light(id: String): ModuleTint = when (id) {
        "em" -> ModuleTint(Emerald, Emerald.copy(0.08f), EmeraldDark, Emerald.copy(0.3f))
        "teal" -> ModuleTint(Teal, Teal.copy(0.08f), TealDark, Teal.copy(0.3f))
        "sky" -> ModuleTint(Sky, Sky.copy(0.08f), SkyDark, Sky.copy(0.3f))
        "cyan" -> ModuleTint(Cyan, Cyan.copy(0.08f), CyanDark, Cyan.copy(0.3f))
        "blue" -> ModuleTint(Indigo, Indigo.copy(0.08f), IndigoDark, Indigo.copy(0.3f))
        "vio" -> ModuleTint(Violet, Violet.copy(0.08f), VioletDark, Violet.copy(0.3f))
        "pink" -> ModuleTint(RoseLight, RoseLight.copy(0.08f), RoseDark, RoseLight.copy(0.3f))
        "rose" -> ModuleTint(Rose, Rose.copy(0.08f), RoseDark, Rose.copy(0.3f))
        "orange" -> ModuleTint(AmberLight, AmberLight.copy(0.08f), AmberDark, AmberLight.copy(0.3f))
        "gold" -> ModuleTint(Amber, Amber.copy(0.08f), AmberDark, Amber.copy(0.3f))
        "indigo" -> ModuleTint(Indigo, Indigo.copy(0.08f), IndigoDark, Indigo.copy(0.3f))
        "slate" -> ModuleTint(Color(0xFF66788F), Color(0xFF66788F).copy(0.08f), Color(0xFF46586E), Color(0xFF66788F).copy(0.3f))
        else -> ModuleTint(Emerald, Emerald.copy(0.08f), EmeraldDark, Emerald.copy(0.3f))
    }
    fun dark(id: String): ModuleTint = when (id) {
        "em" -> ModuleTint(EmeraldLight, Emerald.copy(0.15f), EmeraldLight, Emerald.copy(0.4f))
        "teal" -> ModuleTint(TealLight, Teal.copy(0.15f), TealLight, Teal.copy(0.4f))
        "sky" -> ModuleTint(SkyLight, Sky.copy(0.15f), SkyLight, Sky.copy(0.4f))
        "cyan" -> ModuleTint(CyanLight, Cyan.copy(0.15f), CyanLight, Cyan.copy(0.4f))
        "blue" -> ModuleTint(IndigoLight, Indigo.copy(0.15f), IndigoLight, Indigo.copy(0.4f))
        "vio" -> ModuleTint(VioletLight, Violet.copy(0.15f), VioletLight, Violet.copy(0.4f))
        "pink" -> ModuleTint(RoseLight, Rose.copy(0.15f), RoseLight, Rose.copy(0.4f))
        "rose" -> ModuleTint(RoseLight, Rose.copy(0.15f), RoseLight, Rose.copy(0.4f))
        "orange" -> ModuleTint(AmberLight, Amber.copy(0.15f), AmberLight, Amber.copy(0.4f))
        "gold" -> ModuleTint(AmberLight, Amber.copy(0.15f), AmberLight, Amber.copy(0.4f))
        "indigo" -> ModuleTint(IndigoLight, Indigo.copy(0.15f), IndigoLight, Indigo.copy(0.4f))
        "slate" -> ModuleTint(Color(0xFF93A7C4), Color(0xFF93A7C4).copy(0.15f), Color(0xFFC0CDE2), Color(0xFF93A7C4).copy(0.4f))
        else -> ModuleTint(EmeraldLight, Emerald.copy(0.15f), EmeraldLight, Emerald.copy(0.4f))
    }
    fun of(id: String, isDark: Boolean) = if (isDark) dark(id) else light(id)
}

val LocalMmsColors = staticCompositionLocalOf { LightColorsFixed }
val LocalTint = staticCompositionLocalOf { Tints.light("em") }
