package com.mms.minzmahallu.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mms.minzmahallu.MmsApp
import com.mms.minzmahallu.data.model.PageResult
import com.mms.minzmahallu.data.model.ToastMsg
import com.mms.minzmahallu.i18n.I18n
import com.mms.minzmahallu.ui.components.*
import com.mms.minzmahallu.ui.theme.*
import com.mms.minzmahallu.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ModuleScaffold(
    title: String,
    subtitle: String,
    filters: List<String> = listOf("All"),
    selectedFilter: String,
    onFilter: (String) -> Unit,
    search: String,
    onSearch: (String) -> Unit,
    onAdd: (() -> Unit)? = null,
    addLabel: String = I18n.t("action_save"),
    extraActions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val c = C()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            PageHeader(title, subtitle, T()) {
                extraActions()
                if (onAdd != null) MmsButton(addLabel, onAdd, small = true)
            }
            SearchField(search, onSearch, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                filters.forEach { f ->
                    FilterChip(f, f == selectedFilter, { onFilter(f) })
                }
            }
        }
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) { content() }
    }
}

@Composable
fun FamiliesScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var showForm by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var houseName by remember { mutableStateOf("") }
    var houseNumber by remember { mutableStateOf("") }
    var ward by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val c = C()

    fun reload() = scope.launch {
        rows = withContext(Dispatchers.IO) { repo.familiesList(search, status).rows }
    }
    LaunchedEffect(search, status) { reload() }

    ModuleScaffold(I18n.t("family_title"), I18n.t("family_subtitle"), listOf("All", "Active", "Inactive", "Archived"), status, { status = it }, search, { search = it }, onAdd = {
        editId = null; houseName = ""; houseNumber = ""; ward = ""; area = ""; phone = ""; address = ""; showForm = true
    }, addLabel = I18n.t("add_family")) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { Format.long(it, "id") }) { row ->
                MmsCard(onClick = {
                    editId = Format.long(row, "id")
                    houseName = Format.str(row, "house_name"); houseNumber = Format.str(row, "house_number")
                    ward = Format.str(row, "ward"); area = Format.str(row, "area")
                    phone = Format.str(row, "phone"); address = Format.str(row, "address")
                    showForm = true
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            CellText(Format.str(row, "house_name").ifBlank { "—" }, strong = true, sub = Format.str(row, "family_number"))
                            androidx.compose.foundation.text.BasicText(
                                listOf(Format.str(row, "ward"), Format.str(row, "area"), Format.str(row, "phone")).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MmsType.caption.copy(color = c.fnt)
                            )
                        }
                        StatusPill(Format.str(row, "status"), Format.str(row, "status"))
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicText("${Format.long(row, "member_count")}", style = MmsType.body.copy(color = c.em, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (showForm) {
        MmsDialog(if (editId == null) I18n.t("add_family") else I18n.t("action_edit"), onDismiss = { showForm = false }, onConfirm = {
            scope.launch {
                try {
                    val data = mapOf("houseName" to houseName, "houseNumber" to houseNumber, "ward" to ward, "area" to area, "phone" to phone, "address" to address, "status" to "Active")
                    withContext(Dispatchers.IO) {
                        if (editId == null) repo.familyCreate(data) else repo.familyUpdate(editId!!, data)
                    }
                    showForm = false; reload(); toast(I18n.t("action_save") + " ✓", ToastMsg.Kind.Success)
                } catch (e: Exception) { toast(e.message ?: "Error", ToastMsg.Kind.Error) }
            }
        }) {
            MmsInput(houseName, { houseName = it }, label = I18n.t("family_house_name"))
            MmsInput(houseNumber, { houseNumber = it }, label = I18n.t("family_house_number"))
            MmsInput(ward, { ward = it }, label = I18n.t("family_ward"))
            MmsInput(area, { area = it }, label = I18n.t("family_area"))
            MmsInput(phone, { phone = it }, label = I18n.t("family_phone"))
            MmsInput(address, { address = it }, label = I18n.t("family_address"), singleLine = false)
        }
    }
}

@Composable
fun MembersScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var families by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var showForm by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var mobile by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Other") }
    var familyId by remember { mutableStateOf(0L) }
    var familyLabel by remember { mutableStateOf("") }
    val c = C()
    fun reload() = scope.launch { rows = withContext(Dispatchers.IO) { repo.membersList(search, status = status).rows } }
    LaunchedEffect(search, status) { reload() }
    LaunchedEffect(Unit) { families = withContext(Dispatchers.IO) { repo.familiesList(status = "Active").rows } }

    ModuleScaffold(I18n.t("member_title"), I18n.t("member_subtitle"), listOf("All", "Active", "Inactive", "Deceased"), status, { status = it }, search, { search = it },
        onAdd = { editId = null; name = ""; gender = "Male"; mobile = ""; relationship = "Other"; familyId = 0; familyLabel = ""; showForm = true },
        addLabel = I18n.t("add_member")) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { Format.long(it, "id") }) { row ->
                MmsCard(onClick = {
                    editId = Format.long(row, "id"); name = Format.str(row, "name"); gender = Format.str(row, "gender")
                    mobile = Format.str(row, "mobile"); relationship = Format.str(row, "relationship")
                    familyId = Format.long(row, "family_id"); familyLabel = Format.str(row, "family_number"); showForm = true
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            CellText(Format.str(row, "name"), strong = true, sub = Format.str(row, "member_code"))
                            androidx.compose.foundation.text.BasicText(
                                listOf(Format.str(row, "family_number"), Format.str(row, "relationship"), Format.str(row, "mobile")).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MmsType.caption.copy(color = c.fnt)
                            )
                        }
                        StatusPill(Format.str(row, "status"), Format.str(row, "status"))
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (showForm) {
        MmsDialog(if (editId == null) I18n.t("add_member") else I18n.t("action_edit"), onDismiss = { showForm = false }, onConfirm = {
            scope.launch {
                try {
                    val data = mapOf("name" to name, "gender" to gender, "mobile" to mobile, "relationship" to relationship, "familyId" to familyId, "status" to "Active")
                    withContext(Dispatchers.IO) { if (editId == null) repo.memberCreate(data) else repo.memberUpdate(editId!!, data) }
                    showForm = false; reload(); toast("Saved", ToastMsg.Kind.Success)
                } catch (e: Exception) { toast(e.message ?: "Error", ToastMsg.Kind.Error) }
            }
        }, confirmEnabled = name.isNotBlank() && familyId > 0) {
            MmsInput(name, { name = it }, label = I18n.t("member_name"))
            MmsSelect(gender, listOf("Male", "Female", "Other"), { gender = it }, label = I18n.t("member_gender"))
            MmsSelect(relationship, repo.memberRelationships(), { relationship = it }, label = I18n.t("member_relationship"))
            MmsInput(mobile, { mobile = it }, label = I18n.t("member_mobile"))
            MmsSelect(
                familyLabel.ifBlank { "Select family" },
                families.map { "${Format.str(it, "family_number")} — ${Format.str(it, "house_name")}" },
                { label ->
                    familyLabel = label
                    val code = label.substringBefore(" —")
                    familyId = families.firstOrNull { Format.str(it, "family_number") == code }?.let { Format.long(it, "id") } ?: 0
                },
                label = I18n.t("member_family")
            )
        }
    }
}

@Composable
fun SubscriptionsScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var payId by remember { mutableStateOf<Long?>(null) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Cash") }
    var collected by remember { mutableStateOf(0.0) }
    var pending by remember { mutableStateOf(0.0) }
    val c = C()
    fun reload() = scope.launch {
        withContext(Dispatchers.IO) {
            rows = repo.subscriptionsList(search, status).rows
            collected = repo.subscriptionsTotalCollected()
            pending = repo.subscriptionsTotalPending()
        }
    }
    LaunchedEffect(search, status) { reload() }
    ModuleScaffold(I18n.t("sub_title"), "Household subscription accounts", listOf("All", "Paid", "Pending", "Overdue", "Partial"), status, { status = it }, search, { search = it },
        extraActions = {
            MmsButton(I18n.t("sub_mark_overdue"), {
                scope.launch { withContext(Dispatchers.IO) { repo.markOverdue() }; reload(); toast("Marked overdue", ToastMsg.Kind.Info) }
            }, small = true, primary = false)
        }) {
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Collected", Format.moneyShort(collected), Tints.of("em", c.isDark), modifier = Modifier.weight(1f))
                StatTile("Pending", Format.moneyShort(pending), Tints.of("gold", c.isDark), modifier = Modifier.weight(1f))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { Format.long(it, "id") }) { row ->
                    MmsCard(onClick = {
                        payId = Format.long(row, "id")
                        amount = Format.num(row, "amount").toString()
                        method = "Cash"
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                CellText(Format.str(row, "house_name").ifBlank { Format.str(row, "family_number") }, strong = true, sub = Format.str(row, "family_number"))
                                androidx.compose.foundation.text.BasicText(
                                    "${Format.money(Format.num(row, "amount_paid"))} / ${Format.money(Format.num(row, "amount"))}",
                                    style = MmsType.caption.copy(color = c.mut)
                                )
                            }
                            StatusPill(Format.str(row, "status"), Format.str(row, "status"))
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    if (payId != null) {
        MmsDialog("Record Payment", onDismiss = { payId = null }, confirmLabel = "Collect", onConfirm = {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repo.subscriptionPay(payId!!, amount.toDoubleOrNull() ?: 0.0, method, Format.today())
                    }
                    payId = null; reload(); toast("Payment recorded", ToastMsg.Kind.Success)
                } catch (e: Exception) { toast(e.message ?: "Error", ToastMsg.Kind.Error) }
            }
        }) {
            MmsInput(amount, { amount = it }, label = I18n.t("sub_amount"))
            MmsSelect(method, listOf("Cash", "UPI", "Bank Transfer", "Cheque", "Card", "Other"), { method = it }, label = I18n.t("sub_method"))
        }
    }
}

@Composable
fun DonationsScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var cats by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var show by remember { mutableStateOf(false) }
    var donor by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf(0L) }
    var catLabel by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Cash") }
    var total by remember { mutableStateOf(0.0) }
    val c = C()
    fun reload() = scope.launch {
        withContext(Dispatchers.IO) {
            rows = repo.donationsList(search).rows
            cats = repo.donationCategories()
            total = repo.donationsTotalThisMonth()
            if (catId == 0L && cats.isNotEmpty()) {
                catId = Format.long(cats.first(), "id"); catLabel = Format.str(cats.first(), "name")
            }
        }
    }
    LaunchedEffect(search) { reload() }
    ModuleScaffold(I18n.t("don_title"), I18n.t("don_subtitle"), listOf("All"), "All", {}, search, { search = it },
        onAdd = { donor = ""; amount = ""; method = "Cash"; show = true }, addLabel = I18n.t("dash_qa_add_donation")) {
        Column {
            StatTile(I18n.t("dash_donations_month"), Format.money(total), Tints.of("pink", c.isDark), I18n.t("dash_this_month"), Modifier.fillMaxWidth().padding(bottom = 10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { Format.long(it, "id") }) { row ->
                    MmsCard {
                        Row {
                            Column(Modifier.weight(1f)) {
                                CellText(Format.str(row, "donor_name"), strong = true, sub = Format.str(row, "receipt_number"))
                                androidx.compose.foundation.text.BasicText(Format.str(row, "category_name") + " · " + Format.str(row, "donation_date"), style = MmsType.caption.copy(color = c.fnt))
                            }
                            androidx.compose.foundation.text.BasicText(Format.money(Format.num(row, "amount")), style = MmsType.body.copy(color = c.em, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    if (show) {
        MmsDialog(I18n.t("dash_qa_add_donation"), onDismiss = { show = false }, onConfirm = {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repo.donationCreate(mapOf("donorName" to donor, "amount" to (amount.toDoubleOrNull() ?: 0.0), "categoryId" to catId, "paymentMethod" to method, "donationDate" to Format.today()))
                    }
                    show = false; reload(); toast("Donation saved", ToastMsg.Kind.Success)
                } catch (e: Exception) { toast(e.message ?: "Error", ToastMsg.Kind.Error) }
            }
        }, confirmEnabled = donor.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0 && catId > 0) {
            MmsInput(donor, { donor = it }, label = "Donor name")
            MmsInput(amount, { amount = it }, label = I18n.t("sub_amount"))
            MmsSelect(catLabel.ifBlank { "Category" }, cats.map { Format.str(it, "name") }, {
                catLabel = it; catId = cats.firstOrNull { c -> Format.str(c, "name") == it }?.let { Format.long(it, "id") } ?: 0
            }, label = "Category")
            MmsSelect(method, listOf("Cash", "UPI", "Bank Transfer", "Cheque", "Card", "Other"), { method = it }, label = I18n.t("sub_method"))
        }
    }
}

@Composable
fun AccountingScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var income by remember { mutableStateOf(0.0) }
    var expense by remember { mutableStateOf(0.0) }
    var balance by remember { mutableStateOf(0.0) }
    var show by remember { mutableStateOf(false) }
    var txnType by remember { mutableStateOf("Expense") }
    var amount by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Cash") }
    val c = C()
    fun reload() = scope.launch {
        withContext(Dispatchers.IO) {
            rows = repo.accountingList(search, type).rows
            income = repo.accountingTotalIncome(); expense = repo.accountingTotalExpense(); balance = repo.accountingBalance()
        }
    }
    LaunchedEffect(search, type) { reload() }
    ModuleScaffold("Accounting", "Income, expenses & ledger", listOf("All", "Income", "Expense"), type, { type = it }, search, { search = it },
        onAdd = { amount = ""; desc = ""; txnType = if (type == "Income") "Income" else "Expense"; show = true }, addLabel = "Add entry") {
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Income", Format.moneyShort(income), Tints.of("em", c.isDark), modifier = Modifier.weight(1f))
                StatTile("Expense", Format.moneyShort(expense), Tints.of("rose", c.isDark), modifier = Modifier.weight(1f))
                StatTile("Balance", Format.moneyShort(balance), Tints.of("sky", c.isDark), modifier = Modifier.weight(1f))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { Format.long(it, "id") }) { row ->
                    MmsCard {
                        Row {
                            Column(Modifier.weight(1f)) {
                                CellText(Format.str(row, "description").ifBlank { Format.str(row, "receipt_number") }, strong = true, sub = Format.str(row, "txn_date") + " · " + Format.str(row, "receipt_number"))
                            }
                            val isInc = Format.str(row, "type") == "Income"
                            androidx.compose.foundation.text.BasicText(
                                (if (isInc) "+" else "−") + Format.money(Format.num(row, "amount")),
                                style = MmsType.body.copy(color = if (isInc) c.em else c.cRose, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    if (show) {
        MmsDialog("New transaction", onDismiss = { show = false }, onConfirm = {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repo.accountingCreate(mapOf("type" to txnType, "amount" to (amount.toDoubleOrNull() ?: 0.0), "description" to desc, "paymentMethod" to method, "txnDate" to Format.today()))
                    }
                    show = false; reload(); toast("Entry saved", ToastMsg.Kind.Success)
                } catch (e: Exception) { toast(e.message ?: "Error", ToastMsg.Kind.Error) }
            }
        }) {
            MmsSelect(txnType, listOf("Income", "Expense"), { txnType = it }, label = "Type")
            MmsInput(amount, { amount = it }, label = "Amount")
            MmsInput(desc, { desc = it }, label = "Description")
            MmsSelect(method, listOf("Cash", "UPI", "Bank Transfer", "Cheque", "Card", "Other"), { method = it }, label = "Method")
        }
    }
}

@Composable
fun GenericCrudScreen(
    title: String, subtitle: String,
    filters: List<String> = listOf("All"),
    load: suspend (String, String) -> List<Map<String, Any?>>,
    titleOf: (Map<String, Any?>) -> String,
    subOf: (Map<String, Any?>) -> String,
    statusOf: (Map<String, Any?>) -> String = { "" },
    amountOf: (Map<String, Any?>) -> String? = { null },
    addLabel: String = "Add",
    formFields: List<Pair<String, String>>, // label to key
    onSave: suspend (Map<String, String>, Long?) -> Unit,
    toast: (String, ToastMsg.Kind) -> Unit,
    prefill: (Map<String, Any?>) -> Map<String, String> = { emptyMap() },
) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(filters.first()) }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var show by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var fields by remember { mutableStateOf(formFields.associate { it.second to "" }.toMutableMap()) }
    val c = C()
    fun reload() = scope.launch { rows = withContext(Dispatchers.IO) { load(search, filter) } }
    LaunchedEffect(search, filter) { reload() }
    ModuleScaffold(title, subtitle, filters, filter, { filter = it }, search, { search = it }, onAdd = {
        editId = null; fields = formFields.associate { it.second to "" }.toMutableMap(); show = true
    }, addLabel = addLabel) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { Format.long(it, "id") }) { row ->
                MmsCard(onClick = {
                    editId = Format.long(row, "id")
                    val m = prefill(row).toMutableMap()
                    formFields.forEach { (_, key) ->
                        val snake = key.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
                        if (m[key].isNullOrBlank()) m[key] = Format.str(row, snake).ifBlank { Format.str(row, key) }
                    }
                    fields = m
                    show = true
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            CellText(titleOf(row), strong = true, sub = subOf(row))
                        }
                        val st = statusOf(row)
                        if (st.isNotBlank()) StatusPill(st, st)
                        amountOf(row)?.let {
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.foundation.text.BasicText(it, style = MmsType.body.copy(color = c.em, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (show) {
        MmsDialog(if (editId == null) addLabel else I18n.t("action_edit"), onDismiss = { show = false }, onConfirm = {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { onSave(fields.toMap(), editId) }
                    show = false; reload(); toast("Saved", ToastMsg.Kind.Success)
                } catch (e: Exception) { toast(e.message ?: "Error", ToastMsg.Kind.Error) }
            }
        }) {
            formFields.forEach { (label, key) ->
                MmsInput(fields[key] ?: "", { fields = fields.toMutableMap().also { m -> m[key] = it } }, label = label)
            }
        }
    }
}

@Composable fun StaffScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    GenericCrudScreen("Staff", "Employees & salary", listOf("All", "Active", "Resigned"),
        load = { s, st -> repo.staffList(s, st).rows },
        titleOf = { Format.str(it, "name") }, subOf = { Format.str(it, "staff_code") + " · " + Format.str(it, "role") },
        statusOf = { Format.str(it, "status") },
        addLabel = "Add staff",
        formFields = listOf("Name" to "name", "Role" to "role", "Phone" to "phone", "Salary" to "salary"),
        onSave = { f, id ->
            val data = mapOf("name" to f["name"], "role" to (f["role"] ?: "Other"), "phone" to f["phone"], "salary" to (f["salary"]?.toDoubleOrNull() ?: 0.0))
            if (id == null) repo.staffCreate(data) else repo.staffUpdate(id, data)
        }, toast = toast)
}

@Composable fun CommitteeScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    GenericCrudScreen("Committee", "Elected & nominated members", listOf("All", "Active", "Past"),
        load = { s, st -> repo.committeeList(s, st).rows },
        titleOf = { Format.str(it, "name") }, subOf = { Format.str(it, "committee_code") + " · " + Format.str(it, "position") },
        statusOf = { Format.str(it, "status") },
        addLabel = "Add member",
        formFields = listOf("Name" to "name", "Position" to "position", "Type" to "committeeType", "Phone" to "phone"),
        onSave = { f, id ->
            val data = mapOf("name" to f["name"], "position" to (f["position"] ?: "Committee Member"), "committeeType" to (f["committeeType"] ?: "Executive"), "phone" to f["phone"])
            if (id == null) repo.committeeCreate(data) else repo.committeeUpdate(id, data)
        }, toast = toast)
}

@Composable fun MarriagesScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    GenericCrudScreen(I18n.t("nav_marriage"), "Nikah register",
        load = { s, _ -> repo.marriagesList(s).rows },
        titleOf = { "${Format.str(it,"groom_name")}  ♥  ${Format.str(it,"bride_name")}" },
        subOf = { Format.str(it, "marriage_number") + " · " + Format.str(it, "nikah_date") },
        addLabel = "Add marriage",
        formFields = listOf("Groom" to "groomName", "Groom father" to "groomFather", "Bride" to "brideName", "Bride father" to "brideFather", "Nikah date" to "nikahDate", "Place" to "place", "Mahar" to "mahar"),
        onSave = { f, id ->
            val data = f.mapValues { it.value as Any? } + mapOf("nikahDate" to (f["nikahDate"] ?: Format.today()))
            if (id == null) repo.marriageCreate(data) else repo.marriageUpdate(id, data)
        }, toast = toast)
}

@Composable fun DeathsScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    GenericCrudScreen(I18n.t("nav_death"), "Death register",
        load = { s, _ -> repo.deathsList(s).rows },
        titleOf = { Format.str(it, "deceased_name") },
        subOf = { Format.str(it, "death_number") + " · " + Format.str(it, "date_of_death") },
        addLabel = "Add record",
        formFields = listOf("Name" to "deceasedName", "Father" to "fatherName", "Date of death" to "dateOfDeath", "Gender" to "gender", "Burial place" to "burialPlace", "Cause" to "causeOfDeath"),
        onSave = { f, id ->
            val data = f.mapValues { it.value as Any? } + mapOf("dateOfDeath" to (f["dateOfDeath"] ?: Format.today()), "gender" to (f["gender"] ?: "Male"))
            if (id == null) repo.deathCreate(data) else repo.deathUpdate(id, data)
        }, toast = toast)
}

@Composable fun WelfareScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var show by remember { mutableStateOf(false) }
    var applicant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Financial Assistance") }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var actionId by remember { mutableStateOf<Long?>(null) }
    var actionKind by remember { mutableStateOf("") }
    var actionAmount by remember { mutableStateOf("") }
    var actionReason by remember { mutableStateOf("") }
    var adminPwd by remember { mutableStateOf("") }
    val c = C()
    fun reload() = scope.launch { rows = withContext(Dispatchers.IO) { repo.welfareList(search, status).rows } }
    LaunchedEffect(search, status) { reload() }
    ModuleScaffold(I18n.t("nav_welfare"), "Aid requests & disbursements", listOf("All", "Pending", "Approved", "Rejected", "Disbursed"), status, { status = it }, search, { search = it },
        onAdd = { applicant = ""; amount = ""; reason = ""; category = "Financial Assistance"; show = true }, addLabel = "New request") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { Format.long(it, "id") }) { row ->
                MmsCard {
                    Row {
                        Column(Modifier.weight(1f)) {
                            CellText(Format.str(row, "applicant_name"), strong = true, sub = Format.str(row, "request_number") + " · " + Format.str(row, "category"))
                            androidx.compose.foundation.text.BasicText(Format.money(Format.num(row, "amount_requested")), style = MmsType.bodySm.copy(color = c.mut))
                        }
                        StatusPill(Format.str(row, "status"), Format.str(row, "status"))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val st = Format.str(row, "status")
                        if (st == "Pending") {
                            MmsButton("Approve", { actionId = Format.long(row, "id"); actionKind = "approve"; actionAmount = Format.num(row, "amount_requested").toString(); actionReason = "" }, small = true)
                            MmsButton("Reject", { actionId = Format.long(row, "id"); actionKind = "reject"; actionReason = "" }, small = true, danger = true, ghost = true)
                        }
                        if (st == "Approved") {
                            MmsButton("Disburse", { actionId = Format.long(row, "id"); actionKind = "disburse"; adminPwd = ""; actionReason = "" }, small = true)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (show) {
        MmsDialog("Welfare request", onDismiss = { show = false }, onConfirm = {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repo.welfareCreate(mapOf("applicantName" to applicant, "category" to category, "amountRequested" to (amount.toDoubleOrNull() ?: 0.0), "reason" to reason))
                    }
                    show = false; reload(); toast("Created", ToastMsg.Kind.Success)
                } catch (e: Exception) { toast(e.message ?: "Error", ToastMsg.Kind.Error) }
            }
        }) {
            MmsInput(applicant, { applicant = it }, label = "Applicant")
            MmsSelect(category, repo.welfareCategories(), { category = it }, label = "Category")
            MmsInput(amount, { amount = it }, label = "Amount requested")
            MmsInput(reason, { reason = it }, label = "Reason", singleLine = false)
        }
    }
    if (actionId != null) {
        MmsDialog(
            actionKind.replaceFirstChar { it.uppercase() },
            onDismiss = { actionId = null },
            confirmLabel = actionKind.replaceFirstChar { it.uppercase() },
            danger = actionKind == "reject",
            onConfirm = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            when (actionKind) {
                                "approve" -> repo.welfareApprove(actionId!!, actionAmount.toDoubleOrNull() ?: 0.0, actionReason, Format.today())
                                "reject" -> repo.welfareReject(actionId!!, actionReason)
                                "disburse" -> repo.welfareDisburse(actionId!!, actionReason, adminPwd)
                                else -> Unit
                            }
                        }
                        actionId = null; reload(); toast("Done", ToastMsg.Kind.Success)
                    } catch (e: Exception) { toast(e.message ?: "Error", ToastMsg.Kind.Error) }
                }
            },
            compact = true
        ) {
            if (actionKind == "approve") MmsInput(actionAmount, { actionAmount = it }, label = "Approved amount")
            if (actionKind == "disburse") MmsInput(adminPwd, { adminPwd = it }, label = "Admin password", password = true)
            MmsInput(actionReason, { actionReason = it }, label = "Reason / notes", singleLine = false)
        }
    }
}

