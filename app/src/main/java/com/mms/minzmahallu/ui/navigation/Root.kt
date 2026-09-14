package com.mms.minzmahallu.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mms.minzmahallu.MmsApp
import com.mms.minzmahallu.data.model.AuthUser
import com.mms.minzmahallu.data.model.Dest
import com.mms.minzmahallu.data.model.ToastMsg
import com.mms.minzmahallu.i18n.I18n
import com.mms.minzmahallu.ui.components.*
import com.mms.minzmahallu.ui.screens.*
import com.mms.minzmahallu.ui.theme.*
import com.mms.minzmahallu.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MmsRoot(modifier: Modifier = Modifier) {
    val app = MmsApp.instance
    val repo = app.repo
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var splash by remember { mutableStateOf(true) }
    var user by remember { mutableStateOf(repo.auth.currentUser) }
    var needsSetup by remember { mutableStateOf(false) }
    var dest by remember { mutableStateOf(Dest.Dashboard) }
    var drawerOpen by remember { mutableStateOf(false) }
    val toasts = remember { mutableStateListOf<ToastMsg>() }
    val lang by I18n.lang.collectAsState()

    fun toast(msg: String, kind: ToastMsg.Kind = ToastMsg.Kind.Success) {
        toasts += ToastMsg(System.currentTimeMillis(), msg, kind)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            needsSetup = repo.auth.needsInitialSetup()
            val s = repo.settingsLoad()
            Format.currencySymbol = Format.str(s, "currency_symbol").ifBlank { "₹" }
            val theme = Format.str(s, "theme")
            withContext(Dispatchers.Main) {
                MmsThemeController.setDark(theme == "dark")
                val l = Format.str(s, "language")
                if (l in listOf("en", "ml")) I18n.setLang(ctx, l)
            }
        }
        kotlinx.coroutines.delay(1600)
        splash = false
    }

    Box(modifier.background(C().bodyBg)) {
        when {
            splash -> SplashScreen()
            user == null -> LoginScreen(
                needsSetup = needsSetup,
                onLogin = { u, p ->
                    scope.launch {
                        try {
                            val a = withContext(Dispatchers.IO) { repo.auth.login(u, p) }
                            user = a
                            toast(I18n.t("login_button") + " ✓")
                        } catch (e: Exception) {
                            toast(e.message ?: "Login failed", ToastMsg.Kind.Error)
                        }
                    }
                },
                onSetup = { u, n, p ->
                    scope.launch {
                        try {
                            val a = withContext(Dispatchers.IO) { repo.auth.createInitialAdministrator(u, n, p) }
                            user = a
                            needsSetup = false
                            toast("Administrator created")
                        } catch (e: Exception) {
                            toast(e.message ?: "Setup failed", ToastMsg.Kind.Error)
                        }
                    }
                }
            )
            else -> AppShell(
                user = user!!,
                dest = dest,
                drawerOpen = drawerOpen,
                onDest = { dest = it; drawerOpen = false },
                onToggleDrawer = { drawerOpen = !drawerOpen },
                onLogout = {
                    repo.auth.logout()
                    user = null
                    dest = Dest.Dashboard
                },
                onToggleLang = {
                    I18n.toggle(ctx)
                    scope.launch(Dispatchers.IO) {
                        val s = repo.settingsLoad().toMutableMap()
                        s["language"] = I18n.lang.value
                        repo.settingsSave(s)
                    }
                },
                onToggleTheme = {
                    MmsThemeController.toggle()
                    scope.launch(Dispatchers.IO) {
                        val s = repo.settingsLoad().toMutableMap()
                        s["theme"] = if (MmsThemeController.dark.value) "dark" else "light"
                        repo.settingsSave(s)
                    }
                },
                toast = ::toast,
            )
        }
        ToastHost(toasts.toList()) { id -> toasts.removeAll { it.id == id } }
    }
}

