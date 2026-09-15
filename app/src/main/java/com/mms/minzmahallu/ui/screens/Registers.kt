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
import com.mms.minzmahallu.util.PdfUtil
import com.mms.minzmahallu.util.QrUtil
import com.mms.minzmahallu.util.ShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- marriages

@Composable
fun MarriagesScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var show by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var detail by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var bride by remember { mutableStateOf("") }
    var brideFather by remember { mutableStateOf("") }
    var brideAddr by remember { mutableStateOf("") }
    var groom by remember { mutableStateOf("") }
    var groomFather by remember { mutableStateOf("") }
    var groomAddr by remember { mutableStateOf("") }
    var w1 by remember { mutableStateOf("") }
    var w2 by remember { mutableStateOf("") }
    var nikah by remember { mutableStateOf(Format.today()) }
    var place by remember { mutableStateOf("") }
    var mahar by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    val c = C()

    fun reload() = scope.launch {
        loading = true
        try {
            rows = withContext(Dispatchers.IO) { repo.marriagesList(search).rows }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search) { reload() }

    fun openForm(row: Map<String, Any?>?) {
        editId = row?.let { Format.long(it, "id") }
        bride = row?.let { Format.str(it, "bride_name") } ?: ""
        brideFather = row?.let { Format.str(it, "bride_father") } ?: ""
        brideAddr = row?.let { Format.str(it, "bride_address") } ?: ""
        groom = row?.let { Format.str(it, "groom_name") } ?: ""
        groomFather = row?.let { Format.str(it, "groom_father") } ?: ""
        groomAddr = row?.let { Format.str(it, "groom_address") } ?: ""
        w1 = row?.let { Format.str(it, "witness1") } ?: ""
        w2 = row?.let { Format.str(it, "witness2") } ?: ""
        nikah = row?.let { Format.str(it, "nikah_date") }?.ifBlank { Format.today() } ?: Format.today()
        place = row?.let { Format.str(it, "place") } ?: ""
        mahar = row?.let { Format.str(it, "mahar") } ?: ""
        remarks = row?.let { Format.str(it, "remarks") } ?: ""
        show = true
    }

    ModuleScaffold(
        I18n.t("nav_marriage"), "Nikah register",
        search = search, onSearch = { search = it }, loading = loading,
        onAdd = { openForm(null) }, addLabel = "Add marriage"
    ) {
        if (rows.isEmpty()) {
            EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "", "Add marriage") { openForm(null) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { Format.long(it, "id") }) { row ->
                    MmsCard(onClick = { detail = row }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TintTile(G.HEART, Tints.of("vio", c.isDark), 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                CellText(
                                    "${Format.str(row, "groom_name")}  ${G.HEART}  ${Format.str(row, "bride_name")}",
                                    strong = true,
                                    sub = Format.str(row, "marriage_number")
                                )
                                androidx.compose.foundation.text.BasicText(
                                    Format.str(row, "nikah_date") + " · " + Format.str(row, "place"),
                                    style = MmsType.caption.copy(color = c.fnt)
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    if (detail != null) {
        val row = detail!!
        val ctx = LocalContext.current
        MmsDialog(Format.str(row, "marriage_number"), onDismiss = { detail = null }) {
            DetailRow("Groom", Format.str(row, "groom_name"), strong = true)
            DetailRow("Groom father", Format.str(row, "groom_father"))
            DetailRow("Bride", Format.str(row, "bride_name"), strong = true)
            DetailRow("Bride father", Format.str(row, "bride_father"))
            DetailRow("Nikah date", Format.str(row, "nikah_date"))
            DetailRow("Place", Format.str(row, "place"))
            DetailRow("Mahar", Format.str(row, "mahar"))
            DetailRow("Witnesses", listOf(Format.str(row, "witness1"), Format.str(row, "witness2")).filter { it.isNotBlank() }.joinToString(", "))
            DetailRow("Remarks", Format.str(row, "remarks"))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsButton(I18n.t("action_edit"), { openForm(row); detail = null }, small = true, primary = false, modifier = Modifier.weight(1f))
                MmsButton("Issue certificate", {
                    scope.launch {
                        try {
                            val res = withContext(Dispatchers.IO) {
                                repo.certificateIssue(
                                    "Marriage",
                                    "${Format.str(row, "groom_name")} ${G.HEART} ${Format.str(row, "bride_name")}",
                                    marriageId = Format.long(row, "id"),
                                    notes = "Nikah ${Format.str(row, "nikah_date")} at ${Format.str(row, "place")}"
                                )
                            }
                            toast("Issued ${res["certificateNumber"]}", ToastMsg.Kind.Success)
                        } catch (e: Exception) {
                            toast(e.message ?: "Error", ToastMsg.Kind.Error)
                        }
                    }
                }, small = true, modifier = Modifier.weight(1f))
            }
            MmsButton("Share details", {
                ShareUtil.shareText(
                    ctx,
                    "Nikah Record ${Format.str(row, "marriage_number")}\nGroom: ${Format.str(row, "groom_name")} (S/o ${Format.str(row, "groom_father")})\nBride: ${Format.str(row, "bride_name")} (D/o ${Format.str(row, "bride_father")})\nDate: ${Format.str(row, "nikah_date")}\nPlace: ${Format.str(row, "place")}",
                    "Share nikah record"
                )
            }, small = true, primary = false, modifier = Modifier.fillMaxWidth())
        }
    }

    if (show) {
        MmsDialog(
            if (editId == null) "Add marriage" else I18n.t("action_edit"),
            onDismiss = { show = false },
            onConfirm = {
                scope.launch {
                    try {
                        val data = mapOf(
                            "groomName" to groom, "groomFather" to groomFather, "groomAddress" to groomAddr,
                            "brideName" to bride, "brideFather" to brideFather, "brideAddress" to brideAddr,
                            "witness1" to w1, "witness2" to w2, "mahar" to mahar,
                            "nikahDate" to nikah, "place" to place, "remarks" to remarks
                        )
                        withContext(Dispatchers.IO) {
                            if (editId == null) repo.marriageCreate(data) else repo.marriageUpdate(editId!!, data)
                        }
                        show = false; reload(); toast("Saved ✓", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                }
            },
            confirmEnabled = groom.isNotBlank() && bride.isNotBlank()
        ) {
            SectionLabel("Groom")
            MmsInput(groom, { groom = it }, label = "Groom name")
            MmsInput(groomFather, { groomFather = it }, label = "Groom's father")
            MmsInput(groomAddr, { groomAddr = it }, label = "Groom address")
            SectionLabel("Bride")
            MmsInput(bride, { bride = it }, label = "Bride name")
            MmsInput(brideFather, { brideFather = it }, label = "Bride's father")
            MmsInput(brideAddr, { brideAddr = it }, label = "Bride address")
            SectionLabel("Nikah")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsDateField(nikah, { nikah = it }, label = "Nikah date", modifier = Modifier.weight(1f))
                MmsInput(place, { place = it }, label = "Place", modifier = Modifier.weight(1f))
            }
            MmsInput(mahar, { mahar = it }, label = "Mahar")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(w1, { w1 = it }, label = "Witness 1", modifier = Modifier.weight(1f))
                MmsInput(w2, { w2 = it }, label = "Witness 2", modifier = Modifier.weight(1f))
            }
            MmsInput(remarks, { remarks = it }, label = "Remarks", singleLine = false)
        }
    }
}

// ---------------------------------------------------------------- deaths

@Composable
fun DeathsScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var families by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var show by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var detail by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var name by remember { mutableStateOf("") }
    var father by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var age by remember { mutableStateOf("") }
    var dod by remember { mutableStateOf(Format.today()) }
    var placeOfDeath by remember { mutableStateOf("") }
    var burialDate by remember { mutableStateOf(Format.today()) }
    var burialPlace by remember { mutableStateOf("") }
    var cause by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var famId by remember { mutableStateOf(0L) }
    var famLabel by remember { mutableStateOf("") }
    val c = C()

    fun reload() = scope.launch {
        loading = true
        try {
            withContext(Dispatchers.IO) {
                rows = repo.deathsList(search).rows
                if (families.isEmpty()) families = repo.familiesList(status = "Active").rows
            }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search) { reload() }

    fun openForm(row: Map<String, Any?>?) {
        editId = row?.let { Format.long(it, "id") }
        name = row?.let { Format.str(it, "deceased_name") } ?: ""
        father = row?.let { Format.str(it, "father_name") } ?: ""
        gender = row?.let { Format.str(it, "gender") }?.ifBlank { "Male" } ?: "Male"
        age = row?.let { Format.long(it, "age").let { a -> if (a > 0) a.toString() else "" } } ?: ""
        dod = row?.let { Format.str(it, "date_of_death") }?.ifBlank { Format.today() } ?: Format.today()
        placeOfDeath = row?.let { Format.str(it, "place_of_death") } ?: ""
        burialDate = row?.let { Format.str(it, "burial_date") }?.ifBlank { Format.today() } ?: Format.today()
        burialPlace = row?.let { Format.str(it, "burial_place") } ?: ""
        cause = row?.let { Format.str(it, "cause_of_death") } ?: ""
        address = row?.let { Format.str(it, "address") } ?: ""
        remarks = row?.let { Format.str(it, "remarks") } ?: ""
        famId = row?.let { Format.long(it, "family_id") } ?: 0L
        famLabel = if (famId > 0) families.firstOrNull { Format.long(it, "id") == famId }?.let {
            "${Format.str(it, "family_number")} — ${Format.str(it, "house_name")}"
        } ?: "" else ""
        show = true
    }

    ModuleScaffold(
        I18n.t("nav_death"), "Death register",
        search = search, onSearch = { search = it }, loading = loading,
        onAdd = { openForm(null) }, addLabel = "Add record"
    ) {
        if (rows.isEmpty()) {
            EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "", "Add record") { openForm(null) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { Format.long(it, "id") }) { row ->
                    MmsCard(onClick = { detail = row }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TintTile(Format.str(row, "deceased_name").ifBlank { "D" }.take(1), Tints.of("slate", c.isDark), 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                CellText(Format.str(row, "deceased_name"), strong = true, sub = Format.str(row, "death_number"))
                                androidx.compose.foundation.text.BasicText(
                                    Format.str(row, "date_of_death") + " · " + Format.str(row, "burial_place"),
                                    style = MmsType.caption.copy(color = c.fnt)
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    if (detail != null) {
        val row = detail!!
        MmsDialog(Format.str(row, "death_number"), onDismiss = { detail = null }) {
            DetailRow("Name", Format.str(row, "deceased_name"), strong = true)
            DetailRow("Father", Format.str(row, "father_name"))
            DetailRow("Gender / Age", "${Format.str(row, "gender")} / ${Format.long(row, "age")}")
            DetailRow("Date of death", Format.str(row, "date_of_death"))
            DetailRow("Place of death", Format.str(row, "place_of_death"))
            DetailRow("Burial date", Format.str(row, "burial_date"))
            DetailRow("Burial place", Format.str(row, "burial_place"))
            DetailRow("Cause", Format.str(row, "cause_of_death"))
            DetailRow("Address", Format.str(row, "address"))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsButton(I18n.t("action_edit"), { openForm(row); detail = null }, small = true, primary = false, modifier = Modifier.weight(1f))
                MmsButton("Issue certificate", {
                    scope.launch {
                        try {
                            val res = withContext(Dispatchers.IO) {
                                repo.certificateIssue("Death", Format.str(row, "deceased_name"), deathId = Format.long(row, "id"))
                            }
                            toast("Issued ${res["certificateNumber"]}", ToastMsg.Kind.Success)
                        } catch (e: Exception) {
                            toast(e.message ?: "Error", ToastMsg.Kind.Error)
                        }
                    }
                }, small = true, modifier = Modifier.weight(1f))
            }
        }
    }

    if (show) {
        MmsDialog(
            if (editId == null) "Add record" else I18n.t("action_edit"),
            onDismiss = { show = false },
            onConfirm = {
                scope.launch {
                    try {
                        val data = mapOf(
                            "deceasedName" to name, "fatherName" to father, "gender" to gender,
                            "age" to (age.toLongOrNull()), "dateOfDeath" to dod,
                            "placeOfDeath" to placeOfDeath, "burialDate" to burialDate,
                            "burialPlace" to burialPlace, "causeOfDeath" to cause,
                            "address" to address, "remarks" to remarks,
                            "familyId" to if (famId > 0) famId else null
                        )
                        withContext(Dispatchers.IO) {
                            if (editId == null) repo.deathCreate(data) else repo.deathUpdate(editId!!, data)
                        }
                        show = false; reload(); toast("Saved ✓", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                }
            },
            confirmEnabled = name.isNotBlank()
        ) {
            MmsInput(name, { name = it }, label = "Deceased name")
            MmsInput(father, { father = it }, label = "Father's name")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsSelect(gender, listOf("Male", "Female", "Other"), { gender = it }, label = "Gender", modifier = Modifier.weight(1f))
                MmsInput(age, { age = it }, label = "Age", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsDateField(dod, { dod = it }, label = "Date of death", modifier = Modifier.weight(1f))
                MmsInput(placeOfDeath, { placeOfDeath = it }, label = "Place of death", modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsDateField(burialDate, { burialDate = it }, label = "Burial date", modifier = Modifier.weight(1f))
                MmsInput(burialPlace, { burialPlace = it }, label = "Burial place", modifier = Modifier.weight(1f))
            }
            MmsInput(cause, { cause = it }, label = "Cause of death")
            MmsSelect(
                famLabel.ifBlank { "No family link" },
                listOf("No family link") + families.map { "${Format.str(it, "family_number")} — ${Format.str(it, "house_name")}" },
                { label ->
                    if (label == "No family link") {
                        famLabel = ""; famId = 0
                    } else {
                        famLabel = label
                        val code = label.substringBefore(" —")
                        famId = families.firstOrNull { Format.str(it, "family_number") == code }?.let { Format.long(it, "id") } ?: 0
                    }
                },
                label = "Link family"
            )
            MmsInput(address, { address = it }, label = "Address", singleLine = false)
            MmsInput(remarks, { remarks = it }, label = "Remarks", singleLine = false)
        }
    }
}

// ---------------------------------------------------------------- welfare

@Composable
fun WelfareScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var families by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var show by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var applicant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Financial Assistance") }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var famId by remember { mutableStateOf(0L) }
    var famLabel by remember { mutableStateOf("") }
    var actionRow by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var actionKind by remember { mutableStateOf("") }
    var actionAmount by remember { mutableStateOf("") }
    var actionMinutes by remember { mutableStateOf("") }
    var actionReason by remember { mutableStateOf("") }
    var adminPwd by remember { mutableStateOf("") }
    val c = C()

    fun reload() = scope.launch {
        loading = true
        try {
            withContext(Dispatchers.IO) {
                rows = repo.welfareList(search, status).rows
                if (families.isEmpty()) families = repo.familiesList(status = "Active").rows
            }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search, status) { reload() }

    ModuleScaffold(
        I18n.t("nav_welfare"), "Aid requests & disbursements",
        listOf("All", "Pending", "Approved", "Rejected", "Disbursed"), status, { status = it },
        search, { search = it }, loading = loading,
        onAdd = {
            applicant = ""; amount = ""; reason = ""; category = "Financial Assistance"
            famId = 0; famLabel = ""; show = true
        }, addLabel = "New request"
    ) {
        if (rows.isEmpty()) {
            EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "", "New request") { show = true }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { Format.long(it, "id") }) { row ->
                    MmsCard(onClick = { detail = row }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                CellText(
                                    Format.str(row, "applicant_name"), strong = true,
                                    sub = Format.str(row, "request_number") + " · " + Format.str(row, "category")
                                )
                                androidx.compose.foundation.text.BasicText(
                                    "Requested ${Format.money(Format.num(row, "amount_requested"))}" +
                                        (if (Format.num(row, "amount_approved") > 0) "  ·  Approved ${Format.money(Format.num(row, "amount_approved"))}" else ""),
                                    style = MmsType.bodySm.copy(color = c.mut)
                                )
                            }
                            StatusPill(Format.str(row, "status"), Format.str(row, "status"))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (Format.str(row, "status")) {
                                "Pending" -> {
                                    MmsButton("Approve", {
                                        actionRow = row; actionKind = "approve"
                                        actionAmount = Format.num(row, "amount_requested").toString()
                                        actionMinutes = Format.today(); actionReason = ""
                                    }, small = true)
                                    MmsButton("Reject", {
                                        actionRow = row; actionKind = "reject"; actionReason = ""
                                    }, small = true, danger = true, ghost = true)
                                }
                                "Approved" -> {
                                    MmsButton("Disburse", {
                                        actionRow = row; actionKind = "disburse"
                                        adminPwd = ""; actionReason = ""
                                        actionMinutes = Format.str(row, "minutes_date").ifBlank { Format.today() }
                                    }, small = true)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    if (detail != null) {
        val row = detail!!
        MmsDialog(Format.str(row, "request_number"), onDismiss = { detail = null }, compact = true) {
            DetailRow("Applicant", Format.str(row, "applicant_name"), strong = true)
            DetailRow("Category", Format.str(row, "category"))
            DetailRow("Requested", Format.money(Format.num(row, "amount_requested")))
            DetailRow("Approved", Format.money(Format.num(row, "amount_approved")))
            DetailRow("Status", Format.str(row, "status"), strong = true)
            DetailRow("Request date", Format.str(row, "request_date"))
            DetailRow("Minutes date", Format.str(row, "minutes_date"))
            DetailRow("Disbursed", Format.str(row, "disbursed_date"))
            DetailRow("Reason", Format.str(row, "reason"))
            DetailRow("Remarks", Format.str(row, "remarks"))
        }
    }

    if (show) {
        MmsDialog("Welfare request", onDismiss = { show = false }, onConfirm = {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repo.welfareCreate(
                            mapOf(
                                "applicantName" to applicant, "category" to category,
                                "amountRequested" to (amount.toDoubleOrNull() ?: 0.0), "reason" to reason,
                                "familyId" to if (famId > 0) famId else null
                            )
                        )
                    }
                    show = false; reload(); toast("Created ✓", ToastMsg.Kind.Success)
                } catch (e: Exception) {
                    toast(e.message ?: "Error", ToastMsg.Kind.Error)
                }
            }
        }, confirmEnabled = applicant.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0) {
            MmsInput(applicant, { applicant = it }, label = "Applicant")
            MmsSelect(category, repo.welfareCategories(), { category = it }, label = "Category")
            MmsInput(amount, { amount = it }, label = "Amount requested", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            MmsSelect(
                famLabel.ifBlank { "No family link" },
                listOf("No family link") + families.map { "${Format.str(it, "family_number")} — ${Format.str(it, "house_name")}" },
                { label ->
                    if (label == "No family link") {
                        famLabel = ""; famId = 0
                    } else {
                        famLabel = label
                        val code = label.substringBefore(" —")
                        famId = families.firstOrNull { Format.str(it, "family_number") == code }?.let { Format.long(it, "id") } ?: 0
                    }
                },
                label = "Link family"
            )
            MmsInput(reason, { reason = it }, label = "Reason", singleLine = false)
        }
    }

    if (actionRow != null) {
        val row = actionRow!!
        MmsDialog(
            actionKind.replaceFirstChar { it.uppercase() } + " — " + Format.str(row, "request_number"),
            onDismiss = { actionRow = null },
            confirmLabel = actionKind.replaceFirstChar { it.uppercase() },
            danger = actionKind == "reject",
            onConfirm = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            when (actionKind) {
                                "approve" -> repo.welfareApprove(
                                    Format.long(row, "id"),
                                    actionAmount.toDoubleOrNull() ?: 0.0,
                                    actionReason, actionMinutes
                                )
                                "reject" -> repo.welfareReject(Format.long(row, "id"), actionReason)
                                "disburse" -> repo.welfareDisburse(
                                    Format.long(row, "id"),
                                    actionReason, adminPwd, actionMinutes
                                )
                                else -> Unit
                            }
                        }
                        actionRow = null; reload(); toast("Done ✓", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                }
            },
            compact = true
        ) {
            if (actionKind == "approve") {
                InfoBanner("Approval records the committee minutes date — required before disbursement.", "info")
                MmsInput(actionAmount, { actionAmount = it }, label = "Approved amount", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                MmsDateField(actionMinutes, { actionMinutes = it }, label = "Committee minutes date")
            }
            if (actionKind == "disburse") {
                InfoBanner("Disbursement needs the Administrator password.", "warn")
                MmsDateField(actionMinutes, { actionMinutes = it }, label = "Committee minutes date")
                MmsInput(adminPwd, { adminPwd = it }, label = "Administrator password", password = true)
            }
            MmsInput(actionReason, { actionReason = it }, label = if (actionKind == "reject") "Rejection reason" else "Notes", singleLine = false)
        }
    }
}

// ---------------------------------------------------------------- certificates

@Composable
fun CertificatesScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf("Certificates") }
    var search by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var show by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("Membership") }
    var issuedTo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var revokeId by remember { mutableStateOf<Long?>(null) }
    // verify tab
    var verifyCode by remember { mutableStateOf("") }
    var verifyResult by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var verifyTried by remember { mutableStateOf(false) }
    var orgName by remember { mutableStateOf("Minz Mahallu") }
    val c = C()

    fun reload() = scope.launch {
        loading = true
        try {
            withContext(Dispatchers.IO) {
                rows = repo.certificatesList(search).rows
                orgName = Format.str(repo.settingsLoad(), "mahallu_name").ifBlank { "Minz Mahallu" }
            }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search) { if (tab == "Certificates") reload() }

    ModuleScaffold(
        I18n.t("nav_certificates"), "Issue & verify certificates",
        listOf("Certificates", "Verify"), tab, { tab = it },
        search = if (tab == "Certificates") search else null,
        onSearch = if (tab == "Certificates") ({ s: String -> search = s }) else null,
        loading = loading && tab == "Certificates",
        onAdd = if (tab == "Certificates") ({ type = "Membership"; issuedTo = ""; notes = ""; show = true }) else null,
        addLabel = "Issue"
    ) {
        if (tab == "Verify") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MmsCard {
                    androidx.compose.foundation.text.BasicText(
                        "Verify a certificate",
                        style = MmsType.headline.copy(color = c.tx)
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.text.BasicText(
                        "Enter the certificate number or verification code printed on the document.",
                        style = MmsType.bodySm.copy(color = c.mut)
                    )
                    Spacer(Modifier.height(12.dp))
                    MmsInput(verifyCode, { verifyCode = it; verifyTried = false }, label = "Certificate no / code", placeholder = "CRT-… / code")
                    Spacer(Modifier.height(4.dp))
                    MmsButton("Verify", {
                        scope.launch {
                            verifyResult = withContext(Dispatchers.IO) { repo.certificateVerify(verifyCode.trim()) }
                            verifyTried = true
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = verifyCode.isNotBlank())
                }
                if (verifyTried) {
                    if (verifyResult == null) {
                        InfoBanner("No certificate found for \"$verifyCode\". Check the code and try again.", "error")
                    } else {
                        val r = verifyResult!!
                        val revoked = Format.str(r, "status") == "Revoked"
                        MmsCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TintTile(
                                    if (revoked) G.CROSS else G.CHECK,
                                    Tints.of(if (revoked) "rose" else "em", c.isDark), 42.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    androidx.compose.foundation.text.BasicText(
                                        if (revoked) "REVOKED" else "VALID CERTIFICATE",
                                        style = MmsType.headline.copy(
                                            color = if (revoked) c.cRose else c.em,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    androidx.compose.foundation.text.BasicText(
                                        Format.str(r, "certificate_number"),
                                        style = MmsType.code.copy(color = c.mut)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            DetailRow("Type", Format.str(r, "type"))
                            DetailRow("Issued to", Format.str(r, "issued_to"))
                            DetailRow("Issued", Format.str(r, "issued_date"))
                        }
                    }
                }
            }
        } else {
            Column {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Membership", "Residence", "Marriage", "Death").forEach { t ->
                        MmsCard(modifier = Modifier.weight(1f), onClick = { type = t; issuedTo = ""; notes = ""; show = true }) {
                            androidx.compose.foundation.text.BasicText(
                                t,
                                style = MmsType.bodySm.copy(color = c.tx, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
                if (rows.isEmpty()) {
                    EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "", "Issue") { show = true }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rows, key = { Format.long(it, "id") }) { row ->
                            MmsCard(onClick = { detail = row }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        CellText(
                                            Format.str(row, "issued_to"),
                                            strong = true,
                                            sub = Format.str(row, "certificate_number") + " · " + Format.str(row, "type")
                                        )
                                        androidx.compose.foundation.text.BasicText(
                                            "Verify: " + Format.str(row, "verification_code"),
                                            style = MmsType.caption.copy(color = c.em)
                                        )
                                    }
                                    StatusPill(Format.str(row, "status").ifBlank { "Issued" }, Format.str(row, "status").ifBlank { "Issued" })
                                }
                            }
                        }
                        item { Spacer(Modifier.height(90.dp)) }
                    }
                }
            }
        }
    }

    if (show) {
        MmsDialog("Issue $type certificate", onDismiss = { show = false }, onConfirm = {
            scope.launch {
                try {
                    val r = withContext(Dispatchers.IO) { repo.certificateIssue(type, issuedTo, notes = notes) }
                    show = false; reload(); toast("Issued ${r["certificateNumber"]}", ToastMsg.Kind.Success)
                } catch (e: Exception) {
                    toast(e.message ?: "Error", ToastMsg.Kind.Error)
                }
            }
        }, confirmEnabled = issuedTo.isNotBlank()) {
            MmsSelect(
                type,
                listOf("Membership", "Residence", "Marriage", "Death", "Character", "Income", "NOC"),
                { type = it },
                label = "Type"
            )
            MmsInput(issuedTo, { issuedTo = it }, label = "Issued to")
            MmsInput(notes, { notes = it }, label = "Notes", singleLine = false)
        }
    }

    if (detail != null) {
        val row = detail!!
        val revoked = Format.str(row, "status") == "Revoked"
        val payload = QrUtil.certificatePayload(orgName, Format.str(row, "certificate_number"), Format.str(row, "verification_code"))
        val qr = remember(row) { QrUtil.make(payload, 480) }
        var busy by remember { mutableStateOf(false) }
        MmsDialog(Format.str(row, "certificate_number"), onDismiss = { detail = null }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                QrImage(qr, 170.dp)
            }
            Spacer(Modifier.height(8.dp))
            DetailRow("Type", Format.str(row, "type"))
            DetailRow("Issued to", Format.str(row, "issued_to"), strong = true)
            DetailRow("Issued", Format.str(row, "issued_date"))
            DetailRow("Status", Format.str(row, "status").ifBlank { "Issued" }, strong = true)
            DetailRow("Verify code", Format.str(row, "verification_code"), mono = true)
            DetailRow("Reprints", Format.long(row, "reprint_count").toString())
            DetailRow("Notes", Format.str(row, "notes"))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsButton("PDF ${G.SHARE}", {
                    scope.launch {
                        busy = true
                        try {
                            val s = withContext(Dispatchers.IO) { repo.settingsLoad() }
                            val file = withContext(Dispatchers.IO) {
                                PdfUtil.certificatePdf(
                                    ctx,
                                    Format.str(s, "mahallu_name").ifBlank { orgName },
                                    listOf(Format.str(s, "village"), Format.str(s, "district"), Format.str(s, "phone")).filter { it.isNotBlank() }.joinToString(", "),
                                    "${Format.str(row, "type")} Certificate",
                                    Format.str(row, "issued_to"),
                                    Format.str(row, "certificate_number"),
                                    Format.str(row, "verification_code"),
                                    Format.str(row, "issued_date"),
                                    listOf("Status" to Format.str(row, "status").ifBlank { "Issued" }),
                                    "cert-${Format.str(row, "certificate_number")}.pdf"
                                )
                            }
                            withContext(Dispatchers.IO) { repo.certificateReprint(Format.long(row, "id")) }
                            ShareUtil.shareFile(ctx, file, "application/pdf", "Share certificate")
                            reload()
                        } catch (e: Exception) {
                            toast(e.message ?: "PDF failed", ToastMsg.Kind.Error)
                        }
                        busy = false
                    }
                }, small = true, modifier = Modifier.weight(1f), enabled = !busy && !revoked)
                MmsButton("Share", {
                    ShareUtil.shareText(
                        ctx,
                        "$orgName — ${Format.str(row, "type")} Certificate\nNo: ${Format.str(row, "certificate_number")}\nIssued to: ${Format.str(row, "issued_to")}\nDate: ${Format.str(row, "issued_date")}\nVerify code: ${Format.str(row, "verification_code")}",
                        "Share certificate"
                    )
                }, small = true, primary = false, modifier = Modifier.weight(1f))
            }
            if (!revoked) {
                MmsButton("Revoke certificate", { revokeId = Format.long(row, "id") }, small = true, danger = true, ghost = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (revokeId != null) {
        ConfirmDialog(
            "Revoke certificate",
            "Revoked certificates stay in the register and show as REVOKED when verified. Continue?",
            confirmLabel = "Revoke",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.certificateSetStatus(revokeId!!, "Revoked") }
                    revokeId = null; detail = null; reload(); toast("Certificate revoked", ToastMsg.Kind.Success)
                }
            },
            onDismiss = { revokeId = null }
        )
    }
}

// ---------------------------------------------------------------- tokens

@Composable
fun TokensScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var tokens by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var stats by remember { mutableStateOf(mapOf<String, Any?>()) }
    var search by remember { mutableStateOf("") }
    var showEvent by remember { mutableStateOf(false) }
    var editEvent by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var deleteEvent by remember { mutableStateOf<Long?>(null) }
    var eventName by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(Format.today()) }
    var tokenAction by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var tokenAmount by remember { mutableStateOf("") }
    var cancelReason by remember { mutableStateOf("") }
    val c = C()

    fun reloadEvents() = scope.launch {
        loading = true
        try {
            events = withContext(Dispatchers.IO) { repo.tokenEventsList() }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    fun reloadTokens() = scope.launch {
        val id = selected ?: return@launch
        try {
            withContext(Dispatchers.IO) {
                tokens = repo.tokensList(id, search.ifBlank { null })
                stats = repo.tokenStats(id)
            }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
    }
    LaunchedEffect(Unit) { reloadEvents() }
    LaunchedEffect(selected, search) { reloadTokens() }

    if (selected == null) {
        ModuleScaffold(
            I18n.t("nav_tokens"), "Token events & collection",
            onAdd = { eventName = ""; eventDate = Format.today(); showEvent = true },
            addLabel = "New event", loading = loading
        ) {
            if (events.isEmpty()) {
                EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "Create an event, then generate tokens for families", "New event") {
                    eventName = ""; eventDate = Format.today(); showEvent = true
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(events, key = { Format.long(it, "id") }) { row ->
                        MmsCard(onClick = { selected = Format.long(row, "id"); search = "" }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TintTile("T", Tints.of("pink", c.isDark), 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    CellText(
                                        Format.str(row, "name"), strong = true,
                                        sub = Format.str(row, "event_date") + " · " + Format.long(row, "token_count") + " tokens"
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    StatusPill(Format.str(row, "status").ifBlank { "Active" }, Format.str(row, "status").ifBlank { "Active" })
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MmsButton("Edit", { editEvent = row }, small = true, primary = false)
                                        MmsButton("Del", { deleteEvent = Format.long(row, "id") }, small = true, danger = true, ghost = true)
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
        if (showEvent) {
            MmsDialog("New token event", onDismiss = { showEvent = false }, onConfirm = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repo.tokenEventCreate(mapOf("name" to eventName, "eventDate" to eventDate)) }
                        showEvent = false; reloadEvents(); toast("Event created ✓", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                }
            }, confirmEnabled = eventName.isNotBlank(), compact = true) {
                MmsInput(eventName, { eventName = it }, label = "Event name")
                MmsDateField(eventDate, { eventDate = it }, label = "Event date")
            }
        }
        if (editEvent != null) {
            val row = editEvent!!
            var eName by remember(row) { mutableStateOf(Format.str(row, "name")) }
            var eDate by remember(row) { mutableStateOf(Format.str(row, "event_date")) }
            var eStatus by remember(row) { mutableStateOf(Format.str(row, "status").ifBlank { "Active" }) }
            MmsDialog("Edit event", onDismiss = { editEvent = null }, onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        repo.tokenEventUpdate(
                            Format.long(row, "id"),
                            mapOf(
                                "name" to eName,
                                "eventType" to Format.str(row, "event_type").ifBlank { "General" },
                                "eventDate" to eDate,
                                "venue" to Format.str(row, "venue"),
                                "description" to Format.str(row, "description"),
                                "status" to eStatus,
                                "targetAmount" to Format.num(row, "target_amount")
                            )
                        )
                    }
                    editEvent = null; reloadEvents(); toast("Event updated", ToastMsg.Kind.Success)
                }
            }, compact = true) {
                MmsInput(eName, { eName = it }, label = "Event name")
                MmsDateField(eDate, { eDate = it }, label = "Event date")
                MmsSelect(eStatus, listOf("Active", "Closed", "Cancelled"), { eStatus = it }, label = "Status")
            }
        }
        if (deleteEvent != null) {
            ConfirmDialog(
                "Delete event",
                "The event and its pending tokens will be removed. Events with collected tokens cannot be deleted.",
                confirmLabel = "Delete",
                onConfirm = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repo.tokenEventDelete(deleteEvent!!) }
                            deleteEvent = null; reloadEvents(); toast("Event deleted", ToastMsg.Kind.Success)
                        } catch (e: Exception) {
                            deleteEvent = null
                            toast(e.message ?: "Error", ToastMsg.Kind.Error)
                        }
                    }
                },
                onDismiss = { deleteEvent = null }
            )
        }
    } else {
        val total = (stats["total"] as? Number)?.toLong() ?: 0L
        val collected = (stats["collected"] as? Number)?.toLong() ?: 0L
        val pending = (stats["pending"] as? Number)?.toLong() ?: 0L
        ModuleScaffold(
            "Tokens", "Collect & manage",
            search = search, onSearch = { search = it },
            extraActions = {
                MmsButton("Generate", {
                    scope.launch {
                        try {
                            val n = withContext(Dispatchers.IO) {
                                val fams = repo.familiesList(status = "Active").rows.map { Format.long(it, "id") }
                                repo.tokensGenerate(selected!!, fams)
                            }
                            reloadTokens(); toast("Generated $n tokens", ToastMsg.Kind.Success)
                        } catch (e: Exception) {
                            toast(e.message ?: "Error", ToastMsg.Kind.Error)
                        }
                    }
                }, small = true)
                MmsButton("${G.LEFT} Back", { selected = null; search = "" }, small = true, primary = false)
            }
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Total", total.toString(), Tints.of("pink", c.isDark), modifier = Modifier.weight(1f))
                    StatTile("Collected", collected.toString(), Tints.of("em", c.isDark), modifier = Modifier.weight(1f))
                    StatTile("Pending", pending.toString(), Tints.of("gold", c.isDark), modifier = Modifier.weight(1f))
                }
                if (total > 0) {
                    ProgressBar01(collected.toFloat() / total.toFloat(), tint = Tints.of("em", c.isDark))
                    Spacer(Modifier.height(10.dp))
                }
                if (tokens.isEmpty()) {
                    EmptyState("No tokens", "Tap Generate to create one token per active family")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tokens, key = { Format.long(it, "id") }) { row ->
                            val st = Format.str(row, "status")
                            MmsCard(onClick = {
                                if (st == "Pending") {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { repo.tokenCollect(Format.long(row, "id")) }
                                        reloadTokens(); toast("Collected ✓", ToastMsg.Kind.Success)
                                    }
                                } else {
                                    tokenAction = row; tokenAmount = Format.num(row, "amount").toString(); cancelReason = ""
                                }
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        CellText(
                                            Format.str(row, "token_code"), strong = true,
                                            sub = Format.str(row, "family_number") + " " + Format.str(row, "house_name")
                                        )
                                        if (Format.num(row, "amount") > 0) {
                                            androidx.compose.foundation.text.BasicText(
                                                Format.money(Format.num(row, "amount")),
                                                style = MmsType.caption.copy(color = c.mut)
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        StatusPill(st, st)
                                        if (st == "Pending") {
                                            MmsButton("Details", {
                                                tokenAction = row
                                                tokenAmount = Format.num(row, "amount").toString()
                                                cancelReason = ""
                                            }, small = true, primary = false)
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(90.dp)) }
                    }
                }
            }
        }
        if (tokenAction != null) {
            val row = tokenAction!!
            MmsDialog("Token ${Format.str(row, "token_code")}", onDismiss = { tokenAction = null }, compact = true) {
                DetailRow("Family", "${Format.str(row, "house_name")} (${Format.str(row, "family_number")})")
                DetailRow("Status", Format.str(row, "status"), strong = true)
                MmsInput(tokenAmount, { tokenAmount = it }, label = "Token amount", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                MmsButton("Save amount", {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                repo.tokenSetAmount(Format.long(row, "id"), tokenAmount.toDoubleOrNull() ?: 0.0)
                            }
                            tokenAction = null; reloadTokens(); toast("Amount saved", ToastMsg.Kind.Success)
                        } catch (e: Exception) {
                            toast(e.message ?: "Error", ToastMsg.Kind.Error)
                        }
                    }
                }, small = true, modifier = Modifier.fillMaxWidth())
                if (Format.str(row, "status") == "Pending") {
                    MmsButton("Collect now", {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.tokenCollect(Format.long(row, "id")) }
                            tokenAction = null; reloadTokens(); toast("Collected ✓", ToastMsg.Kind.Success)
                        }
                    }, small = true, modifier = Modifier.fillMaxWidth())
                    MmsInput(cancelReason, { cancelReason = it }, label = "Cancel reason")
                    MmsButton("Cancel token", {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                repo.tokenCancel(Format.long(row, "id"), cancelReason.ifBlank { "Cancelled" })
                            }
                            tokenAction = null; reloadTokens(); toast("Token cancelled", ToastMsg.Kind.Success)
                        }
                    }, small = true, danger = true, ghost = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