@Composable fun CertificatesScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var show by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("Membership") }
    var issuedTo by remember { mutableStateOf("") }
    val c = C()
    fun reload() = scope.launch { rows = withContext(Dispatchers.IO) { repo.certificatesList(search).rows } }
    LaunchedEffect(search) { reload() }
    ModuleScaffold(I18n.t("nav_certificates"), "Issue & verify certificates", listOf("All"), "All", {}, search, { search = it },
        onAdd = { type = "Membership"; issuedTo = ""; show = true }, addLabel = "Issue") {
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Membership", "Residence", "Marriage", "Death").forEach { t ->
                    MmsCard(modifier = Modifier.weight(1f), onClick = { type = t; issuedTo = ""; show = true }) {
                        androidx.compose.foundation.text.BasicText(t, style = MmsType.bodySm.copy(color = c.tx, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                    }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { Format.long(it, "id") }) { row ->
                    MmsCard {
                        CellText(Format.str(row, "issued_to"), strong = true, sub = Format.str(row, "certificate_number") + " · " + Format.str(row, "type"))
                        androidx.compose.foundation.text.BasicText("Verify: " + Format.str(row, "verification_code"), style = MmsType.caption.copy(color = c.em))
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    if (show) {
        MmsDialog("Issue $type certificate", onDismiss = { show = false }, onConfirm = {
            scope.launch {
                try {
                    val r = withContext(Dispatchers.IO) { repo.certificateIssue(type, issuedTo) }
                    show = false; reload(); toast("Issued ${r["certificateNumber"]}", ToastMsg.Kind.Success)
                } catch (e: Exception) { toast(e.message ?: "Error", ToastMsg.Kind.Error) }
            }
        }) {
            MmsSelect(type, listOf("Membership", "Residence", "Marriage", "Death", "Character", "Income"), { type = it }, label = "Type")
            MmsInput(issuedTo, { issuedTo = it }, label = "Issued to")
        }
    }
}

@Composable fun TokensScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var tokens by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var stats by remember { mutableStateOf(mapOf<String, Any?>()) }
    var showEvent by remember { mutableStateOf(false) }
    var eventName by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(Format.today()) }
    val c = C()
    fun reloadEvents() = scope.launch { events = withContext(Dispatchers.IO) { repo.tokenEventsList() } }
    fun reloadTokens() = scope.launch {
        val id = selected ?: return@launch
        withContext(Dispatchers.IO) {
            tokens = repo.tokensList(id)
            stats = repo.tokenStats(id)
        }
    }
    LaunchedEffect(Unit) { reloadEvents() }
    LaunchedEffect(selected) { reloadTokens() }

    if (selected == null) {
        ModuleScaffold(I18n.t("nav_tokens"), "Token events & collection", onAdd = { eventName = ""; eventDate = Format.today(); showEvent = true }, addLabel = "New event",
            filters = listOf("All"), selectedFilter = "All", onFilter = {}, search = "", onSearch = {}) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events, key = { Format.long(it, "id") }) { row ->
                    MmsCard(onClick = { selected = Format.long(row, "id") }) {
                        CellText(Format.str(row, "name"), strong = true, sub = Format.str(row, "event_date") + " · " + Format.long(row, "token_count") + " tokens")
                    }
                }
            }
        }
        if (showEvent) {
            MmsDialog("New token event", onDismiss = { showEvent = false }, onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.tokenEventCreate(mapOf("name" to eventName, "eventDate" to eventDate)) }
                    showEvent = false; reloadEvents(); toast("Event created", ToastMsg.Kind.Success)
                }
            }) {
                MmsInput(eventName, { eventName = it }, label = "Event name")
                MmsInput(eventDate, { eventDate = it }, label = "Date (YYYY-MM-DD)")
            }
        }
    } else {
        ModuleScaffold("Tokens", "Collect & manage",
            extraActions = {
                MmsButton("Generate", {
                    scope.launch {
                        val n = withContext(Dispatchers.IO) {
                            val fams = repo.familiesList(status = "Active").rows.map { Format.long(it, "id") }
                            repo.tokensGenerate(selected!!, fams)
                        }
                        reloadTokens(); toast("Generated $n tokens", ToastMsg.Kind.Success)
                    }
                }, small = true)
                MmsButton("Back", { selected = null }, small = true, primary = false)
            },
            filters = listOf("All"), selectedFilter = "All", onFilter = {}, search = "", onSearch = {}) {
            Column {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Total", (stats["total"] ?: 0).toString(), Tints.of("pink", c.isDark), modifier = Modifier.weight(1f))
                    StatTile("Collected", (stats["collected"] ?: 0).toString(), Tints.of("em", c.isDark), modifier = Modifier.weight(1f))
                    StatTile("Pending", (stats["pending"] ?: 0).toString(), Tints.of("gold", c.isDark), modifier = Modifier.weight(1f))
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tokens, key = { Format.long(it, "id") }) { row ->
                        val st = Format.str(row, "status")
                        MmsCard(onClick = {
                            if (st == "Pending") scope.launch {
                                withContext(Dispatchers.IO) { repo.tokenCollect(Format.long(row, "id")) }
                                reloadTokens(); toast("Collected", ToastMsg.Kind.Success)
                            }
                        }) {
                            Row {
                                Column(Modifier.weight(1f)) {
                                    CellText(Format.str(row, "token_code"), strong = true, sub = Format.str(row, "family_number") + " " + Format.str(row, "house_name"))
                                }
                                StatusPill(st, st)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable fun AssetsScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    GenericCrudScreen(I18n.t("nav_assets"), "Buildings, land & rentable goods", listOf("All") + repo.assetCategories().take(4),
        load = { s, cat -> repo.assetsList(s, if (cat == "All") null else cat).rows },
        titleOf = { Format.str(it, "name") }, subOf = { Format.str(it, "asset_code") + " · " + Format.str(it, "category") },
        statusOf = { Format.str(it, "status") },
        amountOf = { Format.moneyShort(Format.num(it, "current_value")) },
        addLabel = "Add asset",
        formFields = listOf("Name" to "name", "Category" to "category", "Location" to "location", "Current value" to "currentValue", "Status" to "status"),
        onSave = { f, id ->
            val data = mapOf("name" to f["name"], "category" to (f["category"] ?: "Other"), "location" to f["location"], "currentValue" to (f["currentValue"]?.toDoubleOrNull() ?: 0.0), "status" to (f["status"] ?: "In use"))
            if (id == null) repo.assetCreate(data) else repo.assetUpdate(id, data)
        }, toast = toast)
}

@Composable fun UsersScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var show by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Staff") }
    val c = C()
    fun reload() = scope.launch { rows = withContext(Dispatchers.IO) { repo.usersList() } }
    LaunchedEffect(Unit) { reload() }
    ModuleScaffold(I18n.t("nav_users"), "User accounts & roles", onAdd = { username = ""; fullName = ""; password = ""; role = "Staff"; show = true }, addLabel = "Add user",
        filters = listOf("All"), selectedFilter = "All", onFilter = {}, search = "", onSearch = {}) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { Format.long(it, "id") }) { row ->
                MmsCard {
                    Row {
                        Column(Modifier.weight(1f)) {
                            CellText(Format.str(row, "full_name"), strong = true, sub = "@" + Format.str(row, "username") + " · " + Format.str(row, "role"))
                        }
                        StatusPill(if (Format.long(row, "is_active") == 1L) "Active" else "Inactive", if (Format.long(row, "is_active") == 1L) "Active" else "Rejected")
                    }
                }
            }
        }
    }
    if (show) {
        MmsDialog("New user", onDismiss = { show = false }, onConfirm = {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { repo.userCreate(mapOf("username" to username, "fullName" to fullName, "password" to password, "role" to role)) }
                    show = false; reload(); toast("User created", ToastMsg.Kind.Success)
                } catch (e: Exception) { toast(e.message ?: "Error", ToastMsg.Kind.Error) }
            }
        }) {
            MmsInput(username, { username = it }, label = "Username")
            MmsInput(fullName, { fullName = it }, label = "Full name")
            MmsSelect(role, listOf("Administrator", "President", "Secretary", "Treasurer", "Imam", "Staff", "Auditor"), { role = it }, label = "Role")
            MmsInput(password, { password = it }, label = "Password", password = true)
        }
    }
}

