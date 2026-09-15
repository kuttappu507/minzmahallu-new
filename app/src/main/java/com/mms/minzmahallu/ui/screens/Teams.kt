package com.mms.minzmahallu.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mms.minzmahallu.MmsApp
import com.mms.minzmahallu.data.model.ToastMsg
import com.mms.minzmahallu.i18n.I18n
import com.mms.minzmahallu.ui.components.*
import com.mms.minzmahallu.ui.theme.*
import com.mms.minzmahallu.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- staff

@Composable
fun StaffScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var show by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var detail by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var payRow by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var historyRow by remember { mutableStateOf<Map<String, Any?>?>(null) }
    // form
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Other") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var joined by remember { mutableStateOf(Format.today()) }
    var salary by remember { mutableStateOf("") }
    var freq by remember { mutableStateOf("Monthly") }
    var memStatus by remember { mutableStateOf("Active") }
    var notes by remember { mutableStateOf("") }
    // salary form
    var payAmount by remember { mutableStateOf("") }
    var payDate by remember { mutableStateOf(Format.today()) }
    var payMethod by remember { mutableStateOf("Cash") }
    var payRemarks by remember { mutableStateOf("") }
    val c = C()

    fun reload() = scope.launch {
        loading = true
        try {
            rows = withContext(Dispatchers.IO) { repo.staffList(search, status).rows }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search, status) { reload() }

    fun openForm(row: Map<String, Any?>?) {
        editId = row?.let { Format.long(it, "id") }
        name = row?.let { Format.str(it, "name") } ?: ""
        role = row?.let { Format.str(it, "role") }?.ifBlank { "Other" } ?: "Other"
        phone = row?.let { Format.str(it, "phone") } ?: ""
        email = row?.let { Format.str(it, "email") } ?: ""
        address = row?.let { Format.str(it, "address") } ?: ""
        joined = row?.let { Format.str(it, "joined_date") }?.ifBlank { Format.today() } ?: Format.today()
        salary = row?.let { Format.num(it, "salary").let { s -> if (s > 0) s.toString() else "" } } ?: ""
        freq = row?.let { Format.str(it, "payment_frequency") }?.ifBlank { "Monthly" } ?: "Monthly"
        memStatus = row?.let { Format.str(it, "status") }?.ifBlank { "Active" } ?: "Active"
        notes = row?.let { Format.str(it, "notes") } ?: ""
        show = true
    }

    ModuleScaffold(
        I18n.t("nav_staff"), "Employees & salary",
        listOf("All", "Active", "Resigned"), status, { status = it },
        search, { search = it }, loading = loading,
        onAdd = { openForm(null) }, addLabel = "Add staff"
    ) {
        if (rows.isEmpty()) {
            EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "", "Add staff") { openForm(null) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { Format.long(it, "id") }) { row ->
                    MmsCard(onClick = { detail = row }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TintTile(Format.str(row, "name").ifBlank { "S" }.take(1), Tints.of("vio", c.isDark), 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                CellText(Format.str(row, "name"), strong = true, sub = Format.str(row, "staff_code") + " · " + Format.str(row, "role"))
                                androidx.compose.foundation.text.BasicText(
                                    Format.money(Format.num(row, "salary")) + " / " + Format.str(row, "payment_frequency").ifBlank { "Monthly" },
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

    if (detail != null) {
        val row = detail!!
        MmsDialog(Format.str(row, "name"), onDismiss = { detail = null }) {
            DetailRow("Code", Format.str(row, "staff_code"), mono = true)
            DetailRow("Role", Format.str(row, "role"))
            DetailRow("Phone", Format.str(row, "phone"))
            DetailRow("Email", Format.str(row, "email"))
            DetailRow("Joined", Format.str(row, "joined_date"))
            DetailRow("Salary", Format.money(Format.num(row, "salary")), strong = true)
            DetailRow("Frequency", Format.str(row, "payment_frequency"))
            DetailRow("Status", Format.str(row, "status"), strong = true)
            DetailRow("Notes", Format.str(row, "notes"))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsButton(I18n.t("action_edit"), { openForm(row); detail = null }, small = true, primary = false, modifier = Modifier.weight(1f))
                MmsButton("Pay salary", {
                    payRow = row
                    payAmount = Format.num(row, "salary").let { if (it > 0) it.toString() else "" }
                    payDate = Format.today(); payMethod = "Cash"; payRemarks = ""
                    detail = null
                }, small = true, modifier = Modifier.weight(1f))
            }
            MmsButton("Payment history", { historyRow = row; detail = null }, small = true, primary = false, modifier = Modifier.fillMaxWidth())
        }
    }

    if (payRow != null) {
        val row = payRow!!
        MmsDialog("Pay salary — ${Format.str(row, "name")}", onDismiss = { payRow = null }, confirmLabel = "Pay", onConfirm = {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repo.staffPaySalary(
                            mapOf(
                                "staffId" to Format.long(row, "id"),
                                "amount" to (payAmount.toDoubleOrNull() ?: 0.0),
                                "paymentDate" to payDate, "paymentMethod" to payMethod,
                                "remarks" to payRemarks
                            )
                        )
                    }
                    payRow = null; toast("Salary paid ✓", ToastMsg.Kind.Success)
                } catch (e: Exception) {
                    toast(e.message ?: "Error", ToastMsg.Kind.Error)
                }
            }
        }, confirmEnabled = (payAmount.toDoubleOrNull() ?: 0.0) > 0, compact = true) {
            MmsInput(payAmount, { payAmount = it }, label = "Amount", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsDateField(payDate, { payDate = it }, label = "Date", modifier = Modifier.weight(1f))
                MmsSelect(payMethod, listOf("Cash", "UPI", "Bank Transfer", "Cheque", "Card", "Other"), { payMethod = it }, label = "Method", modifier = Modifier.weight(1f))
            }
            MmsInput(payRemarks, { payRemarks = it }, label = "Remarks (month / period)")
        }
    }

    if (historyRow != null) {
        val row = historyRow!!
        var pays by remember(row) { mutableStateOf(listOf<Map<String, Any?>>()) }
        LaunchedEffect(row) {
            pays = withContext(Dispatchers.IO) { repo.staffPayments(Format.long(row, "id")) }
        }
        MmsDialog("Salary history", onDismiss = { historyRow = null }, compact = true) {
            if (pays.isEmpty()) {
                androidx.compose.foundation.text.BasicText("No salary payments recorded", style = MmsType.bodySm.copy(color = c.mut))
            } else {
                pays.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            CellText(Format.money(Format.num(p, "amount")), strong = true, sub = Format.str(p, "receipt_number"))
                            androidx.compose.foundation.text.BasicText(Format.str(p, "remarks"), style = MmsType.caption.copy(color = c.fnt))
                        }
                        androidx.compose.foundation.text.BasicText(Format.str(p, "payment_date"), style = MmsType.caption.copy(color = c.fnt))
                    }
                }
            }
        }
    }

    if (show) {
        MmsDialog(
            if (editId == null) "Add staff" else I18n.t("action_edit"),
            onDismiss = { show = false },
            onConfirm = {
                scope.launch {
                    try {
                        val data = mapOf(
                            "name" to name, "role" to role, "phone" to phone, "email" to email,
                            "address" to address, "joinedDate" to joined,
                            "salary" to (salary.toDoubleOrNull() ?: 0.0),
                            "paymentFrequency" to freq, "status" to memStatus, "notes" to notes
                        )
                        withContext(Dispatchers.IO) {
                            if (editId == null) repo.staffCreate(data) else repo.staffUpdate(editId!!, data)
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
                MmsSelect(role, repo.staffRoles(), { role = it }, label = "Role", modifier = Modifier.weight(1f))
                MmsSelect(memStatus, listOf("Active", "Resigned", "Terminated"), { memStatus = it }, label = "Status", modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(phone, { phone = it }, label = "Phone", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                MmsInput(email, { email = it }, label = "Email", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsDateField(joined, { joined = it }, label = "Joined", modifier = Modifier.weight(1f))
                MmsInput(salary, { salary = it }, label = "Salary", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            MmsSelect(freq, listOf("Monthly", "Weekly", "Daily", "Yearly"), { freq = it }, label = "Pay frequency")
            MmsInput(address, { address = it }, label = "Address", singleLine = false)
            MmsInput(notes, { notes = it }, label = "Notes", singleLine = false)
        }
    }
}

// ---------------------------------------------------------------- committee

@Composable
fun CommitteeScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var show by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var detail by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var name by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("Committee Member") }
    var ctype by remember { mutableStateOf("Executive") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var termStart by remember { mutableStateOf("") }
    var termEnd by remember { mutableStateOf("") }
    var memStatus by remember { mutableStateOf("Active") }
    var notes by remember { mutableStateOf("") }
    val c = C()

    fun reload() = scope.launch {
        loading = true
        try {
            rows = withContext(Dispatchers.IO) { repo.committeeList(search, status).rows }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search, status) { reload() }

    fun openForm(row: Map<String, Any?>?) {
        editId = row?.let { Format.long(it, "id") }
        name = row?.let { Format.str(it, "name") } ?: ""
        position = row?.let { Format.str(it, "position") }?.ifBlank { "Committee Member" } ?: "Committee Member"
        ctype = row?.let { Format.str(it, "committee_type") }?.ifBlank { "Executive" } ?: "Executive"
        phone = row?.let { Format.str(it, "phone") } ?: ""
        email = row?.let { Format.str(it, "email") } ?: ""
        address = row?.let { Format.str(it, "address") } ?: ""
        termStart = row?.let { Format.str(it, "term_start") } ?: ""
        termEnd = row?.let { Format.str(it, "term_end") } ?: ""
        memStatus = row?.let { Format.str(it, "status") }?.ifBlank { "Active" } ?: "Active"
        notes = row?.let { Format.str(it, "notes") } ?: ""
        show = true
    }

    ModuleScaffold(
        I18n.t("nav_committee"), "Elected & nominated members",
        listOf("All", "Active", "Past"), status, { status = it },
        search, { search = it }, loading = loading,
        onAdd = { openForm(null) }, addLabel = "Add member"
    ) {
        if (rows.isEmpty()) {
            EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "", "Add member") { openForm(null) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { Format.long(it, "id") }) { row ->
                    MmsCard(onClick = { detail = row }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TintTile(Format.str(row, "name").ifBlank { "C" }.take(1), Tints.of("cyan", c.isDark), 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                CellText(Format.str(row, "name"), strong = true, sub = Format.str(row, "committee_code") + " · " + Format.str(row, "position"))
                                androidx.compose.foundation.text.BasicText(
                                    Format.str(row, "committee_type") +
                                        (if (Format.str(row, "term_start").isNotBlank()) " · ${Format.str(row, "term_start")} → ${Format.str(row, "term_end")}" else ""),
                                    style = MmsType.caption.copy(color = c.fnt)
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

    if (detail != null) {
        val row = detail!!
        MmsDialog(Format.str(row, "name"), onDismiss = { detail = null }, compact = true) {
            DetailRow("Code", Format.str(row, "committee_code"), mono = true)
            DetailRow("Position", Format.str(row, "position"))
            DetailRow("Type", Format.str(row, "committee_type"))
            DetailRow("Phone", Format.str(row, "phone"))
            DetailRow("Email", Format.str(row, "email"))
            DetailRow("Term", "${Format.str(row, "term_start")} → ${Format.str(row, "term_end")}")
            DetailRow("Status", Format.str(row, "status"), strong = true)
            DetailRow("Notes", Format.str(row, "notes"))
            MmsButton(I18n.t("action_edit"), { openForm(row); detail = null }, small = true, modifier = Modifier.fillMaxWidth())
        }
    }

    if (show) {
        MmsDialog(
            if (editId == null) "Add member" else I18n.t("action_edit"),
            onDismiss = { show = false },
            onConfirm = {
                scope.launch {
                    try {
                        val data = mapOf(
                            "name" to name, "position" to position, "committeeType" to ctype,
                            "phone" to phone, "email" to email, "address" to address,
                            "termStart" to termStart, "termEnd" to termEnd,
                            "status" to memStatus, "notes" to notes
                        )
                        withContext(Dispatchers.IO) {
                            if (editId == null) repo.committeeCreate(data) else repo.committeeUpdate(editId!!, data)
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
                MmsSelect(position, repo.committeePositions(), { position = it }, label = "Position", modifier = Modifier.weight(1f))
                MmsSelect(ctype, repo.committeeTypes(), { ctype = it }, label = "Committee", modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(phone, { phone = it }, label = "Phone", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                MmsInput(email, { email = it }, label = "Email", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsDateField(termStart, { termStart = it }, label = "Term start", modifier = Modifier.weight(1f))
                MmsDateField(termEnd, { termEnd = it }, label = "Term end", modifier = Modifier.weight(1f))
            }
            MmsSelect(memStatus, listOf("Active", "Past", "Resigned"), { memStatus = it }, label = "Status")
            MmsInput(address, { address = it }, label = "Address", singleLine = false)
            MmsInput(notes, { notes = it }, label = "Notes", singleLine = false)
        }
    }
}

// ---------------------------------------------------------------- users

@Composable
fun UsersScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var show by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var editRow by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var resetRow by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Staff") }
    var eFull by remember { mutableStateOf("") }
    var eRole by remember { mutableStateOf("Staff") }
    var eEmail by remember { mutableStateOf("") }
    var ePhone by remember { mutableStateOf("") }
    var eActive by remember { mutableStateOf(true) }
    var newPwd by remember { mutableStateOf("") }
    val me = repo.auth.currentUser
    val c = C()
    val roles = listOf("Administrator", "President", "Secretary", "Treasurer", "Imam", "Staff", "Auditor")

    fun reload() = scope.launch {
        loading = true
        try {
            rows = withContext(Dispatchers.IO) { repo.usersList() }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    ModuleScaffold(
        I18n.t("nav_users"), "User accounts & roles",
        onAdd = { username = ""; fullName = ""; password = ""; role = "Staff"; show = true },
        addLabel = "Add user", loading = loading
    ) {
        if (rows.isEmpty()) {
            EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "", "Add user") { show = true }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { Format.long(it, "id") }) { row ->
                    val active = Format.long(row, "is_active") == 1L
                    val locked = Format.long(row, "is_locked") == 1L
                    MmsCard(onClick = { manage = row }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TintTile(Format.str(row, "full_name").ifBlank { "U" }.take(1), Tints.of("blue", c.isDark), 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                CellText(
                                    Format.str(row, "full_name") + if (me?.id == Format.long(row, "id")) " (you)" else "",
                                    strong = true,
                                    sub = "@" + Format.str(row, "username") + " · " + Format.str(row, "role")
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                StatusPill(if (active) "Active" else "Inactive", if (active) "Active" else "Rejected")
                                if (locked) StatusPill("Locked", "Pending")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    if (show) {
        MmsDialog("New user", onDismiss = { show = false }, onConfirm = {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repo.userCreate(mapOf("username" to username, "fullName" to fullName, "password" to password, "role" to role))
                    }
                    show = false; reload(); toast("User created ✓", ToastMsg.Kind.Success)
                } catch (e: Exception) {
                    toast(e.message ?: "Error", ToastMsg.Kind.Error)
                }
            }
        }, confirmEnabled = username.isNotBlank() && fullName.isNotBlank() && password.length >= 8) {
            MmsInput(username, { username = it }, label = "Username", placeholder = "3–32 chars")
            MmsInput(fullName, { fullName = it }, label = "Full name")
            MmsSelect(role, roles, { role = it }, label = "Role")
            MmsInput(password, { password = it }, label = "Password", password = true)
            androidx.compose.foundation.text.BasicText(
                "Min 8 chars with upper, lower, digit & special character.",
                style = MmsType.caption.copy(color = c.fnt)
            )
        }
    }

    if (manage != null) {
        val row = manage!!
        val id = Format.long(row, "id")
        val isMe = me?.id == id
        val active = Format.long(row, "is_active") == 1L
        val locked = Format.long(row, "is_locked") == 1L
        MmsDialog("@${Format.str(row, "username")}", onDismiss = { manage = null }, compact = true) {
            DetailRow("Name", Format.str(row, "full_name"))
            DetailRow("Role", Format.str(row, "role"))
            DetailRow("Last login", Format.str(row, "last_login_at"))
            Spacer(Modifier.height(4.dp))
            MmsButton("Edit details", {
                eFull = Format.str(row, "full_name"); eRole = Format.str(row, "role")
                eEmail = Format.str(row, "email"); ePhone = Format.str(row, "phone")
                eActive = active; editRow = row
            }, small = true, primary = false, modifier = Modifier.fillMaxWidth())
            MmsButton("Reset password", { newPwd = ""; resetRow = row }, small = true, primary = false, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsButton(if (active) "Deactivate" else "Activate", {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            repo.userUpdate(
                                id,
                                mapOf(
                                    "fullName" to Format.str(row, "full_name"), "role" to Format.str(row, "role"),
                                    "email" to Format.str(row, "email"), "phone" to Format.str(row, "phone"),
                                    "isActive" to if (active) 0 else 1
                                )
                            )
                        }
                        manage = null; reload(); toast("Updated", ToastMsg.Kind.Success)
                    }
                }, small = true, danger = active, ghost = active, primary = !active, modifier = Modifier.weight(1f), enabled = !isMe)
                MmsButton(if (locked) "Unlock" else "Lock", {
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.userToggleLock(id, !locked) }
                        manage = null; reload(); toast(if (locked) "Unlocked" else "Locked", ToastMsg.Kind.Success)
                    }
                }, small = true, primary = false, modifier = Modifier.weight(1f), enabled = !isMe)
            }
            if (isMe) {
                InfoBanner("You cannot lock or deactivate your own account.", "info")
            }
        }
    }

    if (editRow != null) {
        val row = editRow!!
        MmsDialog("Edit user", onDismiss = { editRow = null }, onConfirm = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    repo.userUpdate(
                        Format.long(row, "id"),
                        mapOf("fullName" to eFull, "role" to eRole, "email" to eEmail, "phone" to ePhone, "isActive" to if (eActive) 1 else 0)
                    )
                }
                editRow = null; manage = null; reload(); toast("User updated", ToastMsg.Kind.Success)
            }
        }, compact = true) {
            MmsInput(eFull, { eFull = it }, label = "Full name")
            MmsSelect(eRole, roles, { eRole = it }, label = "Role")
            MmsInput(eEmail, { eEmail = it }, label = "Email")
            MmsInput(ePhone, { ePhone = it }, label = "Phone")
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText("Account active", style = MmsType.bodySm.copy(color = c.tx), modifier = Modifier.weight(1f))
                FilterChip("Yes", eActive, { eActive = true })
                Spacer(Modifier.width(6.dp))
                FilterChip("No", !eActive, { eActive = false })
            }
        }
    }

    if (resetRow != null) {
        val row = resetRow!!
        MmsDialog("Reset password", onDismiss = { resetRow = null }, confirmLabel = "Reset", danger = true,
            confirmEnabled = newPwd.length >= 8,
            onConfirm = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repo.userResetPassword(Format.long(row, "id"), newPwd) }
                        resetRow = null; manage = null; reload()
                        toast("Password reset — user must change it at next login", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                }
            }, compact = true) {
            InfoBanner("The user will be asked to set a new password on next sign-in.", "warn")
            MmsInput(newPwd, { newPwd = it }, label = "Temporary password", password = true)
        }
    }
}
