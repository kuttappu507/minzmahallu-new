package com.mms.minzmahallu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mms.minzmahallu.MmsApp
import com.mms.minzmahallu.data.model.ToastMsg
import com.mms.minzmahallu.i18n.I18n
import com.mms.minzmahallu.ui.components.*
import com.mms.minzmahallu.ui.theme.*
import com.mms.minzmahallu.util.Format
import com.mms.minzmahallu.util.PdfUtil
import com.mms.minzmahallu.util.ShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReportSpec(val id: String, val title: String, val desc: String, val tint: String)

private val REPORTS = listOf(
    ReportSpec("family_dir", "Family directory", "All families with member counts", "em"),
    ReportSpec("member_roll", "Member roll", "Complete member listing", "teal"),
    ReportSpec("defaulters", "Defaulters", "Pending & overdue subscriptions", "rose"),
    ReportSpec("donations", "Donation summary", "Totals by category & month", "pink"),
    ReportSpec("finance", "Income & expense", "Monthly P&L with donations", "sky"),
    ReportSpec("collections", "Collection register", "Subscription receipts in period", "gold"),
    ReportSpec("marriages", "Marriage register", "Chronological nikah list", "vio"),
    ReportSpec("deaths", "Death register", "Chronological death list", "slate"),
    ReportSpec("welfare", "Welfare register", "Aid requests & disbursements", "orange"),
    ReportSpec("certificates", "Certificate log", "Issued certificates", "cyan"),
)