@Composable fun AuditScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    val c = C()
    LaunchedEffect(search) { rows = withContext(Dispatchers.IO) { repo.auditList(search).rows } }
    ModuleScaffold(I18n.t("nav_audit"), "Tamper-evident activity log", filters = listOf("All"), selectedFilter = "All", onFilter = {}, search = search, onSearch = { search = it }) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { Format.long(it, "id") }) { row ->
                MmsCard {
                    CellText(Format.str(row, "action") + " · " + Format.str(row, "module"), strong = true, sub = Format.str(row, "description"))
                    androidx.compose.foundation.text.BasicText(
                        Format.str(row, "username") + " · " + Format.str(row, "created_at"),
                        style = MmsType.caption.copy(color = c.fnt)
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable fun SettingsScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("₹") }
    var prefix by remember { mutableStateOf("RCP") }
    var monthly by remember { mutableStateOf("100") }
    val c = C()
    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) { repo.settingsLoad() }
        name = Format.str(s, "mahallu_name"); address = Format.str(s, "address")
        phone = Format.str(s, "phone"); email = Format.str(s, "email")
        currency = Format.str(s, "currency_symbol").ifBlank { "₹" }
        prefix = Format.str(s, "receipt_prefix").ifBlank { "RCP" }
        monthly = Format.num(s, "subscription_monthly_amount").toString()
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader(I18n.t("nav_settings"), "Mahallu profile & preferences", T()) }
        item {
            MmsCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MmsInput(name, { name = it }, label = "Mahallu name")
                    MmsInput(address, { address = it }, label = "Address", singleLine = false)
                    MmsInput(phone, { phone = it }, label = "Phone")
                    MmsInput(email, { email = it }, label = "Email")
                    MmsInput(currency, { currency = it }, label = "Currency symbol")
                    MmsInput(prefix, { prefix = it }, label = "Receipt prefix")
                    MmsInput(monthly, { monthly = it }, label = "Monthly subscription amount")
                    MmsButton("Save settings", {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    repo.settingsSave(mapOf(
                                        "mahalluName" to name, "address" to address, "phone" to phone, "email" to email,
                                        "currencySymbol" to currency, "receiptPrefix" to prefix,
                                        "subscriptionMonthlyAmount" to (monthly.toDoubleOrNull() ?: 100.0)
                                    ))
                                }
                                toast("Settings saved", ToastMsg.Kind.Success)
                            } catch (e: Exception) { toast(e.message ?: "Error", ToastMsg.Kind.Error) }
                        }
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable fun BackupScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dir = remember { File(ctx.getExternalFilesDir(null), "backups") }
    var files by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    fun reload() = scope.launch { files = withContext(Dispatchers.IO) { repo.backupList(dir) } }
    LaunchedEffect(Unit) { reload() }
    val c = C()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader(I18n.t("nav_backup"), "Local encrypted-ready database backups", T()) }
        item {
            MmsButton("Create backup now", {
                scope.launch {
                    try {
                        val f = withContext(Dispatchers.IO) { repo.backupCreate(dir) }
                        reload(); toast("Backup ${f.name}", ToastMsg.Kind.Success)
                    } catch (e: Exception) { toast(e.message ?: "Error", ToastMsg.Kind.Error) }
                }
            }, modifier = Modifier.fillMaxWidth())
        }
        items(files) { row ->
            MmsCard {
                CellText(Format.str(row, "name"), strong = true, sub = Format.str(row, "path"))
                Spacer(Modifier.height(8.dp))
                MmsButton("Restore", {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { repo.backupRestore(File(Format.str(row, "path"))) }
                        toast(if (ok) "Restored" else "Restore failed", if (ok) ToastMsg.Kind.Success else ToastMsg.Kind.Error)
                    }
                }, small = true, danger = true, ghost = true)
            }
        }
    }
}

