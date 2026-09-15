package com.mms.minzmahallu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mms.minzmahallu.MmsApp
import com.mms.minzmahallu.data.model.*
import com.mms.minzmahallu.i18n.I18n
import com.mms.minzmahallu.ui.components.*
import com.mms.minzmahallu.ui.theme.*
import com.mms.minzmahallu.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(toast: (String, ToastMsg.Kind) -> Unit, onNavigate: (Dest) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    val c = C()
    var summary by remember { mutableStateOf(DashboardSummary()) }
    var collections by remember { mutableStateOf(listOf<ChartPoint>()) }
    var incomeExp by remember { mutableStateOf(listOf<ChartPoint>()) }
    var activity by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var glance by remember { mutableStateOf(mapOf<String, Any?>()) }
    var alerts by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var orgName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }

    fun reload(silent: Boolean = false) = scope.launch {
        if (!silent) refreshing = true
        try {
            withContext(Dispatchers.IO) {
                summary = repo.dashboardSummary()
                collections = repo.monthlyCollections()
                incomeExp = repo.incomeVsExpense()
                activity = repo.recentActivity()
                glance = repo.todayAtGlance()
                alerts = repo.alerts()
                orgName = Format.str(repo.settingsLoad(), "mahallu_name").ifBlank { I18n.t("app_name") }
            }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
        refreshing = false
    }
    LaunchedEffect(Unit) { reload() }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Hero greeting card
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0D9488), Color(0xFF0A5F5A))))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    androidx.compose.foundation.text.BasicText(
                        I18n.t("dash_greeting").ifBlank { "Assalamu Alaikum" },
                        style = MmsType.caption.copy(color = Color.White.copy(alpha = 0.75f))
                    )
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.text.BasicText(
                        orgName.ifBlank { I18n.t("app_name") },
                        style = MmsType.title.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroMini(
                            I18n.t("dash_receipts_today").ifBlank { "Receipts today" },
                            glance["receipts_today"]?.toString() ?: "0"
                        )
                        HeroMini(
                            I18n.t("dash_welfare_pending").ifBlank { "Welfare pending" },
                            glance["welfare_pending"]?.toString() ?: "0"
                        )
                        HeroMini("Overdue", glance["overdue_subs"]?.toString() ?: "0")
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    androidx.compose.foundation.text.BasicText(
                        Format.today(),
                        style = MmsType.code.copy(color = Color.White.copy(alpha = 0.85f))
                    )
                    Spacer(Modifier.height(10.dp))
                    MmsButton(
                        if (refreshing) "…" else "${G.REFRESH} ${I18n.t("action_refresh")}",
                        { reload() },
                        small = true,
                        primary = false,
                        enabled = !refreshing
                    )
                }
            }
        }

        // Alerts
        if (alerts.isNotEmpty()) {
            item {
                MmsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFDF5DD))
                                .border(1.dp, Color(0xFFF2E2A8), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                G.ALERT,
                                style = MmsType.headline.copy(color = Color(0xFF96640A), fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            alerts.forEach { a ->
                                androidx.compose.foundation.text.BasicText(
                                    Format.str(a, "message"),
                                    style = MmsType.bodySm.copy(color = c.tx, fontWeight = FontWeight.Medium),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MmsButton(
                            I18n.t("nav_subscriptions"),
                            { onNavigate(Dest.Subscriptions) },
                            small = true, primary = false, modifier = Modifier.weight(1f)
                        )
                        MmsButton(
                            I18n.t("nav_welfare"),
                            { onNavigate(Dest.Welfare) },
                            small = true, primary = false, modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Quick actions
        item {
            SectionLabel(I18n.t("dash_quick_actions").ifBlank { "Quick actions" })
        }
        item {
            val actions = listOf(
                Triple(I18n.t("dash_qa_add_family"), Dest.Families, "em"),
                Triple(I18n.t("dash_qa_record_payment"), Dest.Subscriptions, "gold"),
                Triple(I18n.t("dash_qa_add_donation"), Dest.Donations, "pink"),
                Triple(I18n.t("dash_qa_add_transaction").ifBlank { "Add entry" }, Dest.Accounting, "sky"),
                Triple(I18n.t("dash_qa_issue_certificate").ifBlank { "Issue certificate" }, Dest.Certificates, "cyan"),
                Triple(I18n.t("dash_qa_view_reports").ifBlank { "Reports" }, Dest.Reports, "blue"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, d, tintId) ->
                            val t = Tints.of(tintId, c.isDark)
                            MmsCard(modifier = Modifier.weight(1f), onClick = { onNavigate(d) }) {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    TintTile(label.take(1), t, 40.dp)
                                    Spacer(Modifier.height(8.dp))
                                    androidx.compose.foundation.text.BasicText(
                                        label,
                                        style = MmsType.caption.copy(
                                            color = c.tx,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Stats
        item {
            SectionLabel(I18n.t("dash_overview").ifBlank { "Overview" })
        }
        item {
            if (loading) {
                LoadingList(3)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            I18n.t("dash_total_families"), summary.totalFamilies.toString(),
                            Tints.of("em", c.isDark), I18n.t("dash_active"), Modifier.weight(1f)
                        )
                        StatTile(
                            I18n.t("dash_total_members"), summary.totalMembers.toString(),
                            Tints.of("teal", c.isDark), I18n.t("dash_active"), Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            I18n.t("dash_monthly_collection"), Format.moneyShort(summary.monthlyCollection),
                            Tints.of("gold", c.isDark), I18n.t("dash_this_month"), Modifier.weight(1f)
                        )
                        StatTile(
                            I18n.t("dash_pending_dues"), Format.moneyShort(summary.pendingDues),
                            Tints.of("rose", c.isDark), I18n.t("dash_overdue"), Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            I18n.t("dash_donations_month"), Format.moneyShort(summary.monthlyDonations),
                            Tints.of("pink", c.isDark), I18n.t("dash_this_month"), Modifier.weight(1f)
                        )
                        StatTile(
                            I18n.t("dash_fund_balance_short"), Format.moneyShort(summary.balance),
                            Tints.of("sky", c.isDark), I18n.t("dash_all_funds"), Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            "Marriages ${Format.year()}", summary.marriagesThisYear.toString(),
                            Tints.of("vio", c.isDark), null, Modifier.weight(1f)
                        )
                        StatTile(
                            "Welfare aided", summary.welfareBeneficiaries.toString(),
                            Tints.of("orange", c.isDark), null, Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Charts
        item {
            MmsCard {
                androidx.compose.foundation.text.BasicText(
                    I18n.t("dash_collections_chart"),
                    style = MmsType.headline.copy(color = c.tx)
                )
                androidx.compose.foundation.text.BasicText(
                    I18n.t("dash_last_6_months"),
                    style = MmsType.caption.copy(color = c.fnt)
                )
                if (collections.all { it.value <= 0 }) {
                    EmptyState(
                        I18n.t("dash_no_collections").ifBlank { "No collections yet" },
                        I18n.t("dash_no_collections_sub").ifBlank { "Recorded payments will appear here" }
                    )
                } else {
                    SimpleBarChart(collections)
                }
            }
        }
        item {
            MmsCard {
                androidx.compose.foundation.text.BasicText(
                    I18n.t("dash_income_vs_expense"),
                    style = MmsType.headline.copy(color = c.tx)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c.em))
                    Spacer(Modifier.width(6.dp))
                    androidx.compose.foundation.text.BasicText(
                        I18n.t("acc_income").ifBlank { "Income" },
                        style = MmsType.caption.copy(color = c.mut)
                    )
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c.cRose))
                    Spacer(Modifier.width(6.dp))
                    androidx.compose.foundation.text.BasicText(
                        I18n.t("acc_expense").ifBlank { "Expense" },
                        style = MmsType.caption.copy(color = c.mut)
                    )
                }
                SimpleBarChart(incomeExp, dual = true)
            }
        }

        // Recent activity
        item {
            MmsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicText(
                        I18n.t("dash_recent_activity"),
                        style = MmsType.headline.copy(color = c.tx),
                        modifier = Modifier.weight(1f)
                    )
                    MmsButton(I18n.t("nav_audit"), { onNavigate(Dest.Audit) }, small = true, primary = false)
                }
                Spacer(Modifier.height(8.dp))
                if (activity.isEmpty()) {
                    androidx.compose.foundation.text.BasicText(
                        I18n.t("dash_no_activity"),
                        style = MmsType.caption.copy(color = c.fnt)
                    )
                } else {
                    activity.take(8).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val module = Format.str(row, "module").ifBlank { "general" }
                            val t = Tints.of(moduleTint(module), c.isDark)
                            Box(Modifier.size(9.dp).clip(androidx.compose.foundation.shape.CircleShape).background(t.sc))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(
                                    Format.str(row, "action"),
                                    style = MmsType.bodySm.copy(color = c.tx, fontWeight = FontWeight.SemiBold)
                                )
                                androidx.compose.foundation.text.BasicText(
                                    Format.str(row, "description"),
                                    style = MmsType.caption.copy(color = c.fnt),
                                    maxLines = 1
                                )
                            }
                            androidx.compose.foundation.text.BasicText(
                                Format.str(row, "created_at").takeLast(8).take(5),
                                style = MmsType.caption.copy(color = c.fnt)
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun HeroMini(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        androidx.compose.foundation.text.BasicText(
            value,
            style = MmsType.headline.copy(color = Color.White, fontWeight = FontWeight.Bold)
        )
        androidx.compose.foundation.text.BasicText(
            label,
            style = MmsType.caption.copy(color = Color.White.copy(alpha = 0.75f)),
            maxLines = 1
        )
    }
}

private fun moduleTint(module: String): String = when (module.lowercase()) {
    "families" -> "em"
    "members" -> "teal"
    "subscriptions" -> "gold"
    "donations" -> "pink"
    "accounting", "transactions" -> "sky"
    "welfare" -> "orange"
    "certificates" -> "cyan"
    "tokens" -> "pink"
    "users", "auth" -> "blue"
    "staff" -> "vio"
    "assets" -> "teal"
    else -> "slate"
}
