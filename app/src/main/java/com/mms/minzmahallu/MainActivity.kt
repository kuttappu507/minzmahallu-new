package com.mms.minzmahallu

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mms.minzmahallu.ui.navigation.MmsRoot
import com.mms.minzmahallu.ui.theme.MmsTheme
import com.mms.minzmahallu.ui.theme.MmsThemeController
import com.mms.minzmahallu.ui.components.MmsCard
import com.mms.minzmahallu.ui.components.C
import com.mms.minzmahallu.ui.theme.MmsType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // enableEdgeToEdge can throw on some OEMs / API levels – never let it crash launch
        try {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color(0xFF0D9488).toArgb()),
                navigationBarStyle = SystemBarStyle.light(
                    Color(0xFFF0F3F6).toArgb(),
                    Color(0xFF0A0E14).toArgb()
                )
            )
        } catch (e: Exception) {
            Log.w("MainActivity", "enableEdgeToEdge failed", e)
            try { enableEdgeToEdge() } catch (_: Exception) {}
        }

        // If Application failed to init DB, show a dedicated error screen instead of crashing via lateinit
        val app = try { MmsApp.instance } catch (e: Exception) { null }
        if (app?.initError != null) {
            Log.e("MainActivity", "App initError present", app.initError)
            setContent {
                val dark by MmsThemeController.dark.collectAsState()
                MmsTheme(dark = dark) {
                    DbErrorScreen(app.initError)
                }
            }
            return
        }

        // Normal path – but still guard setContent against any uncaught composable exception
        try {
            setContent {
                val dark by MmsThemeController.dark.collectAsState()
                MmsTheme(dark = dark) {
                    MmsRoot(modifier = Modifier.fillMaxSize())
                }
            }
        } catch (e: Throwable) {
            Log.e("MainActivity", "setContent crashed", e)
            setContent {
                val dark by MmsThemeController.dark.collectAsState()
                MmsTheme(dark = dark) {
                    DbErrorScreen(e)
                }
            }
        }
    }
}

@Composable
private fun DbErrorScreen(error: Throwable?) {
    val c = C()
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF0D9488), Color(0xFF0A5F5A)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(18.dp))
                .background(c.panel)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.foundation.text.BasicText(
                "Unable to start",
                style = MmsType.title.copy(color = c.tx, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            )
            androidx.compose.foundation.text.BasicText(
                "The app database could not be opened. Try clearing the app's storage or reinstalling. If the problem persists, contact support.",
                style = MmsType.bodySm.copy(color = c.mut)
            )
            if (error != null) {
                androidx.compose.foundation.text.BasicText(
                    (error.message ?: error::class.java.simpleName).take(400),
                    style = MmsType.caption.copy(color = c.cRose)
                )
                androidx.compose.foundation.text.BasicText(
                    Log.getStackTraceString(error).take(1200),
                    style = MmsType.caption.copy(color = c.fnt, fontSize = 10.sp)
                )
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.text.BasicText(
                "Tip: Android Settings → Apps → Minz Mahallu → Storage → Clear Data, then reopen.",
                style = MmsType.caption.copy(color = c.fnt)
            )
        }
    }
}
