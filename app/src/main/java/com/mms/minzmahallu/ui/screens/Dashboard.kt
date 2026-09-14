package com.mms.minzmahallu.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mms.minzmahallu.MmsApp
import com.mms.minzmahallu.data.model.*
import com.mms.minzmahallu.i18n.I18n
import com.mms.minzmahallu.ui.components.*
import com.mms.minzmahallu.ui.theme.*
import com.mms.minzmahallu.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(toast: (String, ToastMsg.Kind) -> Unit, onNavigate: (Dest) -> Unit) {
    val repo = MmsApp.instance.repo
    val c = C()
    var summary by remember { mutableStateOf(DashboardSummary()) }
    var collections by remember { mutableStateOf(listOf<ChartPoint>()) }
    var incomeExp by remember { mutableStateOf(listOf<ChartPoint>()) }
    var activity by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var glance by remember { mutableStateOf(mapOf<String, Any?>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            summary = repo.dashboardSummary()
            collections = repo.monthlyCollections()
            incomeExp = repo.incomeVsExpense()
            activity = repo.recentActivity()
            glance = repo.todayAtGlance()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(I18n.t("nav_dashboard"), I18n.t("dash_subtitle").ifBlank { "Overview of your mahallu today." }, T()) {
                MmsButton(I18n.t("action_refresh"), {
                    // reload
                }, small = true, primary = false)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(I18n.t("dash_qa_add_family"), Dest.Families, "em"),
                    Triple(I18n.t("dash_qa_record_payment"), Dest.Subscriptions, "gold"),
                    Triple(I18n.t("dash_qa_add_donation"), Dest.Donations, "pink"),
                ).forEach { (label, d, tintId) ->
                    val t = Tints.of(tintId, c.isDark)
                    MmsCard(modifier = Modifier.weight(1f), onClick = { onNavigate(d) }) {
                        Box(Modifier.size(28.dp).padding(0.dp))
                        androidx.compose.foundation.text.BasicText(label, style = MmsType.bodySm.copy(color = c.tx, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                    }
                }
            }
        }
        item {
            // 2x2 stat grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(I18n.t("dash_total_families"), summary.totalFamilies.toString(), Tints.of("em", c.isDark), I18n.t("dash_active"), Modifier.weight(1f))
                    StatTile(I18n.t("dash_total_members"), summary.totalMembers.toString(), Tints.of("teal", c.isDark), I18n.t("dash_active"), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(I18n.t("dash_monthly_collection"), Format.moneyShort(summary.monthlyCollection), Tints.of("gold", c.isDark), I18n.t("dash_this_month"), Modifier.weight(1f))
                    StatTile(I18n.t("dash_pending_dues"), Format.moneyShort(summary.pendingDues), Tints.of("rose", c.isDark), I18n.t("dash_overdue"), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(I18n.t("dash_donations_month"), Format.moneyShort(summary.monthlyDonations), Tints.of("pink", c.isDark), I18n.t("dash_this_month"), Modifier.weight(1f))
                    StatTile(I18n.t("dash_fund_balance_short"), Format.moneyShort(summary.balance), Tints.of("sky", c.isDark), I18n.t("dash_all_funds"), Modifier.weight(1f))
                }
            }
        }
        item {
            MmsCard {
                androidx.compose.foundation.text.BasicText(I18n.t("dash_collections_chart"), style = MmsType.headline.copy(color = c.tx))
                androidx.compose.foundation.text.BasicText(I18n.t("dash_last_6_months"), style = MmsType.caption.copy(color = c.fnt))
                SimpleBarChart(collections)
            }
        }
        item {
            MmsCard {
                androidx.compose.foundation.text.BasicText(I18n.t("dash_income_vs_expense"), style = MmsType.headline.copy(color = c.tx))
                SimpleBarChart(incomeExp, dual = true)
            }
        }
        item {
            MmsCard {
                androidx.compose.foundation.text.BasicText(I18n.t("dash_today_glance"), style = MmsType.headline.copy(color = c.tx))
                Spacer(Modifier.height(8.dp))
                listOf(
                    I18n.t("dash_receipts_today") to (glance["receipts_today"]?.toString() ?: "0"),
                    I18n.t("dash_welfare_pending") to (glance["welfare_pending"]?.toString() ?: "0"),
                    "Overdue subs" to (glance["overdue_subs"]?.toString() ?: "0"),
                ).forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        androidx.compose.foundation.text.BasicText(k, style = MmsType.bodySm.copy(color = c.mut), modifier = Modifier.weight(1f))
                        androidx.compose.foundation.text.BasicText(v, style = MmsType.body.copy(color = c.tx, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                    }
                }
            }
        }
        item {
            MmsCard {
                androidx.compose.foundation.text.BasicText(I18n.t("dash_recent_activity"), style = MmsType.headline.copy(color = c.tx))
                Spacer(Modifier.height(8.dp))
                if (activity.isEmpty()) {
                    androidx.compose.foundation.text.BasicText(I18n.t("dash_no_activity"), style = MmsType.caption.copy(color = c.fnt))
                } else {
                    activity.take(8).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(Modifier.weight(1f)) {
                                androidx.compose.foundation.text.BasicText(Format.str(row, "action"), style = MmsType.bodySm.copy(color = c.tx, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                                androidx.compose.foundation.text.BasicText(Format.str(row, "description"), style = MmsType.caption.copy(color = c.fnt), maxLines = 1)
                            }
                            androidx.compose.foundation.text.BasicText(Format.str(row, "created_at").takeLast(8), style = MmsType.caption.copy(color = c.fnt))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
