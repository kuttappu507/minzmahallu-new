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
    val repo = try { app.repo } catch (e: Throwable) { null }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var splash by remember { mutableStateOf(true) }
    var initError by remember { mutableStateOf<Throwable?>(app.initError) }
    var user by remember { mutableStateOf(try { repo?.auth?.currentUser } catch (_: Exception) { null }) }
    var needsSetup by remember { mutableStateOf(false) }
    var dest by remember { mutableStateOf(Dest.Dashboard) }
    var drawerOpen by remember { mutableStateOf(false) }
    val toasts = remember { mutableStateListOf<ToastMsg>() }
    val lang by I18n.lang.collectAsState()

    fun toast(msg: String, kind: ToastMsg.Kind = ToastMsg.Kind.Success) {
        toasts += ToastMsg(System.currentTimeMillis(), msg, kind)
    }

    LaunchedEffect(Unit) {
        // Guard against repo not ready – show error instead of crashing
        if (repo == null) {
            initError = app.initError ?: IllegalStateException("Database not initialised")
            kotlinx.coroutines.delay(600)
            splash = false
            return@LaunchedEffect
        }
        try {
            val setup: Boolean
            val s: Map<String, Any?>
            withContext(Dispatchers.IO) {
                setup = try { repo.auth.needsInitialSetup() } catch (e: Exception) {
                    android.util.Log.e("MmsRoot", "needsInitialSetup failed", e)
                    false
                }
                s = try { repo.settingsLoad() } catch (e: Exception) {
                    android.util.Log.e("MmsRoot", "settingsLoad failed", e)
                    emptyMap()
                }
                Format.currencySymbol = Format.str(s, "currency_symbol").ifBlank { "₹" }
            }
            needsSetup = setup
            val theme = Format.str(s, "theme")
            // theme / lang must run on Main
            MmsThemeController.setDark(theme == "dark")
            val l = Format.str(s, "language")
            if (l in listOf("en", "ml")) I18n.setLang(ctx, l)
        } catch (e: Exception) {
            android.util.Log.e("MmsRoot", "init failed", e)
            initError = e
            toast(e.message ?: "Init failed", ToastMsg.Kind.Error)
        }
        kotlinx.coroutines.delay(1600)
        splash = false
    }

    val c = C()
    // If the repository is unavailable (DB failed to init), show a non-crash error
    // screen instead of Login loops. This early return is also what makes every
    // `repo` usage below compile: after it, Kotlin smart-casts the local `repo`
    // val to a non-null MmsRepository — including inside the lambdas passed to
    // LoginScreen and AppShell (a captured val can't be re-assigned, so the
    // smart cast stays valid there).
    if (repo == null) {
        Box(modifier.background(c.bodyBg)) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.text.BasicText(
                    "Database error",
                    style = MmsType.title.copy(color = c.cRose, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicText(
                    (initError?.message ?: "Failed to open database").take(300),
                    style = MmsType.bodySm.copy(color = c.mut)
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.text.BasicText(
                    "Clear app data and reopen. Settings → Apps → Minz Mahallu → Storage → Clear Data.",
                    style = MmsType.caption.copy(color = c.fnt)
                )
            }
            ToastHost(toasts.toList()) { id -> toasts.removeAll { it.id == id } }
        }
        return
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
            // Modern Top Bar with Glass Effect
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                c.panel.copy(alpha = 0.95f),
                                c.panel.copy(alpha = 0.92f)
                            )
                        )
                    )
                    .shadow(8.dp, ambientColor = c.shadowMd, spotColor = c.shadowMd)
                    .border(1.dp, c.line.copy(alpha = 0.3f))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Menu Button
                MmsIconButton(onClick = onToggleDrawer) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.panel2)
                            .border(1.dp, c.line, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText("⋮", style = TextStyle(color = c.tx, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                    }
                }
                Spacer(Modifier.width(14.dp))
                
                // App Logo with Gradient
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(c.emLight, c.em)))
                        .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = c.glowEmerald),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicText("M", style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp))
                }
                Spacer(Modifier.width(14.dp))
                
                // Title Section
                Column(Modifier.weight(1f)) {
                    androidx.compose.foundation.text.BasicText(
                        I18n.t(dest.titleKey),
                        style = MmsType.title.copy(color = c.tx, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    )
                    androidx.compose.foundation.text.BasicText(
                        I18n.t("app_name"),
                        style = MmsType.caption.copy(color = c.mut, fontSize = 10.sp, letterSpacing = 1.sp)
                    )
                }
                
                // Language Toggle - Pill Design
                Row(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(c.panel2)
                        .border(1.dp, c.line, RoundedCornerShape(99.dp))
                        .padding(4.dp)
                ) {
                    listOf("en" to "EN", "ml" to "മല").forEach { (code, label) ->
                        val on = lang == code
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (on) Brush.horizontalGradient(listOf(c.emLight, c.em)) else Color.Transparent)
                                .mmsClickable { if (!on) onToggleLang() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                label,
                                style = TextStyle(
                                    color = if (on) Color.White else c.mut,
                                    fontSize = 11.sp,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                
                // Theme Toggle
                MmsIconButton(onClick = onToggleTheme) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.panel2)
                            .border(1.dp, c.line, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText(if (c.isDark) "☀" else "☾", style = TextStyle(fontSize = 18.sp, color = c.tx))
                    }
                }
                Spacer(Modifier.width(10.dp))
                
                // User Avatar with Gradient Ring
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(c.skyLight, c.sky)))
                        .border(2.dp, c.panel, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicText(
                        user.initials,
                        style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
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
                androidx.compose.animation.AnimatedVisibility(
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
                androidx.compose.animation.AnimatedVisibility(
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
    
    // Modern Drawer with Gradient Header
    Column(
        modifier
            .shadow(24.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(c.panel.copy(alpha = 0.98f), c.panel)
                )
            )
            .border(1.5.dp, c.line.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
    ) {
        // User Profile Header
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(c.em.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(c.skyLight, c.sky)))
                        .border(3.dp, c.panel, CircleShape)
                        .shadow(8.dp, CircleShape, ambientColor = c.glowSky),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicText(
                        user.initials,
                        style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.text.BasicText(
                    user.fullName,
                    style = MmsType.title.copy(color = c.tx, fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(c.panel2)
                        .border(1.dp, c.line, RoundedCornerShape(99.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    androidx.compose.foundation.text.BasicText(
                        user.role,
                        style = MmsType.caption.copy(color = c.mut, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
        
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.5f)))
        
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
            var lastSec: String? = null
            Dest.all.forEach { d ->
                val sec = d.section
                if (sec != null && sec != lastSec) {
                    lastSec = sec
                    val label = if (lang == "ml") sectionMl[sec] ?: sec else sec
                    Row(Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(c.em))
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicText(
                            label.uppercase(),
                            style = MmsType.overline.copy(color = c.tx, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(c.line.copy(alpha = 0.5f)))
                    }
                }
                val on = dest == d
                val tint = Tints.of(d.tint, c.isDark)
                
                // Modern Nav Item with Gradient Active State
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (on) Brush.horizontalGradient(listOf(tint.sb, tint.sb.copy(alpha = 0.5f)))
                            else Color.Transparent
                        )
                        .then(if (on) Modifier.border(1.5.dp, tint.sl.copy(alpha = 0.6f), RoundedCornerShape(14.dp)) else Modifier)
                        .mmsClickable { onDest(d) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (on) Brush.linearGradient(listOf(tint.sc, tint.sc.copy(alpha = 0.8f)))
                                else tint.sb
                            )
                            .then(if (on) Modifier.shadow(4.dp, RoundedCornerShape(11.dp), ambientColor = tint.sc.copy(0.3f)) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            d.route.take(1).uppercase(),
                            style = TextStyle(
                                color = if (on) Color.White else tint.sc,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(Modifier.width(13.dp))
                    androidx.compose.foundation.text.BasicText(
                        I18n.t(d.titleKey),
                        style = MmsType.body.copy(
                            color = if (on) tint.st else c.mut,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (on) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(tint.sc, Color.White)))
                        )
                    }
                }
            }
        }
        
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.5f)))
        
        // Logout Section
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MmsButton(
                "Logout",
                onLogout,
                modifier = Modifier.weight(1f),
                small = true,
                danger = true,
                icon = "⎋"
            )
        }
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
