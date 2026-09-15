package com.mms.minzmahallu.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mms.minzmahallu.i18n.I18n
import com.mms.minzmahallu.ui.components.*
import com.mms.minzmahallu.ui.theme.*

@Composable
fun SplashScreen() {
    val progress by rememberInfiniteTransition(label = "p").animateFloat(
        0.15f, 0.92f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "bar"
    )
    val pulse by rememberInfiniteTransition(label = "h").animateFloat(
        0.95f, 1.05f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF12A396), Color(0xFF0D9488), Color(0xFF0A5F5A)))
        ),
        contentAlignment = Alignment.Center
    ) {
        // soft glows
        Box(Modifier.size(320.dp).offset((-40).dp, (-120).dp).scale(pulse).clip(CircleShape).background(Color(0xFF2DD4BF).copy(0.25f)))
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .shadow(40.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF12A396), Color(0xFF0A5F5A))))
                .border(1.dp, Color(0xFF2DD4BF).copy(0.3f), RoundedCornerShape(24.dp))
                .padding(48.dp, 52.dp, 48.dp, 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(96.dp).scale(pulse), contentAlignment = Alignment.Center) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(0.12f)))
                androidx.compose.foundation.text.BasicText("☪", style = TextStyle(fontSize = 42.sp, color = Color.White))
            }
            Spacer(Modifier.height(24.dp))
            androidx.compose.foundation.text.BasicText(
                "MINZ MAHALLU",
                style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.text.BasicText(
                I18n.t("app_subtitle").ifBlank { "Management System" },
                style = TextStyle(color = Color.White.copy(0.75f), fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(0.18f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(
                    Brush.horizontalGradient(listOf(Color(0xFF14B8A6), Color(0xFF7DE3C8)))
                ))
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.text.BasicText(
                "Loading community records…",
                style = TextStyle(color = Color.White.copy(0.65f), fontSize = 11.sp)
            )
            Spacer(Modifier.height(28.dp))
            androidx.compose.foundation.text.BasicText(
                "v2.0.0  ·  Android",
                style = TextStyle(color = Color.White.copy(0.5f), fontSize = 10.sp, letterSpacing = 1.sp)
            )
        }
    }
}

@Composable
fun LoginScreen(
    needsSetup: Boolean,
    onLogin: (String, String) -> Unit,
    onSetup: (String, String, String) -> Unit,
) {
    val c = C()
    val ctx = LocalContext.current
    val lang by I18n.lang.collectAsState()
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        // decorative left wash for tablet; full stack on phone
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // language toggle
            Row(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.panel)
                    .border(1.dp, c.line, RoundedCornerShape(99.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("en" to "English", "ml" to "മലയാളം").forEach { (code, label) ->
                    val on = lang == code
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (on) Brush.horizontalGradient(listOf(c.emLight, c.em)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                            .mmsClickable { if (!on) I18n.setLang(ctx, code) }
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            label,
                            style = TextStyle(
                                color = if (on) Color.White else c.mut,
                                fontSize = 12.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0D9488), Color(0xFF0A5F5A))))
                    .padding(28.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(0.15f)), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.text.BasicText("M", style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            androidx.compose.foundation.text.BasicText(I18n.t("app_name"), style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
                            androidx.compose.foundation.text.BasicText("OFFLINE · PRIVATE · SECURE", style = TextStyle(color = Color.White.copy(0.7f), fontSize = 10.sp, letterSpacing = 1.sp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    androidx.compose.foundation.text.BasicText(
                        if (needsSetup) "Create your administrator" else "Welcome back",
                        style = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.text.BasicText(
                        "Mahallu registers, subscriptions, welfare & more — fully offline.",
                        style = TextStyle(color = Color.White.copy(0.75f), fontSize = 13.sp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.panel)
                    .border(1.dp, c.line, RoundedCornerShape(18.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                androidx.compose.foundation.text.BasicText(
                    if (needsSetup) "Initial Setup" else I18n.t("login_title"),
                    style = MmsType.title.copy(color = c.tx, fontSize = 22.sp)
                )
                androidx.compose.foundation.text.BasicText(
                    if (needsSetup) "No default password. Create the first Administrator account."
                    else I18n.t("login_form_sub").ifBlank { "Sign in to continue" },
                    style = MmsType.bodySm.copy(color = c.mut)
                )
                MmsInput(username, { username = it }, label = I18n.t("login_username"), placeholder = "admin")
                if (needsSetup) {
                    MmsInput(fullName, { fullName = it }, label = "Full name", placeholder = "Your name")
                }
                MmsInput(password, { password = it }, label = I18n.t("login_password"), password = true, placeholder = "••••••••")
                if (needsSetup) {
                    MmsInput(confirm, { confirm = it }, label = "Confirm password", password = true)
                    androidx.compose.foundation.text.BasicText(
                        "Min 8 chars with upper, lower, digit & special character.",
                        style = MmsType.caption.copy(color = c.fnt)
                    )
                    if (confirm.isNotEmpty() && password != confirm) {
                        androidx.compose.foundation.text.BasicText(
                            "Passwords do not match.",
                            style = MmsType.caption.copy(color = c.cRose, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                MmsButton(
                    if (needsSetup) "Create & Sign in" else I18n.t("login_button"),
                    onClick = {
                        if (needsSetup) {
                            if (password != confirm) return@MmsButton
                            onSetup(username, fullName, password)
                        } else onLogin(username, password)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = username.isNotBlank() && password.isNotBlank() && (!needsSetup || (fullName.isNotBlank() && password == confirm))
                )
            }
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.text.BasicText("Minz Mahallu · v2.0.0", style = MmsType.caption.copy(color = c.fnt))
        }
    }
}
