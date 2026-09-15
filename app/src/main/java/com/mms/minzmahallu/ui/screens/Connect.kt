package com.mms.minzmahallu.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mms.minzmahallu.MmsApp
import com.mms.minzmahallu.data.model.ToastMsg
import com.mms.minzmahallu.i18n.I18n
import com.mms.minzmahallu.ui.components.*
import com.mms.minzmahallu.ui.theme.*
import com.mms.minzmahallu.util.Format
import com.mms.minzmahallu.util.ShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WhatsAppScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf("Families") }
    var search by remember { mutableStateOf("") }
    var families by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var receipts by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var orgName by remember { mutableStateOf(I18n.t("app_name")) }
    var composeFor by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var message by remember { mutableStateOf("") }
    var dueLine by remember { mutableStateOf("") }
    val c = C()

    fun reload() = scope.launch {
        loading = true
        try {
            withContext(Dispatchers.IO) {
                orgName = Format.str(repo.settingsLoad(), "mahallu_name").ifBlank { I18n.t("app_name") }
                if (tab == "Families") families = repo.familiesWithPhones(search.ifBlank { null })
                else receipts = repo.recentReceipts(60)
            }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(tab, search) { reload() }

    fun openCompose(fam: Map<String, Any?>) {
        composeFor = fam
        message = "Assalamu Alaikum ${Format.str(fam, "house_name")}, this is $orgName."
        dueLine = ""
        scope.launch {
            val due = withContext(Dispatchers.IO) {
                val sub = repo.subscriptionsList(Format.str(fam, "family_number")).rows.firstOrNull()
                if (sub == null) 0.0
                else Format.num(sub, "amount") + Format.num(sub, "arrears") - Format.num(sub, "advance") - Format.num(sub, "amount_paid")
            }
            if (due > 0) dueLine = "Current subscription due: ${Format.money(due)}"
        }
    }

    ModuleScaffold(
        I18n.t("nav_whatsapp"), "Receipt delivery & announcements",
        listOf("Families", "Receipts"), tab, { tab = it },
        search = if (tab == "Families") search else null,
        onSearch = if (tab == "Families") ({ s: String -> search = s }) else null,
        loading = loading
    ) {
        Column {
            InfoBanner("Messages open in your WhatsApp app for review — nothing is sent automatically.", "info")
            Spacer(Modifier.height(10.dp))
            if (tab == "Families") {
                if (families.isEmpty()) {
                    EmptyState(
                        "No numbers found",
                        "Add phone numbers to family records to message them here"
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(families, key = { Format.long(it, "id") }) { fam ->
                            val num = Format.str(fam, "whatsapp_phone").ifBlank { Format.str(fam, "phone") }
                            MmsCard(onClick = { openCompose(fam) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TintTile(
                                        Format.str(fam, "house_name").ifBlank { "F" }.take(1),
                                        Tints.of("teal", c.isDark), 42.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        CellText(
                                            Format.str(fam, "house_name"),
                                            strong = true,
                                            sub = Format.str(fam, "family_number") +
                                                (if (Format.str(fam, "head_name").isNotBlank()) " · ${Format.str(fam, "head_name")}" else "")
                                        )
                                        androidx.compose.foundation.text.BasicText(
                                            ShareUtil.prettyPhone(num),
                                            style = MmsType.caption.copy(color = c.em, fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                    MmsButton("Chat", { openCompose(fam) }, small = true)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(90.dp)) }
                    }
                }
            } else {
                if (receipts.isEmpty()) {
                    EmptyState("No receipts yet", "Recorded donations & collections appear here")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(receipts, key = { Format.str(it, "kind") + Format.long(it, "ref_id") }) { r ->
                            val num = Format.str(r, "wa").ifBlank { Format.str(r, "phone") }
                            MmsCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        CellText(
                                            Format.str(r, "party"),
                                            strong = true,
                                            sub = "${Format.str(r, "receipt")} · ${Format.str(r, "date")}"
                                        )
                                        androidx.compose.foundation.text.BasicText(
                                            Format.money(Format.num(r, "amount")) + " · " + Format.str(r, "method"),
                                            style = MmsType.caption.copy(color = c.mut)
                                        )
                                    }
                                    MmsButton(if (num.isBlank()) "Share" else "Send", {
                                        scope.launch {
                                            val text = withContext(Dispatchers.IO) {
                                                repo.receiptText(Format.str(r, "kind"), Format.long(r, "ref_id"))
                                            }
                                            if (num.isBlank()) ShareUtil.shareText(ctx, text, "Share receipt")
                                            else ShareUtil.openWhatsApp(ctx, num, text)
                                        }
                                    }, small = true)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(90.dp)) }
                    }
                }
            }
        }
    }

    if (composeFor != null) {
        val fam = composeFor!!
        val num = Format.str(fam, "whatsapp_phone").ifBlank { Format.str(fam, "phone") }
        MmsDialog("Message — ${Format.str(fam, "house_name")}", onDismiss = { composeFor = null }, confirmLabel = "Open WhatsApp", onConfirm = {
            ShareUtil.openWhatsApp(ctx, num, message)
            composeFor = null
        }) {
            DetailRow("To", "${ShareUtil.prettyPhone(num)} (${Format.str(fam, "family_number")})")
            if (dueLine.isNotBlank()) {
                InfoBanner(dueLine, "warn")
                Spacer(Modifier.height(4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip("Greeting", false, {
                    message = "Assalamu Alaikum ${Format.str(fam, "house_name")}, this is $orgName."
                })
                FilterChip("Reminder", false, {
                    message = "Assalamu Alaikum ${Format.str(fam, "house_name")}, " +
                        (if (dueLine.isNotBlank()) "your $dueLine. Kindly pay at your convenience." else "this is a gentle reminder from $orgName.") +
                        " — $orgName"
                })
                FilterChip("Meeting", false, {
                    message = "Assalamu Alaikum, there will be a mahallu meeting soon. All are requested to attend. — $orgName"
                })
            }
            MmsInput(message, { message = it }, label = "Message", singleLine = false)
        }
    }
}
