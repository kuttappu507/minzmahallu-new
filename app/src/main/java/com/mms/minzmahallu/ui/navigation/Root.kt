package com.mms.minzmahallu.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
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
    var navArg by remember { mutableStateOf("") }
    val toasts = remember { mutableStateListOf<ToastMsg>() }
    val lang by I18n.lang.collectAsState()

    fun toast(msg: String, kind: ToastMsg.Kind = ToastMsg.Kind.Success) {
        toasts += ToastMsg(System.currentTimeMillis(), msg, kind)
    }

    LaunchedEffect(Unit) {
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
                            toast("Administrator created ✓")
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
                navArg = navArg,
                onDest = { d, arg ->
                    dest = d
                    navArg = arg
                    drawerOpen = false
                },
                onToggleDrawer = { drawerOpen = !drawerOpen },
                onLogout = {
                    repo.auth.logout()
                    user = null
                    dest = Dest.Dashboard
                    navArg = ""
                },
                onToggleLang = {
                    I18n.toggle(ctx)
                    scope.launch(Dispatchers.IO) {
                        try {
                            val s = repo.settingsLoad().toMutableMap()
                            s["language"] = I18n.lang.value
                            repo.settingsSave(s)
                        } catch (_: Exception) { }
                    }
                },
                onToggleTheme = {
                    MmsThemeController.toggle()
                    scope.launch(Dispatchers.IO) {
                        try {
                            val s = repo.settingsLoad().toMutableMap()
                            s["theme"] = if (MmsThemeController.dark.value) "dark" else "light"
                            repo.settingsSave(s)
                        } catch (_: Exception) { }
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
    navArg: String,
    onDest: (Dest, String) -> Unit,
    onToggleDrawer: () -> Unit,
    onLogout: () -> Unit,
    onToggleLang: () -> Unit,
    onToggleTheme: () -> Unit,
    toast: (String, ToastMsg.Kind) -> Unit,
) {
    val c = C()
    val lang by I18n.lang.collectAsState()
    val tint = Tints.of(dest.tint, c.isDark)
    var searchOpen by remember { mutableStateOf(false) }
    var alertsOpen by remember { mutableStateOf(false) }
    var meOpen by remember { mutableStateOf(false) }
    var alerts by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    val scope = rememberCoroutineScope()

    fun go(d: Dest) = onDest(d, "")
    fun reloadAlerts() = scope.launch {
        try {
            alerts = withContext(Dispatchers.IO) { MmsApp.instance.repo.alerts() }
        } catch (_: Exception) { }
    }
    LaunchedEffect(dest) { reloadAlerts() }

    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = drawerOpen && !searchOpen) { onToggleDrawer() }

    CompositionLocalProvider(LocalTint provides tint) {
        Box(Modifier.fillMaxSize().background(c.bodyBg)) {
            Column(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            c.cSky.copy(0.04f),
                            c.bodyBg,
                            c.em.copy(0.04f)
                        )
                    )
                )
            ) {
                // ---- top bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .background(c.panel.copy(alpha = 0.96f))
                        .border(1.dp, c.line.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TopIconBtn(onToggleDrawer) {
                        androidx.compose.foundation.text.BasicText(
                            "☰",
                            style = TextStyle(color = c.tx, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(c.emLight, c.em))),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            "M",
                            style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        androidx.compose.foundation.text.BasicText(
                            I18n.t(dest.titleKey),
                            style = MmsType.title.copy(color = c.tx, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        androidx.compose.foundation.text.BasicText(
                            I18n.t("app_name"),
                            style = MmsType.caption.copy(color = c.mut, fontSize = 9.sp, letterSpacing = 1.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    TopIconBtn({ searchOpen = true }) {
                        androidx.compose.foundation.text.BasicText(G.SEARCH, style = TextStyle(color = c.tx, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    }
                    // alerts bell with badge
                    Box {
                        TopIconBtn({ reloadAlerts(); alertsOpen = true }) {
                            androidx.compose.foundation.text.BasicText(
                                G.DOT,
                                style = TextStyle(
                                    color = if (alerts.isNotEmpty()) c.amber else c.fnt,
                                    fontSize = 16.sp
                                )
                            )
                        }
                        if (alerts.isNotEmpty()) {
                            Box(
                                Modifier.align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(c.cRose)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                androidx.compose.foundation.text.BasicText(
                                    alerts.size.toString(),
                                    style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                    // compact language toggle
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.panel2)
                            .border(1.dp, c.line, RoundedCornerShape(99.dp))
                            .mmsClickable(onClick = onToggleLang)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            if (lang == "ml") "മല" else "EN",
                            style = TextStyle(color = c.emd, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    TopIconBtn(onToggleTheme) {
                        androidx.compose.foundation.text.BasicText(
                            if (c.isDark) "☀" else "☾",
                            style = TextStyle(fontSize = 16.sp, color = c.tx)
                        )
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(c.skyLight, c.sky)))
                            .border(2.dp, c.panel, CircleShape)
                            .mmsClickable { meOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            user.initials,
                            style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // ---- content
                Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                    AnimatedContent(
                        targetState = dest,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 12 }) togetherWith
                                fadeOut(tween(160))
                        },
                        label = "page"
                    ) { d ->
                        ModuleHost(d, navArg, toast = { m, k -> toast(m, k) }, onNavigate = { go(it) })
                    }

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
                            onDest = { go(it) },
                            onLogout = onLogout,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(280.dp)
                                .statusBarsPadding()
                                .padding(12.dp)
                        )
                    }
                }
            }

            // ---- global search overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = searchOpen,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 6 },
                exit = fadeOut(tween(150))
            ) {
                SearchOverlay(
                    onClose = { searchOpen = false },
                    onPick = { d, arg ->
                        searchOpen = false
                        onDest(d, arg)
                    }
                )
            }
        }

        // ---- alerts dialog
        if (alertsOpen) {
            MmsDialog("Attention needed", onDismiss = { alertsOpen = false }, compact = true) {
                if (alerts.isEmpty()) {
                    InfoBanner("All clear — no overdue dues or pending requests.", "success")
                } else {
                    alerts.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(
                                    Format.str(a, "message"),
                                    style = MmsType.bodySm.copy(color = c.tx, fontWeight = FontWeight.Medium)
                                )
                            }
                            MmsButton("View", {
                                alertsOpen = false
                                go(if (Format.str(a, "type") == "welfare") Dest.Welfare else Dest.Subscriptions)
                            }, small = true, primary = false)
                        }
                    }
                }
            }
        }

        // ---- user menu
        if (meOpen) {
            MmsDialog("Signed in", onDismiss = { meOpen = false }, compact = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TintTile(user.initials.take(1), Tints.of("sky", c.isDark), 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        androidx.compose.foundation.text.BasicText(
                            user.fullName,
                            style = MmsType.headline.copy(color = c.tx, fontWeight = FontWeight.Bold)
                        )
                        androidx.compose.foundation.text.BasicText(
                            "@${user.username} · ${user.role}",
                            style = MmsType.caption.copy(color = c.mut)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                MmsButton("My settings & password", { meOpen = false; go(Dest.Settings) }, small = true, primary = false, modifier = Modifier.fillMaxWidth())
                MmsButton("Logout", { meOpen = false; onLogout() }, small = true, danger = true, ghost = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TopIconBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = C()
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(c.panel2)
            .border(1.dp, c.line, RoundedCornerShape(11.dp))
            .mmsClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun SearchOverlay(onClose: () -> Unit, onPick: (Dest, String) -> Unit) {
    val c = C()
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(q) {
        if (q.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        busy = true
        try {
            results = withContext(Dispatchers.IO) { MmsApp.instance.repo.globalSearch(q) }
        } catch (_: Exception) { }
        busy = false
    }

    Box(
        Modifier.fillMaxSize().background(c.bodyBg).statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TopIconBtn(onClose) {
                    androidx.compose.foundation.text.BasicText(G.LEFT, style = TextStyle(color = c.tx, fontSize = 17.sp, fontWeight = FontWeight.Bold))
                }
                SearchField(q, { q = it }, Modifier.weight(1f), placeholder = "Search families, members, receipts…")
            }
            Spacer(Modifier.height(12.dp))
            if (busy) {
                LoadingList(3)
            } else if (q.length >= 2 && results.isEmpty()) {
                EmptyState("No matches", "Try a name, code, phone or receipt number")
            } else if (results.isNotEmpty()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { r ->
                        val kind = Format.str(r, "kind")
                        val (d, tintId) = when (kind) {
                            "family" -> Dest.Families to "em"
                            "member" -> Dest.Members to "teal"
                            else -> Dest.Donations to "pink"
                        }
                        MmsCard(onClick = { onPick(d, Format.str(r, "code")) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TintTile(
                                    Format.str(r, "title").ifBlank { "?" }.take(1),
                                    Tints.of(tintId, c.isDark), 38.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    CellText(Format.str(r, "title"), strong = true, sub = Format.str(r, "code"))
                                }
                                StatusPill(kind.replaceFirstChar { it.uppercase() }, "default")
                            }
                        }
                    }
                }
            } else {
                EmptyState("Global search", "Type at least 2 characters to search everything")
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
    val sectionMl = mapOf(
        "Management" to "മാനേജ്മെന്റ്",
        "Finance" to "സാമ്പത്തികം",
        "Registers" to "രജിസ്റ്ററുകൾ",
        "System" to "സിസ്റ്റം"
    )

    Column(
        modifier
            .shadow(24.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(c.panel.copy(alpha = 0.98f), c.panel)))
            .border(1.5.dp, c.line.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(c.em.copy(alpha = 0.12f), Color.Transparent)))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(c.skyLight, c.sky)))
                        .border(3.dp, c.panel, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicText(
                        user.initials,
                        style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.text.BasicText(
                    user.fullName,
                    style = MmsType.title.copy(color = c.tx, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
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

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            var lastSec: String? = null
            Dest.all.forEach { d ->
                val sec = d.section
                if (sec != null && sec != lastSec) {
                    lastSec = sec
                    val label = if (lang == "ml") sectionMl[sec] ?: sec else sec
                    Row(
                        Modifier.padding(top = 14.dp, bottom = 6.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (on) Brush.horizontalGradient(listOf(tint.sb, tint.sb.copy(alpha = 0.5f)))
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .then(if (on) Modifier.border(1.5.dp, tint.sl.copy(alpha = 0.6f), RoundedCornerShape(14.dp)) else Modifier)
                        .mmsClickable { onDest(d) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TintTile(d.route.take(1), tint, 34.dp, 10.dp)
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.foundation.text.BasicText(
                        I18n.t(d.titleKey),
                        style = MmsType.body.copy(
                            color = if (on) tint.st else c.mut,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (on) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(tint.sc))
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.5f)))

        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MmsButton("Logout", onLogout, modifier = Modifier.weight(1f), small = true, danger = true, icon = "⎋")
        }
        androidx.compose.foundation.text.BasicText(
            "v2.0.0 · Android",
            style = MmsType.caption.copy(color = c.fnt),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun ModuleHost(
    dest: Dest,
    navArg: String,
    toast: (String, ToastMsg.Kind) -> Unit,
    onNavigate: (Dest) -> Unit,
) {
    when (dest) {
        Dest.Dashboard -> DashboardScreen(toast = toast, onNavigate = onNavigate)
        Dest.Families -> FamiliesScreen(toast = toast, initialSearch = navArg)
        Dest.Members -> MembersScreen(toast = toast, initialSearch = navArg)
        Dest.Staff -> StaffScreen(toast = toast)
        Dest.Committee -> CommitteeScreen(toast = toast)
        Dest.Subscriptions -> SubscriptionsScreen(toast = toast, initialSearch = navArg)
        Dest.Donations -> DonationsScreen(toast = toast, initialSearch = navArg)
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
