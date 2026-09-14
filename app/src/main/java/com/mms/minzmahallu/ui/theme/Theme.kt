package com.mms.minzmahallu.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object MmsThemeController {
    private val _dark = MutableStateFlow(false)
    val dark = _dark.asStateFlow()
    fun setDark(v: Boolean) { _dark.value = v }
    fun toggle() { _dark.value = !_dark.value }
}

@Composable
fun MmsTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColorsFixed
    CompositionLocalProvider(
        LocalMmsColors provides colors,
        LocalTint provides Tints.of("em", dark),
    ) {
        content()
    }
}

object MmsType {
    val display = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.3).sp)
    val title = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.2).sp)
    val headline = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    val bodySm = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp)
    val caption = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    val overline = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.6.sp)
    val stat = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp)
    val code = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
}

val MmsColors.shim: Color get() = this.em.copy(alpha = 0.12f)

@Composable
fun Modifier.mmsClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        enabled = enabled,
        indication = null,
        interactionSource = interaction,
        onClick = onClick
    )
}