@Composable fun ReportsScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val c = C()
    val reports = listOf(
        Triple("Family directory", "All active families with member counts", "em"),
        Triple("Member roll", "Complete member listing", "teal"),
        Triple("Defaulters", "Pending & overdue subscriptions", "rose"),
        Triple("Donation summary", "Month / year donation totals", "pink"),
        Triple("Income & expense", "Financial year P&L", "sky"),
        Triple("Marriage register", "Chronological nikah list", "vio"),
        Triple("Death register", "Chronological death list", "slate"),
        Triple("Welfare register", "Aid disbursed this year", "orange"),
        Triple("Certificate log", "Issued certificates", "cyan"),
    )
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeader(I18n.t("nav_reports"), "Printable registers & summaries", T()) }
        items(reports) { (title, desc, tint) ->
            val t = Tints.of(tint, c.isDark)
            MmsCard(onClick = { toast("$title — open from desktop for PDF export; data is live in modules", ToastMsg.Kind.Info) }) {
                androidx.compose.foundation.text.BasicText(title, style = MmsType.headline.copy(color = c.tx, fontSize = 15.sp))
                androidx.compose.foundation.text.BasicText(desc, style = MmsType.caption.copy(color = c.mut))
            }
        }
    }
}

@Composable fun WhatsAppScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val c = C()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader(I18n.t("nav_whatsapp"), "Receipt delivery & announcements", T()) }
        item {
            MmsCard {
                androidx.compose.foundation.text.BasicText("WhatsApp linking", style = MmsType.headline.copy(color = c.tx))
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicText(
                    "The desktop app pairs via Baileys QR for receipt delivery. On Android, use the share sheet from donation/subscription detail to send receipts through the system WhatsApp app. Family WhatsApp numbers are stored in each family record.",
                    style = MmsType.bodySm.copy(color = c.mut)
                )
                Spacer(Modifier.height(12.dp))
                StatusPill("Mobile share mode", "Active")
            }
        }
        item {
            MmsCard {
                androidx.compose.foundation.text.BasicText("How to send a receipt", style = MmsType.headline.copy(color = c.tx))
                listOf(
                    "1. Open Donations or Subscriptions",
                    "2. Record a payment (receipt number auto-assigned)",
                    "3. Use Android Share to send via WhatsApp",
                    "4. Family whatsapp_phone is used when set",
                ).forEach {
                    androidx.compose.foundation.text.BasicText(it, style = MmsType.bodySm.copy(color = c.mut), modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}
