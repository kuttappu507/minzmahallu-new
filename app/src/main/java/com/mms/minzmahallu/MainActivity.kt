package com.mms.minzmahallu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mms.minzmahallu.ui.navigation.MmsRoot
import com.mms.minzmahallu.ui.theme.MmsTheme
import com.mms.minzmahallu.ui.theme.MmsThemeController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color(0xFF0D9488).toArgb()),
            navigationBarStyle = SystemBarStyle.light(
                Color(0xFFF0F3F6).toArgb(),
                Color(0xFF0A0E14).toArgb()
            )
        )
        setContent {
            val dark by MmsThemeController.dark.collectAsState()
            MmsTheme(dark = dark) {
                MmsRoot(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