@Composable
private fun AppShell(
    user: AuthUser,
    dest: Dest,
    drawerOpen: Boolean,
    onDest: (Dest) -> Unit,
    onToggleDrawer: () -> Unit,
    onLogout: () -> Unit,
    onToggleLang: () -> Unit,
    onToggleTheme: () -> Unit,
    toast: (String, ToastMsg.Kind) -> Unit,
) {
    val c = C()
    val lang by I18n.lang.collectAsState()
    val tint = Tints.of(dest.tint, c.isDark)
    CompositionLocalProvider(LocalTint provides tint) {
        Column(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(
                c.cSky.copy(0.04f),
                c.bodyBg,
                c.em.copy(0.04f)
            ))
        )) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(c.panel)
                    .border(1.dp, c.line)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MmsIconButton(onClick = onToggleDrawer) {
                    androidx.compose.foundation.text.BasicText("☰", style = TextStyle(color = c.mut, fontSize = 20.sp))
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.em), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.text.BasicText("M", style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    androidx.compose.foundation.text.BasicText(I18n.t(dest.titleKey), style = MmsType.headline.copy(color = c.tx, fontSize = 16.sp))
                    androidx.compose.foundation.text.BasicText(I18n.t("app_name"), style = MmsType.caption.copy(color = c.fnt, fontSize = 9.sp, letterSpacing = 0.8.sp))
                }
                // lang toggle
                Row(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(c.panel2)
                        .border(1.5.dp, c.line, RoundedCornerShape(99.dp))
                        .padding(3.dp)
                ) {
                    listOf("en" to "EN", "ml" to "മല").forEach { (code, label) ->
                        val on = lang == code
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (on) c.em else Color.Transparent)
                                .mmsClickable { if (!on) onToggleLang() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            androidx.compose.foundation.text.BasicText(label, style = TextStyle(color = if (on) Color.White else c.mut, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                MmsIconButton(onClick = onToggleTheme) {
                    androidx.compose.foundation.text.BasicText(if (c.isDark) "☀" else "☾", style = TextStyle(fontSize = 16.sp, color = c.mut))
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(c.em, c.emdd))),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicText(user.initials, style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                }
            }

            Box(Modifier.fillMaxSize()) {
                // Content
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = if (drawerOpen) 0.dp else 0.dp)
                        .background(
                            Brush.radialGradient(listOf(c.cEm.copy(0.03f), Color.Transparent))
                        )
                ) {
                    AnimatedContent(
                        targetState = dest,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 12 }) togetherWith
                                fadeOut(tween(160))
                        },
                        label = "page"
                    ) { d ->
                        ModuleHost(d, toast = { m, k -> toast(m, k) }, onNavigate = onDest)
                    }
                }

                // Drawer overlay
                AnimatedVisibility(
                    visible = drawerOpen,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(140))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0D1521).copy(0.35f))
                            .mmsClickable(onClick = onToggleDrawer)
                    )
                }
                AnimatedVisibility(
                    visible = drawerOpen,
                    enter = slideInHorizontally(tween(260)) { -it } + fadeIn(),
                    exit = slideOutHorizontally(tween(200)) { -it } + fadeOut()
                ) {
                    SideDrawer(
                        user = user,
                        dest = dest,
                        onDest = onDest,
                        onLogout = onLogout,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(280.dp)
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SideDrawer(
    user: AuthUser,
    dest: Dest,
    onDest: (Dest) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = C()
    val lang by I18n.lang.collectAsState()
    val sectionMl = mapOf("Management" to "മാനേജ്മെന്റ്", "Finance" to "സാമ്പത്തികം", "Registers" to "രജിസ്റ്ററുകൾ", "System" to "സിസ്റ്റം")
    Column(
        modifier
            .shadow(20.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(c.panel)
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp)) {
            var lastSec: String? = null
            Dest.all.forEach { d ->
                val sec = d.section
                if (sec != null && sec != lastSec) {
                    lastSec = sec
                    val label = if (lang == "ml") sectionMl[sec] ?: sec else sec
                    Row(Modifier.padding(top = 14.dp, bottom = 6.dp, start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.text.BasicText(
                            label.uppercase(),
                            style = MmsType.overline.copy(color = c.fnt, fontSize = 9.5.sp, letterSpacing = 1.6.sp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(c.line))
                    }
                }
                val on = dest == d
                val tint = Tints.of(d.tint, c.isDark)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (on) Brush.horizontalGradient(listOf(tint.sc.copy(0.14f), tint.sc.copy(0.05f)))
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .then(if (on) Modifier.border(1.5.dp, tint.sl, RoundedCornerShape(12.dp)) else Modifier)
                        .mmsClickable { onDest(d) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (on) tint.sc else tint.sc.copy(0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            d.route.take(1).uppercase(),
                            style = TextStyle(color = if (on) Color.White else tint.sc, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    androidx.compose.foundation.text.BasicText(
                        I18n.t(d.titleKey),
                        style = MmsType.bodySm.copy(
                            color = if (on) tint.st else c.mut,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.5.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (on) Box(Modifier.size(5.dp).clip(CircleShape).background(tint.sc))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.em, c.emdd))),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.text.BasicText(user.initials, style = TextStyle(color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                androidx.compose.foundation.text.BasicText(user.fullName, style = MmsType.bodySm.copy(color = c.tx, fontWeight = FontWeight.SemiBold))
                androidx.compose.foundation.text.BasicText(user.role, style = MmsType.caption.copy(color = c.fnt))
            }
            MmsIconButton(onClick = onLogout) {
                androidx.compose.foundation.text.BasicText("⎋", style = TextStyle(color = c.cRose, fontSize = 16.sp))
            }
        }
    }
}

@Composable
private fun ModuleHost(dest: Dest, toast: (String, ToastMsg.Kind) -> Unit, onNavigate: (Dest) -> Unit) {
    when (dest) {
        Dest.Dashboard -> DashboardScreen(toast = toast, onNavigate = onNavigate)
        Dest.Families -> FamiliesScreen(toast = toast)
        Dest.Members -> MembersScreen(toast = toast)
        Dest.Staff -> StaffScreen(toast = toast)
        Dest.Committee -> CommitteeScreen(toast = toast)
        Dest.Subscriptions -> SubscriptionsScreen(toast = toast)
        Dest.Donations -> DonationsScreen(toast = toast)
        Dest.WhatsApp -> WhatsAppScreen(toast = toast)
        Dest.Accounting -> AccountingScreen(toast = toast)
        Dest.Assets -> AssetsScreen(toast = toast)
        Dest.Marriages -> MarriagesScreen(toast = toast)
        Dest.Deaths -> DeathsScreen(toast = toast)
        Dest.Welfare -> WelfareScreen(toast = toast)
        Dest.Certificates -> CertificatesScreen(toast = toast)
        Dest.Tokens -> TokensScreen(toast = toast)
        Dest.Reports -> ReportsScreen(toast = toast)
        Dest.Settings -> SettingsScreen(toast = toast)
        Dest.Users -> UsersScreen(toast = toast)
        Dest.Audit -> AuditScreen(toast = toast)
        Dest.Backup -> BackupScreen(toast = toast)
    }
}
