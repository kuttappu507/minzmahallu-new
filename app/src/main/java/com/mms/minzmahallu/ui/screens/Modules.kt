package com.mms.minzmahallu.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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

// ---------------------------------------------------------------- scaffold

@Composable
fun ModuleScaffold(
    title: String,
    subtitle: String,
    filters: List<String> = listOf("All"),
    selectedFilter: String = "All",
    onFilter: (String) -> Unit = {},
    search: String? = null,
    onSearch: ((String) -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    addLabel: String = I18n.t("action_save"),
    loading: Boolean = false,
    extraActions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
            PageHeader(title, subtitle, T()) {
                extraActions()
                if (onAdd != null) MmsButton(addLabel, onAdd, small = true, icon = G.PLUS)
            }
            if (search != null && onSearch != null) {
                SearchField(search, onSearch, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
            if (filters.size > 1 || (filters.size == 1 && filters[0] != "All")) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    filters.forEach { f ->
                        FilterChip(f, f == selectedFilter, { onFilter(f) })
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (loading) LoadingList() else content()
        }
    }
}

// ---------------------------------------------------------------- receipts

/** Receipt detail + PDF export + share + WhatsApp delivery. Shared by all money modules. */
@Composable
fun ReceiptDialog(
    title: String,
    receiptNo: String,
    rows: List<Pair<String, String>>,
    messageText: String,
    waPhone: String,
    onDismiss: () -> Unit,
    toast: (String, ToastMsg.Kind) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = MmsApp.instance.repo
    var busy by remember { mutableStateOf(false) }
    val canWa = ShareUtil.normalizePhone(waPhone) != null

    fun makePdf(): kotlinx.coroutines.Job = scope.launch {
        busy = true
        try {
            val s = withContext(Dispatchers.IO) { repo.settingsLoad() }
            val org = listOf(
                Format.str(s, "mahallu_name").ifBlank { "Minz Mahallu" },
                listOf(Format.str(s, "village"), Format.str(s, "panchayath"), Format.str(s, "district")).filter { it.isNotBlank() }.joinToString(", "),
                Format.str(s, "phone")
            ).filter { it.isNotBlank() }
            val file = withContext(Dispatchers.IO) {
                PdfUtil.receiptPdf(ctx, title, org, rows, null, "receipt-$receiptNo.pdf")
            }
            ShareUtil.shareFile(ctx, file, "application/pdf", "Share receipt")
        } catch (e: Exception) {
            toast(e.message ?: "PDF failed", ToastMsg.Kind.Error)
        }
        busy = false
    }

    MmsDialog(title, onDismiss, compact = false) {
        rows.forEach { (k, v) -> DetailRow(k, v, strong = k.equals("Amount", true)) }
        Spacer(Modifier.height(6.dp))
        if (!canWa) {
            InfoBanner("No WhatsApp number on this family record — PDF and text share still work.", "warn")
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MmsButton("PDF ${G.SHARE}", { makePdf() }, small = true, modifier = Modifier.weight(1f), enabled = !busy)
            MmsButton("Share", { ShareUtil.shareText(ctx, messageText, "Share receipt") }, small = true, primary = false, modifier = Modifier.weight(1f))
        }
        if (canWa) {
            MmsButton(
                "Send via WhatsApp",
                { ShareUtil.openWhatsApp(ctx, waPhone, messageText) },
                small = true, primary = false, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------------------------------------------------------------- families

@Composable
fun FamiliesScreen(toast: (String, ToastMsg.Kind) -> Unit, initialSearch: String = "") {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf(initialSearch) }
    var status by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var showForm by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var archiveId by remember { mutableStateOf<Long?>(null) }
    var archiveReason by remember { mutableStateOf("") }
    // form fields
    var houseName by remember { mutableStateOf("") }
    var houseNumber by remember { mutableStateOf("") }
    var ward by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var altPhone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var waPhone by remember { mutableStateOf("") }
    var waOn by remember { mutableStateOf(true) }
    var famStatus by remember { mutableStateOf("Active") }
    val c = C()

    LaunchedEffect(initialSearch) { if (initialSearch.isNotBlank()) search = initialSearch }
    fun reload() = scope.launch {
        loading = true
        try {
            rows = withContext(Dispatchers.IO) { repo.familiesList(search, status).rows }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search, status) { reload() }

    fun openForm(row: Map<String, Any?>?) {
        editId = row?.let { Format.long(it, "id") }
        houseName = row?.let { Format.str(it, "house_name") } ?: ""
        houseNumber = row?.let { Format.str(it, "house_number") } ?: ""
        ward = row?.let { Format.str(it, "ward") } ?: ""
        area = row?.let { Format.str(it, "area") } ?: ""
        phone = row?.let { Format.str(it, "phone") } ?: ""
        altPhone = row?.let { Format.str(it, "alternative_phone") } ?: ""
        address = row?.let { Format.str(it, "address") } ?: ""
        pincode = row?.let { Format.str(it, "pincode") } ?: ""
        notes = row?.let { Format.str(it, "notes") } ?: ""
        waPhone = row?.let { Format.str(it, "whatsapp_phone") } ?: ""
        waOn = row?.let { Format.long(it, "whatsapp_enabled") != 0L } ?: true
        famStatus = row?.let { Format.str(it, "status") }?.ifBlank { "Active" } ?: "Active"
        showForm = true
    }

    ModuleScaffold(
        I18n.t("family_title"), I18n.t("family_subtitle"),
        listOf("All", "Active", "Inactive", "Archived"), status, { status = it },
        search, { search = it }, loading = loading,
        onAdd = { openForm(null) }, addLabel = I18n.t("add_family")
    ) {
        if (rows.isEmpty()) {
            EmptyState(
                I18n.t("common_no_data").ifBlank { "No records" },
                I18n.t("family_empty_sub").ifBlank { "Add your first family to get started" },
                I18n.t("add_family")
            ) { openForm(null) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { Format.long(it, "id") }) { row ->
                    MmsCard(onClick = { detailId = Format.long(row, "id") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TintTile(
                                Format.str(row, "house_name").ifBlank { "F" }.take(1),
                                Tints.of("em", c.isDark), 42.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                CellText(
                                    Format.str(row, "house_name").ifBlank { "—" },
                                    strong = true, sub = Format.str(row, "family_number")
                                )
                                androidx.compose.foundation.text.BasicText(
                                    listOf(Format.str(row, "ward"), Format.str(row, "area"), Format.str(row, "phone")).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MmsType.caption.copy(color = c.fnt)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                StatusPill(Format.str(row, "status"), Format.str(row, "status"))
                                Spacer(Modifier.height(4.dp))
                                androidx.compose.foundation.text.BasicText(
                                    "${Format.long(row, "member_count")} members",
                                    style = MmsType.caption.copy(color = c.em, fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    // ---- detail
    if (detailId != null) {
        var detail by remember(detailId) { mutableStateOf<Map<String, Any?>>(emptyMap()) }
        var detailLoading by remember(detailId) { mutableStateOf(true) }
        LaunchedEffect(detailId) {
            detailLoading = true
            try {
                detail = withContext(Dispatchers.IO) { repo.familyDetail(detailId!!) }
            } catch (e: Exception) {
                toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
            }
            detailLoading = false
        }
        val fam = (detail["family"] as? Map<String, Any?>) ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val members = (detail["members"] as? List<Map<String, Any?>>) ?: emptyList()
        val sub = (detail["subscription"] as? Map<String, Any?>) ?: emptyMap()
        val ctx = LocalContext.current
        MmsDialog(
            Format.str(fam, "house_name").ifBlank { "Family" },
            onDismiss = { detailId = null }
        ) {
            if (detailLoading) {
                LoadingList(2)
            } else {
                DetailRow("Family No", Format.str(fam, "family_number"), mono = true)
                DetailRow("House", "${Format.str(fam, "house_name")} ${Format.str(fam, "house_number")}".trim())
                DetailRow("Ward / Area", "${Format.str(fam, "ward")} / ${Format.str(fam, "area")}")
                DetailRow("Phone", ShareUtil.prettyPhone(Format.str(fam, "phone")))
                DetailRow("WhatsApp", ShareUtil.prettyPhone(Format.str(fam, "whatsapp_phone").ifBlank { Format.str(fam, "phone") }))
                DetailRow("Address", Format.str(fam, "address"))
                DetailRow("Status", Format.str(fam, "status"), strong = true)
                if (sub.isNotEmpty()) {
                    SectionLabel("Subscription")
                    DetailRow("Plan", Format.str(sub, "plan_name"))
                    DetailRow(
                        "Paid",
                        "${Format.money(Format.num(sub, "amount_paid"))} / ${Format.money(Format.num(sub, "amount"))}"
                    )
                    DetailRow("Status", Format.str(sub, "status"), strong = true)
                }
                SectionLabel("Members (${members.size})")
                if (members.isEmpty()) {
                    androidx.compose.foundation.text.BasicText("No members yet", style = MmsType.caption.copy(color = c.fnt))
                } else {
                    members.forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                CellText(Format.str(m, "name"), strong = true, sub = Format.str(m, "member_code"))
                            }
                            StatusPill(Format.str(m, "relationship").ifBlank { "Member" }, "default")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MmsButton(I18n.t("action_edit"), { openForm(fam); detailId = null }, small = true, primary = false, modifier = Modifier.weight(1f))
                    MmsButton("WhatsApp", {
                        val p = Format.str(fam, "whatsapp_phone").ifBlank { Format.str(fam, "phone") }
                        ShareUtil.openWhatsApp(ctx, p, "Assalamu Alaikum from ${I18n.t("app_name")}")
                    }, small = true, primary = false, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (Format.str(fam, "status") == "Archived") {
                        MmsButton("Restore", {
                            scope.launch {
                                withContext(Dispatchers.IO) { repo.familyRestore(detailId!!) }
                                detailId = null; reload(); toast("Family restored", ToastMsg.Kind.Success)
                            }
                        }, small = true, modifier = Modifier.weight(1f))
                    } else {
                        MmsButton("Archive", {
                            archiveId = detailId; archiveReason = ""
                        }, small = true, danger = true, ghost = true, modifier = Modifier.weight(1f))
                    }
                    MmsButton("Call", {
                        ShareUtil.dial(ctx, Format.str(fam, "phone"))
                    }, small = true, primary = false, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // ---- archive confirm
    if (archiveId != null) {
        MmsDialog("Archive family", onDismiss = { archiveId = null }, confirmLabel = "Archive", danger = true, compact = true,
            onConfirm = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repo.familyArchive(archiveId!!, archiveReason.ifBlank { "Archived" }) }
                        archiveId = null; detailId = null; reload(); toast("Family archived", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                }
            }) {
            androidx.compose.foundation.text.BasicText(
                "Archived families are hidden from active lists but their history is preserved.",
                style = MmsType.bodySm.copy(color = c.mut)
            )
            MmsInput(archiveReason, { archiveReason = it }, label = "Reason", placeholder = "Moved out / merged…")
        }
    }

    // ---- form
    if (showForm) {
        MmsDialog(
            if (editId == null) I18n.t("add_family") else I18n.t("action_edit"),
            onDismiss = { showForm = false },
            onConfirm = {
                scope.launch {
                    try {
                        val data = mapOf(
                            "houseName" to houseName, "houseNumber" to houseNumber, "ward" to ward,
                            "area" to area, "phone" to phone, "altPhone" to altPhone, "address" to address,
                            "pincode" to pincode, "notes" to notes, "status" to famStatus,
                            "whatsappPhone" to waPhone, "whatsappEnabled" to if (waOn) 1 else 0
                        )
                        withContext(Dispatchers.IO) {
                            if (editId == null) repo.familyCreate(data) else repo.familyUpdate(editId!!, data)
                        }
                        showForm = false; reload(); toast(I18n.t("action_save") + " ✓", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                }
            },
            confirmEnabled = houseName.isNotBlank()
        ) {
            MmsInput(houseName, { houseName = it }, label = I18n.t("family_house_name"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(houseNumber, { houseNumber = it }, label = I18n.t("family_house_number"), modifier = Modifier.weight(1f))
                MmsInput(pincode, { pincode = it }, label = "Pincode", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(ward, { ward = it }, label = I18n.t("family_ward"), modifier = Modifier.weight(1f))
                MmsInput(area, { area = it }, label = I18n.t("family_area"), modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(phone, { phone = it }, label = I18n.t("family_phone"), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                MmsInput(altPhone, { altPhone = it }, label = "Alt phone", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
            MmsInput(waPhone, { waPhone = it }, label = "WhatsApp number", placeholder = "Same as phone if blank", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText("WhatsApp enabled", style = MmsType.bodySm.copy(color = c.tx), modifier = Modifier.weight(1f))
                FilterChip("On", waOn, { waOn = true })
                Spacer(Modifier.width(6.dp))
                FilterChip("Off", !waOn, { waOn = false })
            }
            MmsInput(address, { address = it }, label = I18n.t("family_address"), singleLine = false)
            MmsInput(notes, { notes = it }, label = "Notes", singleLine = false)
            if (editId != null) {
                MmsSelect(famStatus, listOf("Active", "Inactive", "Archived"), { famStatus = it }, label = "Status")
            }
        }
    }
}

// ---------------------------------------------------------------- members

@Composable
fun MembersScreen(toast: (String, ToastMsg.Kind) -> Unit, initialSearch: String = "") {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf(initialSearch) }
    var status by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var families by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var showForm by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var archiveId by remember { mutableStateOf<Long?>(null) }
    var archiveReason by remember { mutableStateOf("") }
    // form
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Other") }
    var dob by remember { mutableStateOf("") }
    var blood by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("") }
    var marital by remember { mutableStateOf("Single") }
    var fatherName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var emergency by remember { mutableStateOf("") }
    var memStatus by remember { mutableStateOf("Active") }
    var familyId by remember { mutableStateOf(0L) }
    var familyLabel by remember { mutableStateOf("") }
    val c = C()

    LaunchedEffect(initialSearch) { if (initialSearch.isNotBlank()) search = initialSearch }
    fun reload() = scope.launch {
        loading = true
        try {
            rows = withContext(Dispatchers.IO) { repo.membersList(search, status = status).rows }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search, status) { reload() }
    LaunchedEffect(Unit) {
        families = withContext(Dispatchers.IO) { repo.familiesList(status = "Active").rows }
    }

    fun openForm(row: Map<String, Any?>?) {
        editId = row?.let { Format.long(it, "id") }
        name = row?.let { Format.str(it, "name") } ?: ""
        gender = row?.let { Format.str(it, "gender") }?.ifBlank { "Male" } ?: "Male"
        mobile = row?.let { Format.str(it, "mobile") } ?: ""
        email = row?.let { Format.str(it, "email") } ?: ""
        relationship = row?.let { Format.str(it, "relationship") }?.ifBlank { "Other" } ?: "Other"
        dob = row?.let { Format.str(it, "date_of_birth") } ?: ""
        blood = row?.let { Format.str(it, "blood_group") } ?: ""
        occupation = row?.let { Format.str(it, "occupation") } ?: ""
        marital = row?.let { Format.str(it, "marital_status") }?.ifBlank { "Single" } ?: "Single"
        fatherName = row?.let { Format.str(it, "father_name") } ?: ""
        address = row?.let { Format.str(it, "address") } ?: ""
        emergency = row?.let { Format.str(it, "emergency_contact") } ?: ""
        memStatus = row?.let { Format.str(it, "status") }?.ifBlank { "Active" } ?: "Active"
        familyId = row?.let { Format.long(it, "family_id") } ?: 0L
        familyLabel = if (row == null) "" else "${Format.str(row, "family_number")} — ${Format.str(row, "family_house_name").ifBlank { Format.str(row, "house_name") }}"
        showForm = true
    }

    ModuleScaffold(
        I18n.t("member_title"), I18n.t("member_subtitle"),
        listOf("All", "Active", "Inactive", "Deceased"), status, { status = it },
        search, { search = it }, loading = loading,
        onAdd = { openForm(null) }, addLabel = I18n.t("add_member")
    ) {
        if (rows.isEmpty()) {
            EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "", I18n.t("add_member")) { openForm(null) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { Format.long(it, "id") }) { row ->
                    MmsCard(onClick = { detailId = Format.long(row, "id") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TintTile(Format.str(row, "name").ifBlank { "M" }.take(1), Tints.of("teal", c.isDark), 42.dp)
                            Spacer(Modifier.width(12.dp))
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
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    // ---- detail with relations
    if (detailId != null) {
        var full by remember(detailId) { mutableStateOf<Map<String, Any?>>(emptyMap()) }
        var relations by remember(detailId) { mutableStateOf<Map<String, Any?>>(emptyMap()) }
        var dl by remember(detailId) { mutableStateOf(true) }
        LaunchedEffect(detailId) {
            dl = true
            try {
                withContext(Dispatchers.IO) {
                    full = repo.memberFull(detailId!!) ?: emptyMap()
                    relations = repo.memberRelations(detailId!!) ?: emptyMap()
                }
            } catch (e: Exception) {
                toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
            }
            dl = false
        }
        val ctx = LocalContext.current
        MmsDialog(Format.str(full, "name").ifBlank { "Member" }, onDismiss = { detailId = null }) {
            if (dl) {
                LoadingList(2)
            } else {
                DetailRow("Code", Format.str(full, "member_code"), mono = true)
                DetailRow("Family", "${Format.str(full, "house_name")} (${Format.str(full, "family_number")})")
                DetailRow("Gender", Format.str(full, "gender"))
                DetailRow("DOB", Format.str(full, "date_of_birth"))
                DetailRow("Mobile", ShareUtil.prettyPhone(Format.str(full, "mobile")))
                DetailRow("Relationship", Format.str(full, "relationship"))
                DetailRow("Father", Format.str(full, "father_name"))
                DetailRow("Occupation", Format.str(full, "occupation"))
                DetailRow("Blood", Format.str(full, "blood_group"))
                DetailRow("Marital", Format.str(full, "marital_status"))
                DetailRow("Status", Format.str(full, "status"), strong = true)
                SectionLabel("Family links")
                RelationRow("Father", relations["father"] as? Map<String, Any?>) { detailId = it }
                RelationRow("Mother", relations["mother"] as? Map<String, Any?>) { detailId = it }
                RelationRow("Spouse", relations["spouse"] as? Map<String, Any?>) { detailId = it }
                @Suppress("UNCHECKED_CAST")
                val children = (relations["children"] as? List<Map<String, Any?>>) ?: emptyList()
                if (children.isNotEmpty()) {
                    androidx.compose.foundation.text.BasicText(
                        "Children (${children.size})",
                        style = MmsType.caption.copy(color = c.fnt),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                    children.forEach { ch ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .mmsClickable { detailId = Format.long(ch, "id") }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CellText(Format.str(ch, "name"), strong = true, sub = Format.str(ch, "member_code"))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MmsButton(I18n.t("action_edit"), {
                        val id = detailId
                        detailId = null
                        val row = rows.firstOrNull { Format.long(it, "id") == id }
                        openForm(row)
                    }, small = true, primary = false, modifier = Modifier.weight(1f))
                    MmsButton("Call", { ShareUtil.dial(ctx, Format.str(full, "mobile")) }, small = true, primary = false, modifier = Modifier.weight(1f))
                    MmsButton("Archive", { archiveId = detailId; archiveReason = "" }, small = true, danger = true, ghost = true, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (archiveId != null) {
        MmsDialog("Archive member", onDismiss = { archiveId = null }, confirmLabel = "Archive", danger = true, compact = true,
            onConfirm = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repo.memberArchive(archiveId!!, archiveReason.ifBlank { "Archived" }) }
                        archiveId = null; detailId = null; reload(); toast("Member archived", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                }
            }) {
            androidx.compose.foundation.text.BasicText("The member record is preserved for history.", style = MmsType.bodySm.copy(color = c.mut))
            MmsInput(archiveReason, { archiveReason = it }, label = "Reason")
        }
    }

    // ---- form
    if (showForm) {
        val famOptions = families.map { "${Format.str(it, "family_number")} — ${Format.str(it, "house_name")}" }
        MmsDialog(
            if (editId == null) I18n.t("add_member") else I18n.t("action_edit"),
            onDismiss = { showForm = false },
            onConfirm = {
                scope.launch {
                    try {
                        val data = mapOf(
                            "name" to name, "gender" to gender, "mobile" to mobile, "email" to email,
                            "relationship" to relationship, "familyId" to familyId, "status" to memStatus,
                            "dateOfBirth" to dob, "bloodGroup" to blood, "occupation" to occupation,
                            "maritalStatus" to marital, "fatherName" to fatherName, "address" to address,
                            "emergencyContact" to emergency
                        )
                        withContext(Dispatchers.IO) {
                            if (editId == null) repo.memberCreate(data) else repo.memberUpdate(editId!!, data)
                        }
                        showForm = false; reload(); toast("Saved ✓", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                }
            },
            confirmEnabled = name.isNotBlank() && familyId > 0
        ) {
            MmsInput(name, { name = it }, label = I18n.t("member_name"))
            MmsSelect(
                familyLabel.ifBlank { "Select family" },
                famOptions,
                { label ->
                    familyLabel = label
                    val code = label.substringBefore(" —")
                    familyId = families.firstOrNull { Format.str(it, "family_number") == code }?.let { Format.long(it, "id") } ?: 0
                },
                label = I18n.t("member_family")
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsSelect(gender, listOf("Male", "Female", "Other"), { gender = it }, label = I18n.t("member_gender"), modifier = Modifier.weight(1f))
                MmsSelect(relationship, repo.memberRelationships(), { relationship = it }, label = I18n.t("member_relationship"), modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(mobile, { mobile = it }, label = I18n.t("member_mobile"), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                MmsDateField(dob, { dob = it }, label = "Date of birth", modifier = Modifier.weight(1f))
            }
            MmsInput(fatherName, { fatherName = it }, label = "Father's name")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsSelect(blood, listOf("", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"), { blood = it }, label = "Blood group", modifier = Modifier.weight(1f))
                MmsSelect(marital, listOf("Single", "Married", "Divorced", "Widowed"), { marital = it }, label = "Marital status", modifier = Modifier.weight(1f))
            }
            MmsInput(occupation, { occupation = it }, label = "Occupation")
            MmsInput(email, { email = it }, label = "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            MmsInput(emergency, { emergency = it }, label = "Emergency contact", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            MmsInput(address, { address = it }, label = "Address", singleLine = false)
            if (editId != null) {
                MmsSelect(memStatus, listOf("Active", "Inactive", "Deceased"), { memStatus = it }, label = "Status")
            }
        }
    }
}

@Composable
private fun RelationRow(label: String, person: Map<String, Any?>?, onOpen: (Long) -> Unit) {
    val c = C()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.text.BasicText(label, style = MmsType.caption.copy(color = c.fnt), modifier = Modifier.width(64.dp))
        if (person == null) {
            androidx.compose.foundation.text.BasicText("—", style = MmsType.bodySm.copy(color = c.fnt))
        } else {
            Box(
                Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .mmsClickable { onOpen(Format.long(person, "id")) }
            ) {
                androidx.compose.foundation.text.BasicText(
                    "${Format.str(person, "name")} · ${Format.str(person, "member_code")} ${G.RIGHT}",
                    style = MmsType.bodySm.copy(color = c.emd, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- subscriptions

@Composable
fun SubscriptionsScreen(toast: (String, ToastMsg.Kind) -> Unit, initialSearch: String = "") {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf(initialSearch) }
    var status by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var payRow by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var receiptRow by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var historyId by remember { mutableStateOf<Long?>(null) }
    var payAmount by remember { mutableStateOf("") }
    var payMethod by remember { mutableStateOf("Cash") }
    var payDate by remember { mutableStateOf(Format.today()) }
    var payRemarks by remember { mutableStateOf("") }
    var collected by remember { mutableStateOf(0.0) }
    var pending by remember { mutableStateOf(0.0) }
    val c = C()

    LaunchedEffect(initialSearch) { if (initialSearch.isNotBlank()) search = initialSearch }
    fun reload() = scope.launch {
        loading = true
        try {
            withContext(Dispatchers.IO) {
                rows = repo.subscriptionsList(search, status).rows
                collected = repo.subscriptionsTotalCollected()
                pending = repo.subscriptionsTotalPending()
            }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search, status) { reload() }

    ModuleScaffold(
        I18n.t("sub_title"), "Household subscription accounts",
        listOf("All", "Paid", "Pending", "Overdue", "Partial"), status, { status = it },
        search, { search = it }, loading = loading,
        extraActions = {
            MmsButton(I18n.t("sub_mark_overdue"), {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.markOverdue() }
                    reload(); toast("Marked overdue", ToastMsg.Kind.Info)
                }
            }, small = true, primary = false)
        }
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Collected", Format.moneyShort(collected), Tints.of("em", c.isDark), modifier = Modifier.weight(1f))
                StatTile("Pending", Format.moneyShort(pending), Tints.of("gold", c.isDark), modifier = Modifier.weight(1f))
            }
            if (rows.isEmpty()) {
                EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "Subscriptions are auto-created for active families")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { Format.long(it, "id") }) { row ->
                        val st = Format.str(row, "status")
                        MmsCard(onClick = {
                            if (st == "Paid" && Format.str(row, "receipt_number").isNotBlank()) receiptRow = row
                            else {
                                payRow = row
                                val due = Format.num(row, "amount") + Format.num(row, "arrears") - Format.num(row, "advance") - Format.num(row, "amount_paid")
                                payAmount = "%.2f".format(due.coerceAtLeast(0.0))
                                payMethod = "Cash"; payDate = Format.today(); payRemarks = ""
                            }
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    CellText(
                                        Format.str(row, "house_name").ifBlank { Format.str(row, "family_number") },
                                        strong = true, sub = Format.str(row, "family_number")
                                    )
                                    androidx.compose.foundation.text.BasicText(
                                        "${Format.money(Format.num(row, "amount_paid"))} / ${Format.money(Format.num(row, "amount"))}" +
                                            (if (Format.num(row, "arrears") > 0) "  ·  Arrears ${Format.money(Format.num(row, "arrears"))}" else ""),
                                        style = MmsType.caption.copy(color = c.mut)
                                    )
                                    if (Format.str(row, "receipt_number").isNotBlank()) {
                                        androidx.compose.foundation.text.BasicText(
                                            "Receipt ${Format.str(row, "receipt_number")} — tap to view",
                                            style = MmsType.caption.copy(color = c.em)
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    StatusPill(st, st)
                                    Spacer(Modifier.height(6.dp))
                                    MmsButton("History", { historyId = Format.long(row, "id") }, small = true, primary = false)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }

    // ---- pay
    if (payRow != null) {
        val row = payRow!!
        val due = Format.num(row, "amount") + Format.num(row, "arrears") - Format.num(row, "advance") - Format.num(row, "amount_paid")
        MmsDialog("Record Payment", onDismiss = { payRow = null }, confirmLabel = "Collect", onConfirm = {
            scope.launch {
                try {
                    val now = payAmount.toDoubleOrNull() ?: 0.0
                    require(now > 0) { "Enter an amount greater than zero" }
                    val res = withContext(Dispatchers.IO) {
                        repo.subscriptionPay(Format.long(row, "id"), Format.num(row, "amount_paid") + now, payMethod, payDate, payRemarks)
                    }
                    payRow = null; reload()
                    toast("Receipt ${res["receiptNumber"]}", ToastMsg.Kind.Success)
                } catch (e: Exception) {
                    toast(e.message ?: "Error", ToastMsg.Kind.Error)
                }
            }
        }) {
            DetailRow("Family", "${Format.str(row, "house_name")} (${Format.str(row, "family_number")})")
            DetailRow("Total due", Format.money(due.coerceAtLeast(0.0)), strong = true)
            DetailRow("Already paid", Format.money(Format.num(row, "amount_paid")))
            MmsInput(payAmount, { payAmount = it }, label = I18n.t("sub_amount"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            MmsSelect(payMethod, listOf("Cash", "UPI", "Bank Transfer", "Cheque", "Card", "Other"), { payMethod = it }, label = I18n.t("sub_method"))
            MmsDateField(payDate, { payDate = it }, label = "Payment date")
            MmsInput(payRemarks, { payRemarks = it }, label = "Remarks", singleLine = false)
        }
    }

    // ---- receipt
    if (receiptRow != null) {
        val row = receiptRow!!
        val id = Format.long(row, "id")
        var msg by remember(id) { mutableStateOf("") }
        LaunchedEffect(id) { msg = withContext(Dispatchers.IO) { repo.receiptText("subscription", id) } }
        ReceiptDialog(
            "Subscription Receipt",
            Format.str(row, "receipt_number"),
            listOf(
                "Receipt" to Format.str(row, "receipt_number"),
                "Date" to Format.str(row, "payment_date"),
                "Family" to "${Format.str(row, "house_name")} (${Format.str(row, "family_number")})",
                "Amount" to Format.money(Format.num(row, "amount_paid")),
                "Method" to Format.str(row, "payment_method"),
                "Status" to Format.str(row, "status")
            ),
            msg,
            Format.str(row, "family_phone"),
            onDismiss = { receiptRow = null },
            toast = toast
        )
    }

    // ---- history
    if (historyId != null) {
        var pays by remember(historyId) { mutableStateOf(listOf<Map<String, Any?>>()) }
        LaunchedEffect(historyId) {
            pays = withContext(Dispatchers.IO) { repo.subscriptionPayments(historyId!!) }
        }
        MmsDialog("Payment history", onDismiss = { historyId = null }, compact = true) {
            if (pays.isEmpty()) {
                androidx.compose.foundation.text.BasicText("No recorded payments yet", style = MmsType.bodySm.copy(color = c.mut))
            } else {
                pays.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            CellText(Format.money(Format.num(p, "amount")), strong = true, sub = Format.str(p, "receipt_number"))
                        }
                        androidx.compose.foundation.text.BasicText(
                            Format.str(p, "payment_date"),
                            style = MmsType.caption.copy(color = c.fnt)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- donations

@Composable
fun DonationsScreen(toast: (String, ToastMsg.Kind) -> Unit, initialSearch: String = "") {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var search by remember { mutableStateOf(initialSearch) }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var cats by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var families by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var show by remember { mutableStateOf(false) }
    var receiptRow by remember { mutableStateOf<Map<String, Any?>>(null) }
    var editRow by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var manageCats by remember { mutableStateOf(false) }
    var newCat by remember { mutableStateOf("") }
    // form
    var donor by remember { mutableStateOf("") }
    var donorPhone by remember { mutableStateOf("") }
    var donorAddr by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf(0L) }
    var catLabel by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Cash") }
    var date by remember { mutableStateOf(Format.today()) }
    var purpose by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var txnRef by remember { mutableStateOf("") }
    var linkFamId by remember { mutableStateOf(0L) }
    var linkFamLabel by remember { mutableStateOf("") }
    // edit-auth
    var adminPwd by remember { mutableStateOf("") }
    var editReason by remember { mutableStateOf("") }
    var total by remember { mutableStateOf(0.0) }
    val c = C()

    LaunchedEffect(initialSearch) { if (initialSearch.isNotBlank()) search = initialSearch }
    fun reload() = scope.launch {
        loading = true
        try {
            withContext(Dispatchers.IO) {
                rows = repo.donationsList(search).rows
                cats = repo.donationCategories()
                total = repo.donationsTotalThisMonth()
                if (families.isEmpty()) families = repo.familiesList(status = "Active").rows
                if (catId == 0L && cats.isNotEmpty()) {
                    catId = Format.long(cats.first(), "id"); catLabel = Format.str(cats.first(), "name")
                }
            }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search) { reload() }

    fun openForm() {
        donor = ""; donorPhone = ""; donorAddr = ""; amount = ""; method = "Cash"
        date = Format.today(); purpose = ""; remarks = ""; txnRef = ""
        linkFamId = 0; linkFamLabel = ""
        show = true
    }

    ModuleScaffold(
        I18n.t("don_title"), I18n.t("don_subtitle"),
        search = search, onSearch = { search = it }, loading = loading,
        onAdd = { openForm() }, addLabel = I18n.t("dash_qa_add_donation"),
        extraActions = {
            MmsButton("Categories", { manageCats = true; newCat = "" }, small = true, primary = false)
        }
    ) {
        Column {
            StatTile(
                I18n.t("dash_donations_month"), Format.money(total),
                Tints.of("pink", c.isDark), I18n.t("dash_this_month"),
                Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )
            if (rows.isEmpty()) {
                EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "", I18n.t("dash_qa_add_donation")) { openForm() }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { Format.long(it, "id") }) { row ->
                        MmsCard(onClick = { receiptRow = row }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    CellText(Format.str(row, "donor_name"), strong = true, sub = Format.str(row, "receipt_number"))
                                    androidx.compose.foundation.text.BasicText(
                                        Format.str(row, "category_name") + " · " + Format.str(row, "donation_date"),
                                        style = MmsType.caption.copy(color = c.fnt)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    androidx.compose.foundation.text.BasicText(
                                        Format.money(Format.num(row, "amount")),
                                        style = MmsType.body.copy(color = c.em, fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    MmsButton("Edit", { editRow = row }, small = true, primary = false)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }

    // ---- create
    if (show) {
        MmsDialog(I18n.t("dash_qa_add_donation"), onDismiss = { show = false }, onConfirm = {
            scope.launch {
                try {
                    val res = withContext(Dispatchers.IO) {
                        repo.donationCreate(
                            mapOf(
                                "donorName" to donor, "donorPhone" to donorPhone, "donorAddress" to donorAddr,
                                "amount" to (amount.toDoubleOrNull() ?: 0.0), "categoryId" to catId,
                                "paymentMethod" to method, "donationDate" to date, "purpose" to purpose,
                                "remarks" to remarks, "transactionRef" to txnRef,
                                "familyId" to if (linkFamId > 0) linkFamId else null
                            )
                        )
                    }
                    show = false; reload(); toast("Receipt ${res["receiptNumber"]}", ToastMsg.Kind.Success)
                } catch (e: Exception) {
                    toast(e.message ?: "Error", ToastMsg.Kind.Error)
                }
            }
        }, confirmEnabled = donor.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0 && catId > 0) {
            MmsInput(donor, { donor = it }, label = "Donor name")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(donorPhone, { donorPhone = it }, label = "Donor phone", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                MmsInput(amount, { amount = it }, label = I18n.t("sub_amount"), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            MmsSelect(catLabel.ifBlank { "Category" }, cats.map { Format.str(it, "name") }, {
                catLabel = it; catId = cats.firstOrNull { r -> Format.str(r, "name") == it }?.let { r -> Format.long(r, "id") } ?: 0
            }, label = "Category")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsSelect(method, listOf("Cash", "UPI", "Bank Transfer", "Cheque", "Card", "Other"), { method = it }, label = I18n.t("sub_method"), modifier = Modifier.weight(1f))
                MmsDateField(date, { date = it }, label = "Date", modifier = Modifier.weight(1f))
            }
            MmsSelect(
                linkFamLabel.ifBlank { "No family link" },
                listOf("No family link") + families.map { "${Format.str(it, "family_number")} — ${Format.str(it, "house_name")}" },
                { label ->
                    linkFamLabel = if (label == "No family link") "" else label
                    linkFamId = if (label == "No family link") 0 else {
                        val code = label.substringBefore(" —")
                        families.firstOrNull { Format.str(it, "family_number") == code }?.let { Format.long(it, "id") } ?: 0
                    }
                },
                label = "Link family (for WhatsApp receipt)"
            )
            MmsInput(purpose, { purpose = it }, label = "Purpose")
            MmsInput(txnRef, { txnRef = it }, label = "Transaction ref (UPI / bank)")
            MmsInput(donorAddr, { donorAddr = it }, label = "Donor address", singleLine = false)
            MmsInput(remarks, { remarks = it }, label = "Remarks", singleLine = false)
        }
    }

    // ---- receipt
    if (receiptRow != null) {
        val row = receiptRow!!
        val id = Format.long(row, "id")
        var msg by remember(id) { mutableStateOf("") }
        var full by remember(id) { mutableStateOf<Map<String, Any?>>(row) }
        LaunchedEffect(id) {
            withContext(Dispatchers.IO) {
                msg = repo.receiptText("donation", id)
                full = repo.donationGet(id) ?: row
            }
        }
        val linkedPhone = families.firstOrNull { Format.long(it, "id") == Format.long(full, "family_id") }?.let {
            Format.str(it, "whatsapp_phone").ifBlank { Format.str(it, "phone") }
        } ?: ""
        ReceiptDialog(
            "Donation Receipt",
            Format.str(row, "receipt_number"),
            listOf(
                "Receipt" to Format.str(row, "receipt_number"),
                "Date" to Format.str(row, "donation_date"),
                "Donor" to Format.str(row, "donor_name"),
                "Category" to Format.str(row, "category_name"),
                "Amount" to Format.money(Format.num(row, "amount")),
                "Method" to Format.str(row, "payment_method")
            ),
            msg,
            linkedPhone.ifBlank { Format.str(full, "donor_phone") },
            onDismiss = { receiptRow = null },
            toast = toast
        )
    }

    // ---- edit (admin protected)
    if (editRow != null) {
        val row = editRow!!
        var eDonor by remember(row) { mutableStateOf(Format.str(row, "donor_name")) }
        var eAmount by remember(row) { mutableStateOf(Format.num(row, "amount").toString()) }
        var eMethod by remember(row) { mutableStateOf(Format.str(row, "payment_method").ifBlank { "Cash" }) }
        var eDate by remember(row) { mutableStateOf(Format.str(row, "donation_date")) }
        LaunchedEffect(row) { adminPwd = ""; editReason = "" }
        MmsDialog("Edit donation", onDismiss = { editRow = null }, confirmLabel = "Save (admin)", onConfirm = {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        repo.donationUpdate(
                            Format.long(row, "id"),
                            mapOf(
                                "donorName" to eDonor, "donorPhone" to Format.str(row, "donor_phone"),
                                "donorAddress" to Format.str(row, "donor_address"),
                                "familyId" to Format.long(row, "family_id").let { if (it > 0) it else null },
                                "memberId" to null, "categoryId" to Format.long(row, "category_id"),
                                "amount" to (eAmount.toDoubleOrNull() ?: 0.0), "donationDate" to eDate,
                                "purpose" to Format.str(row, "purpose"), "paymentMethod" to eMethod,
                                "transactionRef" to Format.str(row, "transaction_ref"),
                                "remarks" to Format.str(row, "remarks")
                            ),
                            adminPwd, editReason
                        )
                    }
                    editRow = null; reload(); toast("Donation updated", ToastMsg.Kind.Success)
                } catch (e: Exception) {
                    toast(e.message ?: "Error", ToastMsg.Kind.Error)
                }
            }
        }, compact = true) {
            InfoBanner("Financial records need an Administrator password plus a reason to edit.", "warn")
            MmsInput(eDonor, { eDonor = it }, label = "Donor name")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(eAmount, { eAmount = it }, label = "Amount", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                MmsDateField(eDate, { eDate = it }, label = "Date", modifier = Modifier.weight(1f))
            }
            MmsSelect(eMethod, listOf("Cash", "UPI", "Bank Transfer", "Cheque", "Card", "Other"), { eMethod = it }, label = "Method")
            MmsInput(editReason, { editReason = it }, label = "Reason for edit")
            MmsInput(adminPwd, { adminPwd = it }, label = "Administrator password", password = true)
        }
    }

    // ---- categories
    if (manageCats) {
        MmsDialog("Donation categories", onDismiss = { manageCats = false }, compact = true) {
            cats.forEach { cat ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicText(
                        Format.str(cat, "name"),
                        style = MmsType.bodySm.copy(color = c.tx, fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f)
                    )
                    StatusPill(if (Format.long(cat, "is_active") == 1L) "Active" else "Inactive", if (Format.long(cat, "is_active") == 1L) "Active" else "Rejected")
                }
            }
            Spacer(Modifier.height(6.dp))
            MmsInput(newCat, { newCat = it }, label = "New category", placeholder = "e.g. Ramadan Fund")
            MmsButton("Add category", {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repo.donationCreateCategory(newCat.trim()) }
                        newCat = ""
                        cats = withContext(Dispatchers.IO) { repo.donationCategories() }
                        toast("Category added", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                }
            }, small = true, modifier = Modifier.fillMaxWidth(), enabled = newCat.isNotBlank())
        }
    }
}

// ---------------------------------------------------------------- accounting

@Composable
fun AccountingScreen(toast: (String, ToastMsg.Kind) -> Unit) {
    val repo = MmsApp.instance.repo
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("All") }
    var rows by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }
    var accounts by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var income by remember { mutableStateOf(0.0) }
    var expense by remember { mutableStateOf(0.0) }
    var balance by remember { mutableStateOf(0.0) }
    var show by remember { mutableStateOf(false) }
    var detailRow by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var voidRow by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var adminPwd by remember { mutableStateOf("") }
    // form
    var txnType by remember { mutableStateOf("Expense") }
    var amount by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Cash") }
    var date by remember { mutableStateOf(Format.today()) }
    var payee by remember { mutableStateOf("") }
    var billNo by remember { mutableStateOf("") }
    var txnRef by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf(0L) }
    var accountLabel by remember { mutableStateOf("") }
    val c = C()

    fun reload() = scope.launch {
        loading = true
        try {
            withContext(Dispatchers.IO) {
                rows = if (type == "Voided") repo.voidedTransactions()
                else repo.accountingList(search, type).rows
                income = repo.accountingTotalIncome()
                expense = repo.accountingTotalExpense()
                balance = repo.accountingBalance()
                if (accounts.isEmpty()) accounts = repo.ledgerAccounts()
            }
        } catch (e: Exception) {
            toast(e.message ?: "Load failed", ToastMsg.Kind.Error)
        }
        loading = false
    }
    LaunchedEffect(search, type) { reload() }

    val typeAccounts = accounts.filter { Format.str(it, "type") == txnType }
    fun openForm() {
        amount = ""; desc = ""; payee = ""; billNo = ""; txnRef = ""; category = ""
        txnType = if (type == "Income") "Income" else "Expense"
        method = "Cash"; date = Format.today()
        accountId = 0; accountLabel = ""
        show = true
    }

    ModuleScaffold(
        "Accounting", "Income, expenses & ledger",
        listOf("All", "Income", "Expense", "Voided"), type, { type = it },
        search, { search = it }, loading = loading,
        onAdd = { openForm() }, addLabel = "Add entry"
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Income", Format.moneyShort(income), Tints.of("em", c.isDark), modifier = Modifier.weight(1f))
                StatTile("Expense", Format.moneyShort(expense), Tints.of("rose", c.isDark), modifier = Modifier.weight(1f))
                StatTile("Balance", Format.moneyShort(balance), Tints.of("sky", c.isDark), modifier = Modifier.weight(1f))
            }
            if (rows.isEmpty()) {
                EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "", "Add entry") { openForm() }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { Format.long(it, "id") }) { row ->
                        val isInc = Format.str(row, "type") == "Income"
                        val voided = Format.str(row, "status") == "Void"
                        MmsCard(onClick = { detailRow = row }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TintTile(
                                    if (isInc) G.UP else G.DOWN,
                                    Tints.of(if (isInc) "em" else "rose", c.isDark), 38.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    CellText(
                                        Format.str(row, "description").ifBlank { Format.str(row, "receipt_number") },
                                        strong = true,
                                        sub = Format.str(row, "txn_date") + " · " + Format.str(row, "receipt_number")
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    androidx.compose.foundation.text.BasicText(
                                        (if (isInc) "+" else "−") + Format.money(Format.num(row, "amount")),
                                        style = MmsType.body.copy(
                                            color = if (isInc) c.em else c.cRose,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    if (voided) {
                                        Spacer(Modifier.height(4.dp))
                                        StatusPill("Void", "Void")
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

    // ---- create
    if (show) {
        MmsDialog("New transaction", onDismiss = { show = false }, onConfirm = {
            scope.launch {
                try {
                    val res = withContext(Dispatchers.IO) {
                        repo.accountingCreate(
                            mapOf(
                                "type" to txnType, "amount" to (amount.toDoubleOrNull() ?: 0.0),
                                "description" to desc, "paymentMethod" to method, "txnDate" to date,
                                "payee" to payee, "billNo" to billNo, "transactionRef" to txnRef,
                                "category" to category, "accountId" to if (accountId > 0) accountId else null
                            )
                        )
                    }
                    show = false; reload(); toast("Entry ${res["voucherNo"]}", ToastMsg.Kind.Success)
                } catch (e: Exception) {
                    toast(e.message ?: "Error", ToastMsg.Kind.Error)
                }
            }
        }, confirmEnabled = (amount.toDoubleOrNull() ?: 0.0) > 0) {
            MmsSelect(txnType, listOf("Income", "Expense"), {
                txnType = it; accountId = 0; accountLabel = ""
            }, label = "Type")
            MmsSelect(
                accountLabel.ifBlank { "Auto ledger account" },
                listOf("Auto ledger account") + typeAccounts.map { "${Format.str(it, "code")} — ${Format.str(it, "name")}" },
                { label ->
                    if (label == "Auto ledger account") {
                        accountLabel = ""; accountId = 0
                    } else {
                        accountLabel = label
                        val code = label.substringBefore(" —")
                        accountId = typeAccounts.firstOrNull { Format.str(it, "code") == code }?.let { Format.long(it, "id") } ?: 0
                    }
                },
                label = "Ledger account"
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(amount, { amount = it }, label = "Amount", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                MmsDateField(date, { date = it }, label = "Date", modifier = Modifier.weight(1f))
            }
            MmsInput(desc, { desc = it }, label = "Description")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsSelect(method, listOf("Cash", "UPI", "Bank Transfer", "Cheque", "Card", "Other"), { method = it }, label = "Method", modifier = Modifier.weight(1f))
                MmsInput(payee, { payee = it }, label = "Payee", modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmsInput(billNo, { billNo = it }, label = "Bill no", modifier = Modifier.weight(1f))
                MmsInput(txnRef, { txnRef = it }, label = "Txn ref", modifier = Modifier.weight(1f))
            }
            MmsInput(category, { category = it }, label = "Category")
        }
    }

    // ---- detail
    if (detailRow != null) {
        val row = detailRow!!
        val voided = Format.str(row, "status") == "Void"
        MmsDialog("Transaction", onDismiss = { detailRow = null }, compact = true) {
            DetailRow("Voucher", Format.str(row, "voucher_no"), mono = true)
            DetailRow("Receipt", Format.str(row, "receipt_number"), mono = true)
            DetailRow("Type", Format.str(row, "type"))
            DetailRow("Amount", Format.money(Format.num(row, "amount")), strong = true)
            DetailRow("Date", Format.str(row, "txn_date"))
            DetailRow("Description", Format.str(row, "description"))
            DetailRow("Method", Format.str(row, "payment_method"))
            DetailRow("Payee", Format.str(row, "payee"))
            DetailRow("Bill", Format.str(row, "bill_no"))
            DetailRow("By", Format.str(row, "created_by_name"))
            if (voided) {
                SectionLabel("Void info")
                DetailRow("Reason", Format.str(row, "void_reason"))
                DetailRow("By", Format.str(row, "voided_by_name"))
                DetailRow("At", Format.str(row, "voided_at"))
            } else {
                MmsButton("Void transaction", {
                    voidRow = row; voidReason = ""; adminPwd = ""
                }, small = true, danger = true, ghost = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // ---- void
    if (voidRow != null) {
        MmsDialog("Void transaction", onDismiss = { voidRow = null }, confirmLabel = "Void", danger = true, compact = true,
            confirmEnabled = voidReason.isNotBlank() && adminPwd.isNotBlank(),
            onConfirm = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            repo.accountingVoid(Format.long(voidRow!!, "id"), voidReason, adminPwd)
                        }
                        voidRow = null; detailRow = null; reload(); toast("Transaction voided", ToastMsg.Kind.Success)
                    } catch (e: Exception) {
                        toast(e.message ?: "Error", ToastMsg.Kind.Error)
                    }
                }
            }) {
            InfoBanner("Voiding keeps the audit trail. The entry moves to the Voided list.", "warn")
            MmsInput(voidReason, { voidReason = it }, label = "Void reason", singleLine = false)
            MmsInput(adminPwd, { adminPwd = it }, label = "Administrator password", password = true)
        }
    }
}

