package com.mms.minzmahallu.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- assets

@Composable
fun AssetsScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf(mapOf<String, Any?>()) }
    var show by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var detail by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Other") }
    var location by remember { mutableStateOf("") }
    var refNo by remember { mutableStateOf("") }
    var acqDate by remember { mutableStateOf("") }
    var acqCost by remember { mutableStateOf("") }
    var curValue by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("In use") }
    var condition by remember { mutableStateOf("Good") }
    var custodian by remember { mutableStateOf("") }
    var incomeGen by remember { mutableStateOf(false) }
    var tenant by remember { mutableStateOf("") }
    var rent by remember { mutableStateOf("") }
    var agreeStart by remember { mutableStateOf("") }
    var agreeEnd by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val c = C()

    fun reload() = scope.launch {
        loading = true
        try {
            withContext(Dispatchers.IO) {
                rows = repo.assetsList(search, if (cat == "All") null else cat).rows
                summary = repo.assetSummary()
            }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search, cat) { reload() }

    fun openForm(row: Map<String, Any?>?) {
        editId = row?.let { Format.long(it, "id") }
        name = row?.let { Format.str(it, "name") } ?: ""
        category = row?.let { Format.str(it, "category") }?.ifBlank { "Other" } ?: "Other"
        location = row?.let { Format.str(it, "location") } ?: ""
        refNo = row?.let { Format.str(it, "reference_no") } ?: ""
        acqDate = row?.let { Format.str(it, "acquisition_date") } ?: ""
        acqCost = row?.let { Format.num(it, "acquisition_cost").let { v -> if (v > 0) v.toString() else "" } } ?: ""
        curValue = row?.let { Format.num(it, "current_value").let { v -> if (v > 0) v.toString() else "" } } ?: ""
        status = row?.let { Format.str(it, "status") }?.ifBlank { "In use" } ?: "In use"
        condition = row?.let { Format.str(it, "condition_note") }?.ifBlank { "Good" } ?: "Good"
        custodian = row?.let { Format.str(it, "custodian") } ?: ""
        incomeGen = row?.let { Format.long(it, "income_generating") == 1L } ?: false
        tenant = row?.let { Format.str(it, "tenant_name") } ?: ""
        rent = row?.let { Format.num(it, "monthly_rent").let { v -> if (v > 0) v.toString() else "" } } ?: ""
        agreeStart = row?.let { Format.str(it, "agreement_start") } ?: ""
        agreeEnd = row?.let { Format.str(it, "agreement_end") } ?: ""
        notes = row?.let { Format.str(it, "notes") } ?: ""
        show = true
    }

    ModuleScaffold(
        I18n.t("nav_assets"), "Buildings, land & rentable goods",
        listOf("All") + repo.assetCategories().take(5), cat, { cat = it },
        search, { search = it }, loading = loading,
        onAdd = { openForm(null) }, addLabel = "Add asset"
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Assets", ((summary["count"] as? Number)?.toLong() ?: 0L).toString(), Tints.of("teal", c.isDark), modifier = Modifier.weight(1f))
                StatTile("Value", Format.moneyShort((summary["value"] as? Number)?.toDouble() ?: 0.0), Tints.of("em", c.isDark), modifier = Modifier.weight(1f))
                StatTile("Rented", ((summary["rented"] as? Number)?.toLong() ?: 0L).toString(), Tints.of("gold", c.isDark), modifier = Modifier.weight(1f))
            }
            if (rows.isEmpty()) {
                EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "", "Add asset") { openForm(null) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { Format.long(it, "id") }) { row ->
                        MmsCard(onClick = { detail = row }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TintTile(Format.str(row, "name").ifBlank { "A" }.take(1), Tints.of("teal", c.isDark), 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    CellText(Format.str(row, "name"), strong = true, sub = Format.str(row, "asset_code") + " · " + Format.str(row, "category"))
                                    androidx.compose.foundation.text.BasicText(
                                        Format.moneyShort(Format.num(row, "current_value")) +
                                            (if (Format.str(row, "status") == "Given rent") " · ${Format.str(row, "tenant_name")} · ${Format.money(Format.num(row, "monthly_rent"))}/mo" else ""),
                                        style = MmsType.caption.copy(color = c.mut)
                                    )
                                }
                                StatusPill(Format.str(row, "status"), Format.str(row, "status"))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }

    if (detail != null) {
        val row = detail!!
        MmsDialog(Format.str(row, "name"), onDismiss = { detail = null }) {
            DetailRow("Code", Format.str(row, "asset_code"), mono = true)
            DetailRow("Category", Format.str(row, "category"))
            DetailRow("Location", Format.str(row, "location"))
            DetailRow("Reference", Format.str(row, "reference_no"))
            DetailRow("Acquired", "${Format.str(row, "acquisition_date")} · ${Format.money(Format.num(row, "acquisition_cost"))}")
            DetailRow("Current value", Format.money(Format.num(row, "current_value")), strong = true)
            DetailRow("Status", Format.str(row, "status"), strong = true)
            DetailRow("Condition", Format.str(row, "condition_note"))
            DetailRow("Custodian", Format.str(row, "custodian"))
            if (Format.long(row, "income_generating") == 1L || Format.str(row, "status") == "Given rent") {
                SectionLabel("Rental")
                DetailRow("Tenant", Format.str(row, "tenant_name"))
                DetailRow("Monthly rent", Format.money(Format.num(row, "monthly_rent")))
                DetailRow("Agreement", "${Format.str(row, "agreement_start")} → ${Format.str(row, "agreement_end")}")
            }
            DetailRow("Notes", Format.str(row, "notes"))
            MmsButton(I18n.t("action_edit"), { openForm(row); detail = null }, small = true, modifier = Modifier.fillMaxWidth())
        }
    }

    if (show) {
        MmsDialog(
            if (editId == null) "Add asset" else I18n.t("action_edit"),
            onDismiss = { show = false },
            onConfirm = {
                scope.launch {
                    try {
                        val data = mapOf(
                            "name" to name, "category" to category, "location" to location,
                            "referenceNo" to refNo, "acquisitionDate" to acqDate,
                            "acquisitionCost" to (acqCost.toDoubleOrNull() ?: 0.0),
                            "currentValue" to (curValue.toDoubleOrNull() ?: 0.0),
                            "status" to status, "conditionNote" to condition, "custodian" to custodian,
                            "incomeGenerating" to if (incomeGen) 1 else 0,
                            "tenantName" to tenant, "monthlyRent" to (rent.toDoubleOrNull() ?: 0.0),
                            "agreementStart" to agreeStart, "agreementEnd" to agreeEnd, "notes" to notes
                        )
                        withContext(Dispatchers.IO) {
                            if (editId == null) repo.assetCreate(data) else repo.assetUpdate(editId!!, data)
                        }
                        show = false; reload(); toast("Saved ✓", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                }
            },
            confirmEnabled = name.isNotBlank()
        ) {
            MmsInput(name, { name = it }, label = "Name")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsSelect(category, repo.assetCategories(), { category = it }, label = "Category", modifier = Modifier.weight(1f))
                MmsSelect(status, repo.assetStatuses(), { status = it }, label = "Status", modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(location, { location = it }, label = "Location", modifier = Modifier.weight(1f))
                MmsInput(refNo, { refNo = it }, label = "Doc ref no", modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsDateField(acqDate, { acqDate = it }, label = "Acquired on", modifier = Modifier.weight(1f))
                MmsInput(acqCost, { acqCost = it }, label = "Cost", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(curValue, { curValue = it }, label = "Current value", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                MmsSelect(condition, listOf("Excellent", "Good", "Fair", "Needs repair", "Damaged"), { condition = it }, label = "Condition", modifier = Modifier.weight(1f))
            }
            MmsInput(custodian, { custodian = it }, label = "Custodian")
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText("Income generating", style = MmsType.bodySm.copy(color = c.tx), modifier = Modifier.weight(1f))
                FilterChip("Yes", incomeGen, { incomeGen = true })
                Spacer(Modifier.width(6.dp))
                FilterChip("No", !incomeGen, { incomeGen = false })
            }
            if (incomeGen || status == "Given rent") {
                SectionLabel("Rental")
                MmsInput(tenant, { tenant = it }, label = "Tenant name")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MmsInput(rent, { rent = it }, label = "Monthly rent", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    MmsDateField(agreeStart, { agreeStart = it }, label = "Agreement from", modifier = Modifier.weight(1f))
                }
                MmsDateField(agreeEnd, { agreeEnd = it }, label = "Agreement to")
            }
            MmsInput(notes, { notes = it }, label = "Notes", singleLine = false)
        }
    }
}

// ---------------------------------------------------------------- audit

@Composable
fun AuditScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var module by remember { mutableStateOf("All") }
    var modules by remember { mutableStateOf(listOf<String>()) }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<Map<String, Any?>?>(null) }
    val c = C()

    fun reload() = scope.launch {
        loading = true
        try {
            withContext(Dispatchers.IO) {
                if (modules.isEmpty()) modules = repo.auditModules()
                val all = repo.auditList(search).rows
                rows = if (module == "All") all else all.filter { Format.str(it, "module") == module }
            }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search, module) { reload() }

    ModuleScaffold(
        I18n.t("nav_audit"), "Tamper-evident activity log",
        listOf("All") + modules.take(8), module, { module = it },
        search, { search = it }, loading = loading
    ) {
        if (rows.isEmpty()) {
            EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "Actions across the app are logged here")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows.take(300), key = { Format.long(it, "id") }) { row ->
                    MmsCard(onClick = { detail = row }) {
                        CellText(
                            Format.str(row, "action") + " · " + Format.str(row, "module"),
                            strong = true, sub = Format.str(row, "description")
                        )
                        androidx.compose.foundation.text.BasicText(
                            Format.str(row, "username") + " · " + Format.str(row, "created_at"),
                            style = MmsType.caption.copy(color = c.fnt)
                        )
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    if (detail != null) {
        val row = detail!!
        MmsDialog("Audit #${Format.long(row, "id")}", onDismiss = { detail = null }, compact = true) {
            DetailRow("User", Format.str(row, "username"))
            DetailRow("Action", Format.str(row, "action"), strong = true)
            DetailRow("Module", Format.str(row, "module"))
            DetailRow("Entity", Format.long(row, "entity_id").toString())
            DetailRow("Description", Format.str(row, "description"))
            DetailRow("Time", Format.str(row, "created_at"))
            DetailRow("Hash", Format.str(row, "entry_hash"), mono = true)
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("₹") }
    var prefix by remember { mutableStateOf("RCP") }
    var monthly by remember { mutableStateOf("100") }
    var freq by remember { mutableStateOf("Monthly") }
    var fy by remember { mutableStateOf("04-01") }
    var wakf by remember { mutableStateOf("") }
    var society by remember { mutableStateOf("") }
    var village by remember { mutableStateOf("") }
    var panchayath by remember { mutableStateOf("") }
    var taluk by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var autoBackup by remember { mutableStateOf(true) }
    var backupHours by remember { mutableStateOf("24") }
    var loaded by remember { mutableStateOf(false) }
    var pwd1 by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }
    val me = repo.auth.currentUser
    val c = C()

    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) { repo.settingsLoad() }
        name = Format.str(s, "mahallu_name"); address = Format.str(s, "address")
        phone = Format.str(s, "phone"); email = Format.str(s, "email")
        currency = Format.str(s, "currency_symbol").ifBlank { "₹" }
        prefix = Format.str(s, "receipt_prefix").ifBlank { "RCP" }
        monthly = Format.num(s, "subscription_monthly_amount").let { if (it > 0) it.toString() else "100" }
        freq = Format.str(s, "subscription_frequency").ifBlank { "Monthly" }
        fy = Format.str(s, "financial_year_start").ifBlank { "04-01" }
        wakf = Format.str(s, "wakf_reg_no"); society = Format.str(s, "society_reg_no")
        village = Format.str(s, "village"); panchayath = Format.str(s, "panchayath")
        taluk = Format.str(s, "taluk"); district = Format.str(s, "district")
        pincode = Format.str(s, "pincode"); state = Format.str(s, "state")
        autoBackup = Format.long(s, "auto_backup") != 0L
        backupHours = (Format.long(s, "backup_interval_hours").let { if (it > 0) it else 24 }).toString()
        loaded = true
    }

    fun save() = scope.launch {
        try {
            withContext(Dispatchers.IO) {
                repo.settingsSave(
                    mapOf(
                        "mahalluName" to name, "address" to address, "phone" to phone, "email" to email,
                        "currencySymbol" to currency, "receiptPrefix" to prefix,
                        "subscriptionMonthlyAmount" to (monthly.toDoubleOrNull() ?: 100.0),
                        "subscriptionFrequency" to freq, "financialYearStart" to fy,
                        "wakfRegNo" to wakf, "societyRegNo" to society,
                        "village" to village, "panchayath" to panchayath, "taluk" to taluk,
                        "district" to district, "pincode" to pincode, "state" to state,
                        "autoBackup" to if (autoBackup) 1 else 0,
                        "backupIntervalHours" to (backupHours.toLongOrNull() ?: 24)
                    )
                )
            }
            toast("Settings saved ✓", ToastMsg.Kind.Success)
        } catch (e: Exception) {
            toast(e.message ?: "Error", ToastMsg.Kind.Error)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader(I18n.t("nav_settings"), "Mahallu profile & preferences", T()) }
        if (!loaded) {
            item { LoadingList(3) }
        } else {
            item {
                MmsCard {
                    SectionLabel("Mahallu profile")
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MmsInput(name, { name = it }, label = "Mahallu name")
                        MmsInput(address, { address = it }, label = "Address", singleLine = false)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MmsInput(phone, { phone = it }, label = "Phone", modifier = Modifier.weight(1f))
                            MmsInput(email, { email = it }, label = "Email", modifier = Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MmsInput(village, { village = it }, label = "Village", modifier = Modifier.weight(1f))
                            MmsInput(panchayath, { panchayath = it }, label = "Panchayath", modifier = Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MmsInput(taluk, { taluk = it }, label = "Taluk", modifier = Modifier.weight(1f))
                            MmsInput(district, { district = it }, label = "District", modifier = Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MmsInput(pincode, { pincode = it }, label = "Pincode", modifier = Modifier.weight(1f))
                            MmsInput(state, { state = it }, label = "State", modifier = Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MmsInput(wakf, { wakf = it }, label = "Wakf reg no", modifier = Modifier.weight(1f))
                            MmsInput(society, { society = it }, label = "Society reg no", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                MmsCard {
                    SectionLabel("Finance")
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MmsInput(currency, { currency = it }, label = "Currency symbol", modifier = Modifier.weight(1f))
                            MmsInput(prefix, { prefix = it }, label = "Receipt prefix", modifier = Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MmsInput(monthly, { monthly = it }, label = "Monthly subscription", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            MmsSelect(freq, listOf("Monthly", "Quarterly"), { freq = it }, label = "Frequency", modifier = Modifier.weight(1f))
                        }
                        MmsInput(fy, { fy = it }, label = "Financial year start (MM-DD)", placeholder = "04-01")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.text.BasicText("Auto backup", style = MmsType.bodySm.copy(color = c.tx), modifier = Modifier.weight(1f))
                            FilterChip("On", autoBackup, { autoBackup = true })
                            Spacer(Modifier.width(6.dp))
                            FilterChip("Off", !autoBackup, { autoBackup = false })
                        }
                        MmsInput(backupHours, { backupHours = it }, label = "Backup interval (hours)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        MmsButton("Save settings", { save() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                MmsCard {
                    SectionLabel("Change password")
                    Spacer(Modifier.height(10.dp))
                    if (me == null) {
                        InfoBanner("Sign in to change your password.", "warn")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MmsInput(pwd1, { pwd1 = it }, label = "New password", password = true)
                            MmsInput(pwd2, { pwd2 = it }, label = "Confirm password", password = true)
                            MmsButton("Update password", {
                                scope.launch {
                                    try {
                                        require(pwd1 == pwd2) { "Passwords do not match" }
                                        withContext(Dispatchers.IO) { repo.auth.changePassword(me.id, pwd1) }
                                        pwd1 = ""; pwd2 = ""
                                        toast("Password updated ✓", ToastMsg.Kind.Success)
                                    } catch (e: Exception) {
                                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth(), enabled = pwd1.length >= 8 && pwd1 == pwd2)
                        }
                    }
                }
            }
            item {
                MmsCard {
                    SectionLabel("About")
                    Spacer(Modifier.height(6.dp))
                    DetailRow("App", "Minz Mahallu Management System")
                    DetailRow("Version", "2.0.0 (Android)")
                    DetailRow("Data", "Offline SQLite on this device")
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

// ---------------------------------------------------------------- backup

@Composable
fun BackupScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dir = remember { File(ctx.getExternalFilesDir(null), "backups") }
    var files by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var restoreFile by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var deleteFile by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val c = C()
    val df = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    fun reload() = scope.launch {
        loading = true
        try {
            files = withContext(Dispatchers.IO) { repo.backupList(dir) }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader(I18n.t("nav_backup"), "Local database backups", T()) }
        item {
            MmsButton("Create backup now", {
                scope.launch {
                    busy = true
                    try {
                        val f = withContext(Dispatchers.IO) { repo.backupCreate(dir) }
                        reload(); toast("Backup ${f.name} ✓", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        }
        item {
            InfoBanner(
                "Backups are stored in the app's private folder. Share a copy to Drive or WhatsApp for safekeeping — restoring replaces all current data.",
                "info"
            )
        }
        if (loading) {
            item { LoadingList(3) }
        } else if (files.isEmpty()) {
            item { EmptyState("No backups yet", "Create your first backup above", "Create backup") { create() } }
        } else {
            items(files) { row ->
                MmsCard {
                    CellText(Format.str(row, "name"), strong = true, sub = Format.str(row, "path"))
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.text.BasicText(
                        formatBytes((row["size"] as? Number)?.toLong() ?: 0L) + "  ·  " +
                            df.format(Date((row["modified"] as? Number)?.toLong() ?: 0L)),
                        style = MmsType.caption.copy(color = c.fnt)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MmsButton("Share", {
                            ShareUtil.shareFile(ctx, File(Format.str(row, "path")), "application/octet-stream", "Share backup")
                        }, small = true, primary = false, modifier = Modifier.weight(1f))
                        MmsButton("Restore", { restoreFile = row }, small = true, modifier = Modifier.weight(1f))
                        MmsButton("Delete", { deleteFile = row }, small = true, danger = true, ghost = true, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }

    if (restoreFile != null) {
        val row = restoreFile!!
        ConfirmDialog(
            "Restore backup",
            "Current data will be REPLACED by \"${Format.str(row, "name")}\". This cannot be undone.",
            confirmLabel = "Restore",
            onConfirm = {
                scope.launch {
                    busy = true
                    val ok = withContext(Dispatchers.IO) { repo.backupRestore(File(Format.str(row, "path"))) }
                    busy = false
                    restoreFile = null
                    toast(if (ok) "Restored — restart the app" else "Restore failed", if (ok) ToastMsg.Kind.Success else ToastMsg.Kind.Error)
                }
            },
            onDismiss = { restoreFile = null }
        )
    }

    if (deleteFile != null) {
        val row = deleteFile!!
        ConfirmDialog(
            "Delete backup",
            "Delete \"${Format.str(row, "name")}\" permanently?",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { repo.backupDelete(File(Format.str(row, "path"))) }
                    deleteFile = null
                    if (ok) reload()
                    toast(if (ok) "Backup deleted" else "Delete failed", if (ok) ToastMsg.Kind.Success else ToastMsg.Kind.Error)
                }
            },
            onDismiss = { deleteFile = null }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024; i++
    }
    return "%.1f %s".format(v, units[i])
}