@Composable
fun ReportsScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    var open by remember { mutableStateOf<ReportSpec?>(null) }
    val c = C()
    if (open == null) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PageHeader(I18n.t("nav_reports"), "Printable registers & summaries", T()) }
            items(REPORTS) { spec ->
                val t = Tints.of(spec.tint, c.isDark)
                MmsCard(onClick = { open = spec }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TintTile(spec.title.take(1), t, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.foundation.text.BasicText(
                                spec.title,
                                style = MmsType.headline.copy(color = c.tx, fontSize = 15.sp)
                            )
                            androidx.compose.foundation.text.BasicText(
                                spec.desc,
                                style = MmsType.caption.copy(color = c.mut)
                            )
                        }
                        androidx.compose.foundation.text.BasicText(
                            G.RIGHT,
                            style = MmsType.headline.copy(color = t.sc, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    } else {
        ReportDetail(open!!, onBack = { open = null }, toast = toast)
    }
}

@Composable
private fun ReportDetail(spec: ReportSpec, onBack: () -> Unit, toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val c = C()
    val t = Tints.of(spec.tint, c.isDark)
    var columns by remember(spec) { mutableStateOf(listOf<String>()) }
    var weights by remember(spec) { mutableStateOf(listOf<Float>()) }
    var rows by remember(spec) { mutableStateOf(listOf<List<String>>()) }
    var summary by remember(spec) { mutableStateOf(listOf<Pair<String, String>>()) }
    var loading by remember(spec) { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var orgName by remember { mutableStateOf("Minz Mahallu") }
    val needsRange = spec.id in listOf("donations", "collections")
    var from by remember(spec) { mutableStateOf(Format.today().take(7) + "-01") }
    var to by remember(spec) { mutableStateOf(Format.today()) }

    suspend fun load() {
        loading = true
        try {
            val data = withContext(Dispatchers.IO) {
                orgName = Format.str(repo.settingsLoad(), "mahallu_name").ifBlank { "Minz Mahallu" }
                when (spec.id) {
                    "family_dir" -> {
                        val r = repo.reportFamilyDirectory()
                        ReportData(
                            listOf("Family", "House", "Ward", "Phone", "Members"),
                            listOf(1f, 1.6f, 1f, 1.2f, 0.8f),
                            r.map {
                                listOf(
                                    Format.str(it, "family_number"), Format.str(it, "house_name"),
                                    Format.str(it, "ward"), Format.str(it, "phone"),
                                    Format.long(it, "members").toString()
                                )
                            },
                            listOf("Families" to r.size.toString())
                        )
                    }
                    "member_roll" -> {
                        val r = repo.reportMemberRoll()
                        ReportData(
                            listOf("Code", "Name", "Mobile", "Family", "Status"),
                            listOf(1f, 1.8f, 1.1f, 1.2f, 0.9f),
                            r.map {
                                listOf(
                                    Format.str(it, "member_code"), Format.str(it, "name"),
                                    Format.str(it, "mobile"), Format.str(it, "family_number"),
                                    Format.str(it, "status")
                                )
                            },
                            listOf("Members" to r.size.toString())
                        )
                    }
                    "defaulters" -> {
                        val r = repo.defaultersList()
                        val due = r.sumOf { Format.num(it, "due_amount") }
                        ReportData(
                            listOf("Family", "House", "Phone", "Pending", "Due"),
                            listOf(1f, 1.6f, 1.1f, 0.8f, 1f),
                            r.map {
                                listOf(
                                    Format.str(it, "family_number"), Format.str(it, "house_name"),
                                    Format.str(it, "phone"), Format.long(it, "pending_count").toString(),
                                    Format.money(Format.num(it, "due_amount"))
                                )
                            },
                            listOf("Defaulters" to r.size.toString(), "Total due" to Format.money(due))
                        )
                    }
                    "donations" -> {
                        val r = repo.reportDonationsByCategory(from, to)
                        val total = r.sumOf { Format.num(it, "total") }
                        ReportData(
                            listOf("Category", "Count", "Total"),
                            listOf(2f, 0.8f, 1.2f),
                            r.map {
                                listOf(
                                    Format.str(it, "category"), Format.long(it, "count").toString(),
                                    Format.money(Format.num(it, "total"))
                                )
                            },
                            listOf("Period" to "$from → $to", "Total" to Format.money(total))
                        )
                    }
                    "finance" -> {
                        val r = repo.reportMonthlyFinance(12)
                        val net = r.sumOf { Format.num(it, "net") }
                        ReportData(
                            listOf("Month", "Income", "Expense", "Don.", "Subs", "Net"),
                            listOf(1.1f, 1f, 1f, 0.9f, 0.9f, 1f),
                            r.map {
                                listOf(
                                    Format.str(it, "month"),
                                    Format.moneyShort(Format.num(it, "income")),
                                    Format.moneyShort(Format.num(it, "expense")),
                                    Format.moneyShort(Format.num(it, "donations")),
                                    Format.moneyShort(Format.num(it, "subscriptions")),
                                    Format.moneyShort(Format.num(it, "net"))
                                )
                            },
                            listOf("12-month net" to Format.money(net))
                        )
                    }
                    "collections" -> {
                        val r = repo.reportCollectionRegister(from, to)
                        val total = r.sumOf { Format.num(it, "amount") }
                        ReportData(
                            listOf("Receipt", "Date", "Family", "Amount"),
                            listOf(1.4f, 1f, 1.6f, 1f),
                            r.map {
                                listOf(
                                    Format.str(it, "receipt"), Format.str(it, "date"),
                                    Format.str(it, "family"), Format.money(Format.num(it, "amount"))
                                )
                            },
                            listOf("Receipts" to r.size.toString(), "Total" to Format.money(total))
                        )
                    }
                    "marriages" -> {
                        val r = repo.marriagesList().rows
                        ReportData(
                            listOf("No", "Groom", "Bride", "Date"),
                            listOf(1.2f, 1.5f, 1.5f, 1f),
                            r.map {
                                listOf(
                                    Format.str(it, "marriage_number"), Format.str(it, "groom_name"),
                                    Format.str(it, "bride_name"), Format.str(it, "nikah_date")
                                )
                            },
                            listOf("Marriages" to r.size.toString())
                        )
                    }
                    "deaths" -> {
                        val r = repo.deathsList().rows
                        ReportData(
                            listOf("No", "Name", "Date", "Burial place"),
                            listOf(1.2f, 1.7f, 1f, 1.3f),
                            r.map {
                                listOf(
                                    Format.str(it, "death_number"), Format.str(it, "deceased_name"),
                                    Format.str(it, "date_of_death"), Format.str(it, "burial_place")
                                )
                            },
                            listOf("Records" to r.size.toString())
                        )
                    }
                    "welfare" -> {
                        val r = repo.welfareList().rows
                        val disbursed = r.filter { Format.str(it, "status") == "Disbursed" }.sumOf { Format.num(it, "amount_approved") }
                        ReportData(
                            listOf("No", "Applicant", "Category", "Status", "Amount"),
                            listOf(1f, 1.5f, 1.3f, 1f, 1f),
                            r.map {
                                listOf(
                                    Format.str(it, "request_number"), Format.str(it, "applicant_name"),
                                    Format.str(it, "category"), Format.str(it, "status"),
                                    Format.money(Format.num(it, "amount_requested"))
                                )
                            },
                            listOf("Requests" to r.size.toString(), "Disbursed" to Format.money(disbursed))
                        )
                    }
                    else -> {
                        val r = repo.reportCertificateLog()
                        ReportData(
                            listOf("No", "Type", "Issued to", "Status"),
                            listOf(1.3f, 1f, 1.7f, 0.9f),
                            r.map {
                                listOf(
                                    Format.str(it, "certificate_number"), Format.str(it, "type"),
                                    Format.str(it, "issued_to"), Format.str(it, "status")
                                )
                            },
                            listOf("Certificates" to r.size.toString())
                        )
                    }
                }
            }
            columns = data.columns; weights = data.weights; rows = data.rows; summary = data.summary
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }

    LaunchedEffect(spec, from, to) {
        if (!needsRange || (from.isNotBlank() && to.isNotBlank())) load()
    }

    fun exportPdf() = scope.launch {
        busy = true
        try {
            val sub = if (needsRange) "$from → $to" else "As on ${Format.today()}"
            val file = withContext(Dispatchers.IO) {
                PdfUtil.tablePdf(
                    ctx, spec.title, sub, orgName, columns, weights, rows,
                    null, "report-${spec.id}.pdf"
                )
            }
            ShareUtil.shareFile(ctx, file, "application/pdf", "Share ${spec.title}")
        } catch (e: Exception) {
            toast(e.message ?: "PDF failed", ToastMsg.Kind.Error)
        }
        busy = false
    }

    fun shareText() {
        val sb = StringBuilder()
        sb.appendLine("*$orgName*")
        sb.appendLine(spec.title)
        sb.appendLine(if (needsRange) "$from → $to" else Format.today())
        sb.appendLine("------------------------")
        summary.forEach { (k, v) -> sb.appendLine("$k: $v") }
        sb.appendLine("------------------------")
        rows.take(60).forEach { sb.appendLine(it.joinToString(" | ")) }
        if (rows.size > 60) sb.appendLine("…and ${rows.size - 60} more (see PDF)")
        ShareUtil.shareText(ctx, sb.toString(), "Share ${spec.title}")
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageHeader(spec.title, spec.desc, t) {
            MmsButton("${G.LEFT} Back", onBack, small = true, primary = false)
        }
        if (needsRange) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsDateField(from, { from = it }, label = "From", modifier = Modifier.weight(1f))
                MmsDateField(to, { to = it }, label = "To", modifier = Modifier.weight(1f))
            }
        }
        if (summary.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.take(3).forEach { (k, v) ->
                    StatTile(k, v, t, modifier = Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MmsButton("PDF ${G.SHARE}", { exportPdf() }, small = true, modifier = Modifier.weight(1f), enabled = !busy && !loading)
            MmsButton("Share text", { shareText() }, small = true, primary = false, modifier = Modifier.weight(1f))
            MmsButton("${G.REFRESH}", { scope.launch { load() } }, small = true, primary = false)
        }
        if (loading) {
            LoadingList()
        } else if (rows.isEmpty()) {
            EmptyState("No data", "Nothing to show for this report yet")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(c.head)
                            .border(1.dp, c.line, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        val total = weights.sum().coerceAtLeast(1f)
                        columns.forEachIndexed { i, col ->
                            androidx.compose.foundation.text.BasicText(
                                col.uppercase(),
                                style = MmsType.label.copy(color = c.mut, fontSize = 10.sp),
                                modifier = Modifier.weight(weights.getOrElse(i) { 1f } / total),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                items(rows) { r ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(c.panel)
                            .border(1.dp, c.line)
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        val total = weights.sum().coerceAtLeast(1f)
                        r.forEachIndexed { i, cell ->
                            androidx.compose.foundation.text.BasicText(
                                cell.ifBlank { "—" },
                                style = MmsType.bodySm.copy(color = c.tx, fontSize = 12.sp),
                                modifier = Modifier.weight(weights.getOrElse(i) { 1f } / total),
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            "${rows.size} rows",
                            style = MmsType.caption.copy(color = c.fnt)
                        )
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }
}

private data class ReportData(
    val columns: List<String>,
    val weights: List<Float>,
    val rows: List<List<String>>,
    val summary: List<Pair<String, String>>,
)
