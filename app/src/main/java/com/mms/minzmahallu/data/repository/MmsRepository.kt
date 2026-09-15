package com.mms.minzmahallu.data.repository

import com.mms.minzmahallu.data.db.DatabaseManager
import com.mms.minzmahallu.data.model.ChartPoint
import com.mms.minzmahallu.data.model.DashboardSummary
import com.mms.minzmahallu.data.model.PageResult
import com.mms.minzmahallu.util.Crypto
import com.mms.minzmahallu.util.Format
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Unified data facade mirroring window.mms.* from Electron preload. */
class MmsRepository(val db: DatabaseManager) {
    val auth = AuthService(db)

    private fun page(sql: String, countSql: String, params: List<String>, page: Int?, pageSize: Int?): PageResult {
        if (page != null && pageSize != null && page > 0 && pageSize > 0) {
            val offset = (page - 1) * pageSize
            val rows = db.all("$sql LIMIT ? OFFSET ?", (params + listOf(pageSize.toString(), offset.toString())).toTypedArray())
            val total = (db.scalar(countSql, params.toTypedArray()) as? Number)?.toInt() ?: 0
            return PageResult(rows, total)
        }
        val rows = db.all(sql, params.toTypedArray())
        return PageResult(rows, rows.size)
    }
    private fun like(s: String) = "%$s%"
    private fun actorId() = auth.currentUser?.id ?: 1L

    fun dashboardSummary(): DashboardSummary {
        ensureCurrentMonthSubscriptions()
        val row = db.one("SELECT * FROM v_dashboard_summary") ?: emptyMap()
        val income = (db.scalar("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='Income' AND (status IS NULL OR status!='Void') AND strftime('%Y-%m',txn_date)=strftime('%Y-%m','now')") as? Number)?.toDouble() ?: 0.0
        val expense = (db.scalar("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='Expense' AND (status IS NULL OR status!='Void') AND strftime('%Y-%m',txn_date)=strftime('%Y-%m','now')") as? Number)?.toDouble() ?: 0.0
        val balInc = (db.scalar("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='Income' AND (status IS NULL OR status!='Void')") as? Number)?.toDouble() ?: 0.0
        val balExp = (db.scalar("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='Expense' AND (status IS NULL OR status!='Void')") as? Number)?.toDouble() ?: 0.0
        val don = Format.num(row, "monthly_donations")
        val coll = Format.num(row, "monthly_collection")
        return DashboardSummary(
            totalFamilies = Format.long(row, "total_families"), totalMembers = Format.long(row, "total_members"),
            activeMembers = Format.long(row, "active_members"), monthlyCollection = coll,
            pendingDues = Format.num(row, "pending_dues"), monthlyDonations = don,
            welfareBeneficiaries = Format.long(row, "welfare_beneficiaries"),
            marriagesThisYear = Format.long(row, "marriages_this_year"), deathsThisYear = Format.long(row, "deaths_this_year"),
            balance = balInc + donTotalAll() + subCollectedAll() - balExp,
            incomeThisMonth = income + coll + don, expenseThisMonth = expense,
        )
    }
    private fun donTotalAll() = (db.scalar("SELECT COALESCE(SUM(amount),0) FROM donations") as? Number)?.toDouble() ?: 0.0
    private fun subCollectedAll() = (db.scalar("SELECT COALESCE(SUM(amount_paid),0) FROM subscriptions") as? Number)?.toDouble() ?: 0.0

    fun monthlyCollections(months: Int = 6): List<ChartPoint> {
        val out = mutableListOf<ChartPoint>()
        for (i in (months - 1) downTo 0) {
            val key = db.scalar("SELECT strftime('%Y-%m', date('now', ?))", arrayOf("-${i} months"))?.toString() ?: continue
            val label = db.scalar("SELECT strftime('%b', date('now', ?))", arrayOf("-${i} months"))?.toString() ?: key
            val v = (db.scalar("SELECT COALESCE(SUM(amount_paid),0) FROM subscriptions WHERE strftime('%Y-%m', payment_date)=?", arrayOf(key)) as? Number)?.toDouble() ?: 0.0
            out += ChartPoint(label, v)
        }
        return out
    }
    fun incomeVsExpense(months: Int = 6): List<ChartPoint> {
        val out = mutableListOf<ChartPoint>()
        for (i in (months - 1) downTo 0) {
            val key = db.scalar("SELECT strftime('%Y-%m', date('now', ?))", arrayOf("-${i} months"))?.toString() ?: continue
            val label = db.scalar("SELECT strftime('%b', date('now', ?))", arrayOf("-${i} months"))?.toString() ?: key
            val inc = (db.scalar("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='Income' AND (status IS NULL OR status!='Void') AND strftime('%Y-%m',txn_date)=?", arrayOf(key)) as? Number)?.toDouble() ?: 0.0
            val exp = (db.scalar("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='Expense' AND (status IS NULL OR status!='Void') AND strftime('%Y-%m',txn_date)=?", arrayOf(key)) as? Number)?.toDouble() ?: 0.0
            val don = (db.scalar("SELECT COALESCE(SUM(amount),0) FROM donations WHERE strftime('%Y-%m',donation_date)=?", arrayOf(key)) as? Number)?.toDouble() ?: 0.0
            val sub = (db.scalar("SELECT COALESCE(SUM(amount_paid),0) FROM subscriptions WHERE strftime('%Y-%m',payment_date)=?", arrayOf(key)) as? Number)?.toDouble() ?: 0.0
            out += ChartPoint(label, inc + don + sub, exp)
        }
        return out
    }
    fun recentActivity(limit: Int = 12) = db.all("SELECT * FROM audit_log ORDER BY id DESC LIMIT ?", arrayOf(limit.toString()))
    fun todayAtGlance(): Map<String, Any?> {
        val today = Format.today()
        return mapOf(
            "receipts_today" to (((db.scalar("SELECT COUNT(*) FROM donations WHERE donation_date=?", arrayOf(today)) as? Number)?.toLong() ?: 0L) + ((db.scalar("SELECT COUNT(*) FROM subscriptions WHERE payment_date=?", arrayOf(today)) as? Number)?.toLong() ?: 0L)),
            "welfare_pending" to ((db.scalar("SELECT COUNT(*) FROM welfare_requests WHERE status='Pending'") as? Number)?.toLong() ?: 0L),
            "overdue_subs" to ((db.scalar("SELECT COUNT(*) FROM subscriptions WHERE status IN ('Pending','Overdue')") as? Number)?.toLong() ?: 0L),
        )
    }
    fun alerts(): List<Map<String, Any?>> {
        val list = mutableListOf<Map<String, Any?>>()
        val overdue = (db.scalar("SELECT COUNT(*) FROM subscriptions WHERE status IN ('Overdue','Pending') AND amount_paid < amount") as? Number)?.toLong() ?: 0
        if (overdue > 0) list += mapOf("type" to "overdue", "count" to overdue, "message" to "$overdue families with pending dues")
        val wel = (db.scalar("SELECT COUNT(*) FROM welfare_requests WHERE status='Pending'") as? Number)?.toLong() ?: 0
        if (wel > 0) list += mapOf("type" to "welfare", "count" to wel, "message" to "$wel welfare requests awaiting action")
        return list
    }

    fun familiesList(search: String? = null, status: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        ensureCurrentMonthSubscriptions()
        val where = mutableListOf("1=1"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(family_number LIKE ? OR house_name LIKE ? OR house_number LIKE ? OR phone LIKE ? OR area LIKE ? OR ward LIKE ?)"; val t = like(search); repeat(6) { params += t } }
        if (!status.isNullOrBlank() && status != "All") { where += "status = ?"; params += status }
        val w = where.joinToString(" AND ")
        val sql = "SELECT f.*, (SELECT COUNT(*) FROM members m WHERE m.family_id=f.id AND IFNULL(m.archive_state,0)=0 AND m.status!='Inactive') AS member_count FROM families f WHERE $w ORDER BY f.family_number ASC"
        return page(sql, "SELECT COUNT(*) FROM families f WHERE $w", params, page, pageSize)
    }
    fun familyGet(id: Long) = db.one("SELECT * FROM families WHERE id=?", arrayOf(id.toString()))
    fun familyCreate(data: Map<String, Any?>): Map<String, Any?> {
        val num = Format.nextCode(db.all("SELECT family_number AS n FROM families").map { Format.str(it, "n") }, "FAM", 4)
        val id = db.run("INSERT INTO families (family_number,house_name,house_number,ward,area,address,pincode,phone,alternative_phone,status,notes,whatsapp_phone,whatsapp_enabled) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(num, data["houseName"] ?: "", data["houseNumber"] ?: "", data["ward"] ?: "", data["area"] ?: "", data["address"] ?: "", data["pincode"] ?: "", data["phone"] ?: "", data["altPhone"] ?: "", data["status"] ?: "Active", data["notes"] ?: "", data["whatsappPhone"] ?: "", if (data["whatsappEnabled"] == 0 || data["whatsappEnabled"] == false) 0 else 1))
        auth.audit("CREATE", "families", id, "Created family $num"); return mapOf("id" to id, "familyNumber" to num)
    }
    fun familyUpdate(id: Long, data: Map<String, Any?>) {
        db.run("UPDATE families SET house_name=?,house_number=?,ward=?,area=?,address=?,pincode=?,phone=?,alternative_phone=?,status=?,notes=?,whatsapp_phone=?,whatsapp_enabled=?,updated_at=datetime('now') WHERE id=?",
            arrayOf(data["houseName"] ?: "", data["houseNumber"] ?: "", data["ward"] ?: "", data["area"] ?: "", data["address"] ?: "", data["pincode"] ?: "", data["phone"] ?: "", data["altPhone"] ?: "", data["status"] ?: "Active", data["notes"] ?: "", data["whatsappPhone"] ?: "", if (data["whatsappEnabled"] == 0 || data["whatsappEnabled"] == false) 0 else 1, id))
        auth.audit("UPDATE", "families", id, "Updated family")
    }
    fun familyArchive(id: Long, reason: String) {
        db.run("UPDATE families SET status='Archived',archived_at=datetime('now'),archived_by=?,archive_reason=?,updated_at=datetime('now') WHERE id=?", arrayOf(actorId(), reason, id))
        auth.audit("ARCHIVE", "families", id, reason)
    }
    fun familyRestore(id: Long) {
        db.run("UPDATE families SET status='Active',archived_at=NULL,archive_reason=NULL,updated_at=datetime('now') WHERE id=?", arrayOf(id))
        auth.audit("RESTORE", "families", id, "Restored family")
    }

    fun membersList(search: String? = null, familyId: Long? = null, status: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        val where = mutableListOf("1=1"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(m.name LIKE ? OR m.member_code LIKE ? OR m.mobile LIKE ? OR m.email LIKE ?)"; val t = like(search); repeat(4) { params += t } }
        if (familyId != null) { where += "m.family_id=?"; params += familyId.toString() }
        if (!status.isNullOrBlank() && status != "All") { where += "m.status=?"; params += status }
        val w = where.joinToString(" AND ")
        return page("SELECT m.*, f.family_number, f.house_name AS family_house_name FROM members m LEFT JOIN families f ON f.id=m.family_id WHERE $w ORDER BY m.member_code ASC", "SELECT COUNT(*) FROM members m WHERE $w", params, page, pageSize)
    }
    fun memberGet(id: Long) = db.one("SELECT * FROM members WHERE id=?", arrayOf(id.toString()))
    private fun assertSingleHead(familyId: Long?, excludeId: Long = -1) {
        if (familyId == null) return
        val existing = db.one("SELECT id,name FROM members WHERE family_id=? AND IFNULL(archive_state,0)=0 AND (is_head=1 OR relationship='Head') AND id!=? LIMIT 1", arrayOf(familyId.toString(), excludeId.toString()))
        if (existing != null) error("This family already has a head (${Format.str(existing,"name")}). A family can have only one head.")
    }
    fun memberCreate(data: Map<String, Any?>): Map<String, Any?> {
        val famId = (data["familyId"] as? Number)?.toLong()
        if (data["relationship"] == "Head") assertSingleHead(famId)
        val num = Format.nextCode(db.all("SELECT member_code AS n FROM members").map { Format.str(it, "n") }, "MBR", 4)
        val isHead = if (data["relationship"] == "Head") 1 else 0
        val id = db.run("INSERT INTO members (member_code,family_id,name,arabic_name,father_name,gender,date_of_birth,age,blood_group,occupation,education,marital_status,mobile,email,emergency_contact,relationship,is_head,status,nationality,address,father_id,mother_id,spouse_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(num, famId, data["name"] ?: "", data["arabicName"] ?: "", data["fatherName"] ?: "", data["gender"] ?: "Male", data["dateOfBirth"] ?: "", data["age"], data["bloodGroup"] ?: "", data["occupation"] ?: "", data["education"] ?: "", data["maritalStatus"] ?: "Single", data["mobile"] ?: "", data["email"] ?: "", data["emergencyContact"] ?: "", data["relationship"] ?: "Other", isHead, data["status"] ?: "Active", data["nationality"] ?: "Indian", data["address"] ?: "", data["fatherId"], data["motherId"], data["spouseId"]))
        auth.audit("CREATE", "members", id, "Created member $num"); return mapOf("id" to id, "memberCode" to num)
    }
    fun memberUpdate(id: Long, data: Map<String, Any?>) {
        val famId = (data["familyId"] as? Number)?.toLong()
        if (data["relationship"] == "Head") assertSingleHead(famId, id)
        db.run("UPDATE members SET family_id=?,name=?,arabic_name=?,father_name=?,gender=?,date_of_birth=?,age=?,blood_group=?,occupation=?,education=?,marital_status=?,mobile=?,email=?,emergency_contact=?,relationship=?,is_head=?,status=?,nationality=?,address=?,father_id=?,mother_id=?,spouse_id=?,updated_at=datetime('now') WHERE id=?",
            arrayOf(famId, data["name"] ?: "", data["arabicName"] ?: "", data["fatherName"] ?: "", data["gender"] ?: "Male", data["dateOfBirth"] ?: "", data["age"], data["bloodGroup"] ?: "", data["occupation"] ?: "", data["education"] ?: "", data["maritalStatus"] ?: "Single", data["mobile"] ?: "", data["email"] ?: "", data["emergencyContact"] ?: "", data["relationship"] ?: "Other", if (data["relationship"] == "Head") 1 else 0, data["status"] ?: "Active", data["nationality"] ?: "Indian", data["address"] ?: "", data["fatherId"], data["motherId"], data["spouseId"], id))
        auth.audit("UPDATE", "members", id, "Updated member")
    }
    fun memberArchive(id: Long, reason: String) {
        db.run("UPDATE members SET archive_state=1,status='Inactive',archived_at=datetime('now'),archived_by=?,archive_reason=?,updated_at=datetime('now') WHERE id=?", arrayOf(actorId(), reason, id))
        auth.audit("ARCHIVE", "members", id, reason)
    }
    fun memberRelationships() = listOf("Head", "Spouse", "Son", "Daughter", "Parent", "Brother", "Sister", "Nephew", "Niece", "Grandfather", "Grandmother", "Grandson", "Granddaughter", "Father-in-law", "Mother-in-law", "Other")
    fun memberRelations(id: Long): Map<String, Any?>? {
        val self = db.one("SELECT father_id,mother_id,spouse_id FROM members WHERE id=?", arrayOf(id.toString())) ?: return null
        fun pick(mid: Any?): Map<String, Any?>? {
            val i = (mid as? Number)?.toLong() ?: return null
            return db.one("SELECT id,member_code,name,gender,relationship,is_head,mobile,date_of_birth,status FROM members WHERE id=? AND IFNULL(archive_state,0)=0", arrayOf(i.toString()))
        }
        val children = db.all("SELECT id,member_code,name,gender,date_of_birth,status FROM members WHERE (father_id=? OR mother_id=?) AND IFNULL(archive_state,0)=0 AND id!=? ORDER BY date_of_birth ASC", arrayOf(id.toString(), id.toString(), id.toString()))
        return mapOf("father" to pick(self["father_id"]), "mother" to pick(self["mother_id"]), "spouse" to pick(self["spouse_id"]), "children" to children)
    }

    fun ensureCurrentMonthSubscriptions() {
        val settings = settingsLoad()
        val amount = Format.num(settings, "subscription_monthly_amount").let { if (it <= 0) 100.0 else it }
        val periodStart = db.scalar("SELECT date('now','start of month')")?.toString() ?: return
        val periodEnd = db.scalar("SELECT date('now','start of month','+1 month','-1 day')")?.toString() ?: return
        val plan = db.one("SELECT * FROM subscription_plans WHERE frequency='Monthly' AND is_active=1 ORDER BY id LIMIT 1") ?: return
        val planId = Format.long(plan, "id")
        val families = db.all("SELECT id FROM families WHERE status='Active'")
        db.transaction {
            for (f in families) {
                val fid = Format.long(f, "id")
                val head = db.one("SELECT id FROM members WHERE family_id=? AND IFNULL(archive_state,0)=0 AND (is_head=1 OR relationship='Head') LIMIT 1", arrayOf(fid.toString()))
                val headId = head?.let { Format.long(it, "id") }
                val existing = db.one("SELECT id,period_start,amount,amount_paid,arrears FROM subscriptions WHERE family_id=? LIMIT 1", arrayOf(fid.toString()))
                if (existing == null) {
                    db.run("INSERT INTO subscriptions (family_id,member_id,plan_id,period_start,period_end,amount,amount_paid,status) VALUES (?,?,?,?,?,?,0,'Pending')", arrayOf(fid, headId, planId, periodStart, periodEnd, amount))
                } else if (Format.str(existing, "period_start") != periodStart) {
                    val paid = Format.num(existing, "amount_paid"); val due = Format.num(existing, "amount")
                    val arrearsAdd = if (paid < due) due - paid else 0.0
                    db.run("UPDATE subscriptions SET period_start=?,period_end=?,amount=?,amount_paid=0,status='Pending',arrears=?,payment_date=NULL,receipt_number=NULL,updated_at=datetime('now') WHERE id=?",
                        arrayOf(periodStart, periodEnd, amount, Format.num(existing, "arrears") + arrearsAdd, Format.long(existing, "id")))
                }
            }
        }
    }
    fun subscriptionsList(search: String? = null, status: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        ensureCurrentMonthSubscriptions()
        val where = mutableListOf("1=1"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(f.family_number LIKE ? OR f.house_name LIKE ? OR s.receipt_number LIKE ?)"; val t = like(search); repeat(3) { params += t } }
        if (!status.isNullOrBlank() && status != "All") { where += "s.status=?"; params += status }
        val w = where.joinToString(" AND ")
        return page("SELECT s.*, f.family_number, f.house_name, f.phone AS family_phone, m.name AS member_name, p.name AS plan_name FROM subscriptions s LEFT JOIN families f ON f.id=s.family_id LEFT JOIN members m ON m.id=s.member_id LEFT JOIN subscription_plans p ON p.id=s.plan_id WHERE $w ORDER BY f.family_number ASC",
            "SELECT COUNT(*) FROM subscriptions s LEFT JOIN families f ON f.id=s.family_id WHERE $w", params, page, pageSize)
    }
    fun subscriptionGet(id: Long) = db.one("SELECT s.*, f.family_number, f.house_name FROM subscriptions s LEFT JOIN families f ON f.id=s.family_id WHERE s.id=?", arrayOf(id.toString()))
    fun subscriptionPay(id: Long, amountPaid: Double, method: String, paymentDate: String, remarks: String = ""): Map<String, Any?> {
        val sub = subscriptionGet(id) ?: error("Subscription not found")
        val receipt = nextReceiptNumber(paymentDate)
        val due = Format.num(sub, "amount") + Format.num(sub, "arrears") - Format.num(sub, "advance")
        val status = when { amountPaid >= due -> "Paid"; amountPaid > 0 -> "Partial"; else -> "Pending" }
        db.run("UPDATE subscriptions SET amount_paid=?,payment_date=?,payment_method=?,receipt_number=?,status=?,remarks=?,collected_by=?,updated_at=datetime('now') WHERE id=?",
            arrayOf(amountPaid, paymentDate, method, receipt, status, remarks, actorId(), id))
        try { db.run("INSERT INTO subscription_payments (subscription_id,amount,payment_date,payment_method,receipt_number,remarks,collected_by) VALUES (?,?,?,?,?,?,?)", arrayOf(id, amountPaid, paymentDate, method, receipt, remarks, actorId())) } catch (_: Exception) {}
        auth.audit("PAYMENT", "subscriptions", id, "Collected $amountPaid receipt $receipt")
        return mapOf("receiptNumber" to receipt, "status" to status)
    }
    fun subscriptionPlans() = db.all("SELECT * FROM subscription_plans WHERE is_active=1 ORDER BY id")
    fun subscriptionsTotalCollected() = (db.scalar("SELECT COALESCE(SUM(amount_paid),0) FROM subscriptions WHERE status='Paid'") as? Number)?.toDouble() ?: 0.0
    fun subscriptionsTotalPending() = (db.scalar("SELECT COALESCE(SUM(amount-amount_paid),0) FROM subscriptions WHERE status IN ('Pending','Overdue','Partial')") as? Number)?.toDouble() ?: 0.0
    fun markOverdue() { db.run("UPDATE subscriptions SET status='Overdue' WHERE status='Pending' AND date(period_end) < date('now')"); auth.audit("MARK_OVERDUE", "subscriptions", null, "Marked overdue") }

    fun donationsList(search: String? = null, category: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        val where = mutableListOf("1=1"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(d.donor_name LIKE ? OR d.receipt_number LIKE ? OR d.donor_phone LIKE ?)"; val t = like(search); repeat(3) { params += t } }
        if (!category.isNullOrBlank() && category != "All") { where += "c.name=?"; params += category }
        val w = where.joinToString(" AND ")
        return page("SELECT d.*, c.name AS category_name FROM donations d LEFT JOIN donation_categories c ON c.id=d.category_id WHERE $w ORDER BY d.donation_date DESC, d.id DESC",
            "SELECT COUNT(*) FROM donations d LEFT JOIN donation_categories c ON c.id=d.category_id WHERE $w", params, page, pageSize)
    }
    fun donationGet(id: Long) = db.one("SELECT d.*, c.name AS category_name FROM donations d LEFT JOIN donation_categories c ON c.id=d.category_id WHERE d.id=?", arrayOf(id.toString()))
    fun donationCreate(data: Map<String, Any?>): Map<String, Any?> {
        val date = data["donationDate"]?.toString() ?: Format.today()
        val receipt = data["receiptNumber"]?.toString()?.takeIf { it.isNotBlank() } ?: nextReceiptNumber(date)
        val id = db.run("INSERT INTO donations (donor_name,donor_phone,donor_address,family_id,member_id,category_id,amount,donation_date,receipt_number,purpose,payment_method,transaction_ref,received_by,remarks) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(data["donorName"], data["donorPhone"] ?: "", data["donorAddress"] ?: "", data["familyId"], data["memberId"], data["categoryId"], data["amount"], date, receipt, data["purpose"] ?: "", data["paymentMethod"] ?: "Cash", data["transactionRef"] ?: "", data["receivedBy"] ?: actorId(), data["remarks"] ?: ""))
        auth.audit("CREATE", "donations", id, "Donation $receipt"); return mapOf("id" to id, "receiptNumber" to receipt)
    }
    fun donationUpdate(id: Long, data: Map<String, Any?>, adminPassword: String, reason: String) {
        auth.verifyAdminPassword(adminPassword)
        db.run("UPDATE donations SET donor_name=?,donor_phone=?,donor_address=?,family_id=?,member_id=?,category_id=?,amount=?,donation_date=?,purpose=?,payment_method=?,transaction_ref=?,remarks=?,updated_at=datetime('now') WHERE id=?",
            arrayOf(data["donorName"], data["donorPhone"], data["donorAddress"], data["familyId"], data["memberId"], data["categoryId"], data["amount"], data["donationDate"], data["purpose"], data["paymentMethod"], data["transactionRef"], data["remarks"], id))
        auth.audit("UPDATE", "donations", id, reason.ifBlank { "Updated donation" })
    }
    fun donationCategories() = db.all("SELECT * FROM donation_categories WHERE is_active=1 ORDER BY name")
    fun donationCategoriesAll() = db.all("SELECT dc.*, (SELECT COUNT(*) FROM donations d WHERE d.category_id=dc.id) AS donation_count FROM donation_categories dc ORDER BY dc.is_active DESC, dc.name")
    fun donationCreateCategory(name: String, description: String = ""): Long {
        require(name.isNotBlank()) { "Category name is required" }
        return db.run("INSERT INTO donation_categories (name,description,is_active) VALUES (?,?,1)", arrayOf(name.trim(), description))
    }
    fun donationsTotalThisMonth() = (db.scalar("SELECT COALESCE(SUM(amount),0) FROM donations WHERE strftime('%Y-%m',donation_date)=strftime('%Y-%m','now')") as? Number)?.toDouble() ?: 0.0
    private fun nextReceiptNumber(date: String): String {
        val settings = settingsLoad()
        val prefix = Format.str(settings, "receipt_prefix").ifBlank { "RCP" }
        val parts = date.split("-"); val y = parts.getOrNull(0) ?: Format.year().toString(); val m = parts.getOrNull(1) ?: "01"
        val likePat = "$prefix/$y/$m/%"
        val rows = db.all("SELECT receipt_number AS n FROM donations WHERE receipt_number LIKE ? UNION ALL SELECT receipt_number AS n FROM subscriptions WHERE receipt_number LIKE ?", arrayOf(likePat, likePat))
        var max = 0; val re = Regex("""(\d+)\s*$""")
        for (r in rows) { val match = re.find(Format.str(r, "n")) ?: continue; max = maxOf(max, match.groupValues[1].toIntOrNull() ?: 0) }
        return "$prefix/$y/$m/${(max + 1).toString().padStart(3, '0')}"
    }

    fun accountingList(search: String? = null, type: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        val where = mutableListOf("(t.status IS NULL OR t.status != 'Void')"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(t.description LIKE ? OR t.receipt_number LIKE ? OR t.transaction_ref LIKE ? OR t.voucher_no LIKE ? OR t.bill_no LIKE ? OR t.payee LIKE ? OR IFNULL(t.category,'') LIKE ?)"; val t = like(search); repeat(7) { params += t } }
        if (!type.isNullOrBlank() && type != "All") { where += "t.type=?"; params += type }
        val w = where.joinToString(" AND ")
        return page("SELECT t.*, u.username AS created_by_name FROM transactions t LEFT JOIN users u ON u.id=t.created_by WHERE $w ORDER BY t.txn_date DESC, t.id DESC", "SELECT COUNT(*) FROM transactions t WHERE $w", params, page, pageSize)
    }
    fun accountingGet(id: Long) = db.one("SELECT * FROM transactions WHERE id=?", arrayOf(id.toString()))
    fun accountingCreate(data: Map<String, Any?>): Map<String, Any?> {
        val existing = db.all("SELECT receipt_number AS n FROM transactions").map { Format.str(it, "n") }
        val receipt = data["receiptNumber"]?.toString()?.takeIf { it.isNotBlank() } ?: Format.nextCode(existing.filter { it.startsWith("TXN") }, "TXN", 4)
        val year = Format.year()
        val voucher = data["voucherNo"]?.toString()?.takeIf { it.isNotBlank() } ?: "VOU-$year-${((db.scalar("SELECT COALESCE(MAX(id),0)+1 FROM transactions") as? Number)?.toInt() ?: 1).toString().padStart(4,'0')}"
        val accountId = (data["accountId"] as? Number)?.toLong() ?: db.one("SELECT id FROM ledger_accounts WHERE type=? LIMIT 1", arrayOf(data["type"]?.toString() ?: "Expense"))?.let { Format.long(it, "id") } ?: 1L
        val id = db.run("INSERT INTO transactions (txn_date,account_id,type,amount,payment_method,reference,description,receipt_number,created_by,transaction_ref,voucher_no,bill_no,payee,category,asset_id,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'Posted')",
            arrayOf(data["txnDate"] ?: Format.today(), accountId, data["type"] ?: "Expense", data["amount"], data["paymentMethod"] ?: "Cash", data["reference"] ?: "", data["description"] ?: "", receipt, actorId(), data["transactionRef"] ?: "", voucher, data["billNo"] ?: "", data["payee"] ?: "", data["category"] ?: "", data["assetId"]))
        auth.audit("CREATE", "accounting", id, "${data["type"]} $receipt"); return mapOf("id" to id, "receiptNumber" to receipt, "voucherNo" to voucher)
    }
    fun accountingVoid(id: Long, reason: String, adminPassword: String) {
        auth.verifyAdminPassword(adminPassword); require(reason.isNotBlank()) { "Void reason is required" }
        db.run("UPDATE transactions SET status='Void',voided_at=datetime('now'),voided_by=?,void_reason=?,updated_at=datetime('now') WHERE id=?", arrayOf(actorId(), reason, id))
        auth.audit("VOID", "accounting", id, reason)
    }
    fun accountingTotalIncome() = (db.scalar("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='Income' AND (status IS NULL OR status!='Void')") as? Number)?.toDouble() ?: 0.0
    fun accountingTotalExpense() = (db.scalar("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='Expense' AND (status IS NULL OR status!='Void')") as? Number)?.toDouble() ?: 0.0
    fun accountingBalance() = accountingTotalIncome() + donTotalAll() + subCollectedAll() - accountingTotalExpense()
    fun ledgerAccounts() = db.all("SELECT * FROM ledger_accounts WHERE is_active=1 ORDER BY type, code")

    fun marriagesList(search: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        val where = mutableListOf("1=1"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(m.marriage_number LIKE ? OR m.bride_name LIKE ? OR m.groom_name LIKE ?)"; val t = like(search); repeat(3) { params += t } }
        val w = where.joinToString(" AND ")
        return page("SELECT m.* FROM marriages m WHERE $w ORDER BY m.nikah_date DESC, m.id DESC", "SELECT COUNT(*) FROM marriages m WHERE $w", params, page, pageSize)
    }
    fun marriageGet(id: Long) = db.one("SELECT * FROM marriages WHERE id=?", arrayOf(id.toString()))
    fun marriageCreate(data: Map<String, Any?>): Map<String, Any?> {
        val num = Format.nextRegister(db.all("SELECT marriage_number AS n FROM marriages").map { Format.str(it,"n") }, "MRG")
        val id = db.run("INSERT INTO marriages (marriage_number,bride_name,bride_father,bride_address,groom_name,groom_father,groom_address,witness1,witness2,witness3,witness4,mahar,nikah_date,registration_date,place,remarks) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(num, data["brideName"] ?: "", data["brideFather"] ?: "", data["brideAddress"] ?: "", data["groomName"] ?: "", data["groomFather"] ?: "", data["groomAddress"] ?: "", data["witness1"] ?: "", data["witness2"] ?: "", data["witness3"] ?: "", data["witness4"] ?: "", data["mahar"] ?: "", data["nikahDate"], data["registrationDate"] ?: Format.today(), data["place"] ?: "", data["remarks"] ?: ""))
        auth.audit("CREATE", "marriages", id, num); return mapOf("id" to id, "marriageNumber" to num)
    }
    fun marriageUpdate(id: Long, data: Map<String, Any?>) {
        db.run("UPDATE marriages SET bride_name=?,bride_father=?,bride_address=?,groom_name=?,groom_father=?,groom_address=?,witness1=?,witness2=?,witness3=?,witness4=?,mahar=?,nikah_date=?,registration_date=?,place=?,remarks=?,updated_at=datetime('now') WHERE id=?",
            arrayOf(data["brideName"] ?: "", data["brideFather"] ?: "", data["brideAddress"] ?: "", data["groomName"] ?: "", data["groomFather"] ?: "", data["groomAddress"] ?: "", data["witness1"] ?: "", data["witness2"] ?: "", data["witness3"] ?: "", data["witness4"] ?: "", data["mahar"] ?: "", data["nikahDate"] ?: "", data["registrationDate"] ?: Format.today(), data["place"] ?: "", data["remarks"] ?: "", id))
        auth.audit("UPDATE", "marriages", id, "Updated marriage")
    }

    fun deathsList(search: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        val where = mutableListOf("1=1"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(d.death_number LIKE ? OR d.deceased_name LIKE ? OR d.father_name LIKE ?)"; val t = like(search); repeat(3) { params += t } }
        val w = where.joinToString(" AND ")
        return page("SELECT d.* FROM deaths d WHERE $w ORDER BY d.date_of_death DESC, d.id DESC", "SELECT COUNT(*) FROM deaths d WHERE $w", params, page, pageSize)
    }
    fun deathGet(id: Long) = db.one("SELECT * FROM deaths WHERE id=?", arrayOf(id.toString()))
    fun deathCreate(data: Map<String, Any?>): Map<String, Any?> {
        val num = Format.nextRegister(db.all("SELECT death_number AS n FROM deaths").map { Format.str(it,"n") }, "DTH")
        val id = db.run("INSERT INTO deaths (death_number,deceased_name,father_name,gender,age,date_of_death,place_of_death,burial_date,cause_of_death,burial_place,address,family_id,registration_date,remarks) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(num, data["deceasedName"] ?: "", data["fatherName"] ?: "", data["gender"] ?: "Male", data["age"], data["dateOfDeath"], data["placeOfDeath"] ?: "", data["burialDate"] ?: "", data["causeOfDeath"] ?: "", data["burialPlace"] ?: "", data["address"] ?: "", data["familyId"], data["registrationDate"] ?: Format.today(), data["remarks"] ?: ""))
        auth.audit("CREATE", "deaths", id, num); return mapOf("id" to id, "deathNumber" to num)
    }
    fun deathUpdate(id: Long, data: Map<String, Any?>) {
        db.run("UPDATE deaths SET deceased_name=?,father_name=?,gender=?,age=?,date_of_death=?,place_of_death=?,burial_date=?,cause_of_death=?,burial_place=?,address=?,family_id=?,registration_date=?,remarks=?,updated_at=datetime('now') WHERE id=?",
            arrayOf(data["deceasedName"] ?: "", data["fatherName"] ?: "", data["gender"] ?: "Male", data["age"], data["dateOfDeath"], data["placeOfDeath"] ?: "", data["burialDate"] ?: "", data["causeOfDeath"] ?: "", data["burialPlace"] ?: "", data["address"] ?: "", data["familyId"], data["registrationDate"] ?: Format.today(), data["remarks"] ?: "", id))
        auth.audit("UPDATE", "deaths", id, "Updated death record")
    }

    fun welfareList(search: String? = null, status: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        val where = mutableListOf("1=1"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(w.request_number LIKE ? OR w.applicant_name LIKE ?)"; val t = like(search); repeat(2) { params += t } }
        if (!status.isNullOrBlank() && status != "All") { where += "w.status=?"; params += status }
        val w = where.joinToString(" AND ")
        return page("SELECT w.* FROM welfare_requests w WHERE $w ORDER BY w.request_date DESC, w.id DESC", "SELECT COUNT(*) FROM welfare_requests w WHERE $w", params, page, pageSize)
    }
    fun welfareGet(id: Long) = db.one("SELECT * FROM welfare_requests WHERE id=?", arrayOf(id.toString()))
    fun welfareCreate(data: Map<String, Any?>): Map<String, Any?> {
        val num = Format.nextRegister(db.all("SELECT request_number AS n FROM welfare_requests").map { Format.str(it,"n") }, "WEL", withYear = false, pad = 4)
        val id = db.run("INSERT INTO welfare_requests (request_number,applicant_name,family_id,category,amount_requested,amount_approved,reason,request_date,status,remarks,processed_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(num, data["applicantName"], data["familyId"], data["category"] ?: "Financial Assistance", data["amountRequested"], data["amountApproved"] ?: 0, data["reason"] ?: "", Format.today(), "Pending", data["remarks"] ?: "", actorId()))
        auth.audit("CREATE", "welfare", id, num); return mapOf("id" to id, "requestNumber" to num)
    }
    fun welfareApprove(id: Long, amount: Double, remarks: String, minutesDate: String = "") {
        db.run("UPDATE welfare_requests SET status='Approved',amount_approved=?,remarks=?,minutes_date=COALESCE(NULLIF(?,''),minutes_date),processed_by=?,processed_date=datetime('now'),updated_at=datetime('now') WHERE id=?", arrayOf(amount, remarks, minutesDate, actorId(), id))
        auth.audit("APPROVE", "welfare", id, "Approved $amount")
    }
    fun welfareReject(id: Long, reason: String) {
        db.run("UPDATE welfare_requests SET status='Rejected',remarks=?,processed_by=?,processed_date=datetime('now'),updated_at=datetime('now') WHERE id=?", arrayOf(reason, actorId(), id))
        auth.audit("REJECT", "welfare", id, reason)
    }
    fun welfareDisburse(id: Long, reason: String, adminPassword: String, minutesDate: String = "") {
        auth.verifyAdminPassword(adminPassword)
        val w = welfareGet(id) ?: error("Welfare request not found")
        val md = minutesDate.ifBlank { Format.str(w, "minutes_date") }
        if (md.isBlank()) error("Date of the committee minutes approving this amount is missing.")
        db.run("UPDATE welfare_requests SET status='Disbursed',disbursed_date=?,minutes_date=?,processed_by=?,updated_at=datetime('now') WHERE id=?", arrayOf(Format.today(), md, actorId(), id))
        auth.audit("DISBURSE", "welfare", id, reason.ifBlank { "Disbursed" })
    }
    fun welfareCategories() = listOf("Medical Aid", "Education Aid", "Marriage Assistance", "Financial Assistance")

    fun certificatesList(search: String? = null, type: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        val where = mutableListOf("1=1"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(c.certificate_number LIKE ? OR c.issued_to LIKE ? OR c.verification_code LIKE ?)"; val t = like(search); repeat(3) { params += t } }
        if (!type.isNullOrBlank() && type != "All") { where += "c.type=?"; params += type }
        val w = where.joinToString(" AND ")
        return page("SELECT c.* FROM certificates c WHERE $w ORDER BY c.issued_date DESC, c.id DESC", "SELECT COUNT(*) FROM certificates c WHERE $w", params, page, pageSize)
    }
    fun certificateIssue(type: String, issuedTo: String, memberId: Long? = null, familyId: Long? = null, marriageId: Long? = null, deathId: Long? = null, notes: String = ""): Map<String, Any?> {
        val existing = db.all("SELECT certificate_number AS n FROM certificates").map { Format.str(it,"n") }
        val prefix = when (type) { "Membership" -> "CRT-MEM"; "Residence" -> "CRT-RES"; "Marriage" -> "CRT-MRG"; "Death" -> "CRT-DTH"; else -> "CRT" }
        val num = Format.nextRegister(existing, prefix)
        val code = Crypto.randomToken(8).uppercase()
        val id = db.run("INSERT INTO certificates (certificate_number,type,member_id,family_id,marriage_id,death_id,issued_to,issued_date,issued_by,notes,status,verification_code) VALUES (?,?,?,?,?,?,?,?,?,?, 'Issued',?)",
            arrayOf(num, type, memberId, familyId, marriageId, deathId, issuedTo, Format.today(), actorId(), notes, code))
        auth.audit("ISSUE", "certificates", id, "$type $num"); return mapOf("id" to id, "certificateNumber" to num, "verificationCode" to code)
    }
    fun certificateVerify(code: String) = db.one("SELECT * FROM certificates WHERE verification_code=? OR certificate_number=?", arrayOf(code, code))

    fun tokenEventsList() = db.all("SELECT e.*, (SELECT COUNT(*) FROM tokens t WHERE t.event_id=e.id) AS token_count FROM token_events e ORDER BY e.event_date DESC, e.id DESC")
    fun tokenEventGet(id: Long) = db.one("SELECT * FROM token_events WHERE id=?", arrayOf(id.toString()))
    fun tokenEventCreate(data: Map<String, Any?>): Long {
        val id = db.run("INSERT INTO token_events (name,event_type,event_date,venue,description,status,target_amount) VALUES (?,?,?,?,?,?,?)",
            arrayOf(data["name"], data["eventType"] ?: "General", data["eventDate"] ?: Format.today(), data["venue"] ?: "", data["description"] ?: "", data["status"] ?: "Active", data["targetAmount"] ?: 0))
        auth.audit("CREATE", "tokens", id, "Event ${data["name"]}"); return id
    }
    fun tokenEventUpdate(id: Long, data: Map<String, Any?>) {
        db.run("UPDATE token_events SET name=?,event_type=?,event_date=?,venue=?,description=?,status=?,target_amount=?,updated_at=datetime('now') WHERE id=?",
            arrayOf(data["name"], data["eventType"], data["eventDate"], data["venue"], data["description"], data["status"], data["targetAmount"] ?: 0, id))
    }
    fun tokensList(eventId: Long, search: String? = null): List<Map<String, Any?>> {
        val where = mutableListOf("t.event_id=?"); val params = mutableListOf(eventId.toString())
        if (!search.isNullOrBlank()) { where += "(t.token_code LIKE ? OR f.family_number LIKE ? OR f.house_name LIKE ?)"; val t = like(search); repeat(3) { params += t } }
        return db.all("SELECT t.*, f.family_number, f.house_name FROM tokens t LEFT JOIN families f ON f.id=t.family_id WHERE ${where.joinToString(" AND ")} ORDER BY t.token_code ASC", params.toTypedArray())
    }
    fun tokensGenerate(eventId: Long, familyIds: List<Long>): Int {
        var created = 0
        val existing = db.all("SELECT token_code AS n FROM tokens WHERE event_id=?", arrayOf(eventId.toString())).map { Format.str(it,"n") }.toMutableList()
        db.transaction {
            for (fid in familyIds) {
                if (db.one("SELECT id FROM tokens WHERE event_id=? AND family_id=? AND status!='Cancelled'", arrayOf(eventId.toString(), fid.toString())) != null) continue
                val code = Format.nextCode(existing, "TOK", 4); existing += code
                db.run("INSERT INTO tokens (event_id,family_id,token_code,status) VALUES (?,?,?,'Pending')", arrayOf(eventId, fid, code)); created++
            }
        }
        auth.audit("GENERATE", "tokens", eventId, "Generated $created tokens"); return created
    }
    fun tokenCollect(id: Long) { db.run("UPDATE tokens SET status='Collected',amount_collected=amount,collected_at=datetime('now'),collected_by=? WHERE id=?", arrayOf(actorId(), id)); auth.audit("COLLECT", "tokens", id, "Token collected") }
    fun tokenCancel(id: Long, reason: String) { db.run("UPDATE tokens SET status='Cancelled',cancelled_at=datetime('now'),cancel_reason=? WHERE id=?", arrayOf(reason, id)); auth.audit("CANCEL", "tokens", id, reason) }
    fun tokenStats(eventId: Long): Map<String, Any?> {
        val eid = eventId.toString()
        return mapOf(
            "total" to ((db.scalar("SELECT COUNT(*) FROM tokens WHERE event_id=?", arrayOf(eid)) as? Number)?.toLong() ?: 0),
            "collected" to ((db.scalar("SELECT COUNT(*) FROM tokens WHERE event_id=? AND status='Collected'", arrayOf(eid)) as? Number)?.toLong() ?: 0),
            "pending" to ((db.scalar("SELECT COUNT(*) FROM tokens WHERE event_id=? AND status='Pending'", arrayOf(eid)) as? Number)?.toLong() ?: 0),
            "amount" to ((db.scalar("SELECT COALESCE(SUM(amount_collected),0) FROM tokens WHERE event_id=?", arrayOf(eid)) as? Number)?.toDouble() ?: 0.0),
        )
    }

    fun staffList(search: String? = null, status: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        val where = mutableListOf("1=1"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(s.name LIKE ? OR s.staff_code LIKE ? OR s.phone LIKE ?)"; val t = like(search); repeat(3) { params += t } }
        if (!status.isNullOrBlank() && status != "All") { if (status == "Archived") where += "s.archive_state=1" else { where += "s.archive_state=0 AND s.status=?"; params += status } } else where += "IFNULL(s.archive_state,0)=0"
        val w = where.joinToString(" AND ")
        return page("SELECT s.* FROM staff s WHERE $w ORDER BY s.staff_code ASC", "SELECT COUNT(*) FROM staff s WHERE $w", params, page, pageSize)
    }
    fun staffGet(id: Long) = db.one("SELECT * FROM staff WHERE id=?", arrayOf(id.toString()))
    fun staffRoles() = listOf("Imam", "Muazzin", "Teacher", "Clerk", "Cleaner", "Guard", "Cook", "Driver", "Other")
    fun staffCreate(data: Map<String, Any?>): Map<String, Any?> {
        val num = Format.nextCode(db.all("SELECT staff_code AS n FROM staff").map { Format.str(it,"n") }, "STF", 4)
        val id = db.run("INSERT INTO staff (staff_code,member_id,name,role,phone,email,address,joined_date,salary,payment_frequency,status,notes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(num, data["memberId"], data["name"], data["role"] ?: "Other", data["phone"] ?: "", data["email"] ?: "", data["address"] ?: "", data["joinedDate"] ?: Format.today(), data["salary"] ?: 0, data["paymentFrequency"] ?: "Monthly", data["status"] ?: "Active", data["notes"] ?: ""))
        auth.audit("CREATE", "staff", id, num); return mapOf("id" to id, "staffCode" to num)
    }
    fun staffUpdate(id: Long, data: Map<String, Any?>) {
        db.run("UPDATE staff SET member_id=?,name=?,role=?,phone=?,email=?,address=?,joined_date=?,salary=?,payment_frequency=?,status=?,notes=?,updated_at=datetime('now') WHERE id=?",
            arrayOf(data["memberId"], data["name"], data["role"], data["phone"] ?: "", data["email"] ?: "", data["address"] ?: "", data["joinedDate"], data["salary"] ?: 0, data["paymentFrequency"] ?: "Monthly", data["status"] ?: "Active", data["notes"] ?: "", id))
    }
    fun staffPaySalary(data: Map<String, Any?>): Long {
        val id = db.run("INSERT INTO staff_payments (staff_id,amount,payment_date,period_start,period_end,payment_method,receipt_number,remarks,created_by) VALUES (?,?,?,?,?,?,?,?,?)",
            arrayOf(data["staffId"], data["amount"], data["paymentDate"] ?: Format.today(), data["periodStart"], data["periodEnd"], data["paymentMethod"] ?: "Cash", data["receiptNumber"] ?: "", data["remarks"] ?: "", actorId()))
        auth.audit("PAYMENT", "staff", id, "Salary payment"); return id
    }
    fun staffPayments(staffId: Long? = null) = if (staffId != null) db.all("SELECT * FROM staff_payments WHERE staff_id=? ORDER BY payment_date DESC", arrayOf(staffId.toString())) else db.all("SELECT p.*, s.name AS staff_name FROM staff_payments p LEFT JOIN staff s ON s.id=p.staff_id ORDER BY p.payment_date DESC LIMIT 200")

    fun committeeList(search: String? = null, status: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        val where = mutableListOf("1=1"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(c.name LIKE ? OR c.committee_code LIKE ? OR c.phone LIKE ?)"; val t = like(search); repeat(3) { params += t } }
        if (!status.isNullOrBlank() && status != "All") { if (status == "Archived") where += "c.archive_state=1" else { where += "IFNULL(c.archive_state,0)=0 AND c.status=?"; params += status } } else where += "IFNULL(c.archive_state,0)=0"
        val w = where.joinToString(" AND ")
        return page("SELECT c.* FROM committee_members c WHERE $w ORDER BY c.committee_code ASC", "SELECT COUNT(*) FROM committee_members c WHERE $w", params, page, pageSize)
    }
    fun committeeGet(id: Long) = db.one("SELECT * FROM committee_members WHERE id=?", arrayOf(id.toString()))
    fun committeePositions() = listOf("President", "Vice President", "Secretary", "Joint Secretary", "Treasurer", "Auditor", "Committee Member", "Advisory Member", "Trustee", "Other")
    fun committeeTypes() = listOf("Executive", "Advisory", "Working", "Sub-Committee", "Trust")
    fun committeeCreate(data: Map<String, Any?>): Map<String, Any?> {
        val num = Format.nextCode(db.all("SELECT committee_code AS n FROM committee_members").map { Format.str(it,"n") }, "COM", 4)
        val id = db.run("INSERT INTO committee_members (committee_code,member_id,name,position,committee_type,phone,email,address,term_start,term_end,status,notes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(num, data["memberId"], data["name"], data["position"] ?: "Committee Member", data["committeeType"] ?: "Executive", data["phone"] ?: "", data["email"] ?: "", data["address"] ?: "", data["termStart"], data["termEnd"], data["status"] ?: "Active", data["notes"] ?: ""))
        auth.audit("CREATE", "committee", id, num); return mapOf("id" to id, "committeeCode" to num)
    }
    fun committeeUpdate(id: Long, data: Map<String, Any?>) {
        db.run("UPDATE committee_members SET member_id=?,name=?,position=?,committee_type=?,phone=?,email=?,address=?,term_start=?,term_end=?,status=?,notes=?,updated_at=datetime('now') WHERE id=?",
            arrayOf(data["memberId"], data["name"], data["position"], data["committeeType"], data["phone"] ?: "", data["email"] ?: "", data["address"] ?: "", data["termStart"], data["termEnd"], data["status"] ?: "Active", data["notes"] ?: "", id))
    }

    fun assetsList(search: String? = null, category: String? = null, status: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        val where = mutableListOf("1=1"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(a.asset_code LIKE ? OR a.name LIKE ? OR a.location LIKE ?)"; val t = like(search); repeat(3) { params += t } }
        if (!category.isNullOrBlank() && category != "All") { where += "a.category=?"; params += category }
        if (!status.isNullOrBlank() && status != "All") { where += "a.status=?"; params += status }
        val w = where.joinToString(" AND ")
        return page("SELECT a.* FROM assets a WHERE $w ORDER BY a.asset_code ASC", "SELECT COUNT(*) FROM assets a WHERE $w", params, page, pageSize)
    }
    fun assetGet(id: Long) = db.one("SELECT * FROM assets WHERE id=?", arrayOf(id.toString()))
    fun assetCategories() = listOf("Building", "Land", "Shop", "Room", "Hall", "Vehicle", "Furniture", "Equipment", "Other")
    fun assetStatuses() = listOf("In use", "Given rent", "Vacant", "Under construction", "Sold", "Demolished", "Transferred")
    fun assetCreate(data: Map<String, Any?>): Map<String, Any?> {
        require(!data["name"]?.toString().isNullOrBlank()) { "Asset name is required" }
        val code = Format.nextCode(db.all("SELECT asset_code AS n FROM assets").map { Format.str(it,"n") }, "AST", 3)
        val id = db.run("INSERT INTO assets (asset_code,name,category,reference_no,location,acquisition_date,acquisition_cost,current_value,status,condition_note,custodian,income_generating,tenant_name,monthly_rent,agreement_start,agreement_end,notes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(code, data["name"], data["category"] ?: "Other", data["referenceNo"] ?: "", data["location"] ?: "", data["acquisitionDate"] ?: "", data["acquisitionCost"] ?: 0, data["currentValue"] ?: 0, data["status"] ?: "In use", data["conditionNote"] ?: "Good", data["custodian"] ?: "", if (data["incomeGenerating"] == true || data["incomeGenerating"] == 1) 1 else 0, data["tenantName"] ?: "", data["monthlyRent"] ?: 0, data["agreementStart"] ?: "", data["agreementEnd"] ?: "", data["notes"] ?: ""))
        auth.audit("CREATE", "assets", id, code); return mapOf("id" to id, "assetCode" to code)
    }
    fun assetUpdate(id: Long, data: Map<String, Any?>) {
        db.run("UPDATE assets SET name=?,category=?,reference_no=?,location=?,acquisition_date=?,acquisition_cost=?,current_value=?,status=?,condition_note=?,custodian=?,income_generating=?,tenant_name=?,monthly_rent=?,agreement_start=?,agreement_end=?,notes=?,updated_at=datetime('now') WHERE id=?",
            arrayOf(data["name"], data["category"], data["referenceNo"] ?: "", data["location"] ?: "", data["acquisitionDate"] ?: "", data["acquisitionCost"] ?: 0, data["currentValue"] ?: 0, data["status"], data["conditionNote"] ?: "Good", data["custodian"] ?: "", if (data["incomeGenerating"] == true || data["incomeGenerating"] == 1) 1 else 0, data["tenantName"] ?: "", data["monthlyRent"] ?: 0, data["agreementStart"] ?: "", data["agreementEnd"] ?: "", data["notes"] ?: "", id))
    }
    fun assetSummary(): Map<String, Any?> = mapOf(
        "count" to ((db.scalar("SELECT COUNT(*) FROM assets") as? Number)?.toLong() ?: 0),
        "value" to ((db.scalar("SELECT COALESCE(SUM(current_value),0) FROM assets") as? Number)?.toDouble() ?: 0.0),
        "rented" to ((db.scalar("SELECT COUNT(*) FROM assets WHERE status='Given rent'") as? Number)?.toLong() ?: 0),
    )

    fun usersList() = db.all("SELECT id,username,full_name,role,email,phone,is_active,is_locked,last_login_at,must_change_pwd,created_at FROM users ORDER BY id")
    fun userCreate(data: Map<String, Any?>): Long {
        val (stored, salt) = Crypto.hashPassword(data["password"]?.toString() ?: error("Password required"))
        val id = db.run("INSERT INTO users (username,full_name,password_hash,password_salt,role,email,phone,is_active) VALUES (?,?,?,?,?,?,?,1)",
            arrayOf(data["username"], data["fullName"], stored, salt, data["role"] ?: "Staff", data["email"] ?: "", data["phone"] ?: ""))
        auth.audit("CREATE", "users", id, "Created user ${data["username"]}"); return id
    }
    fun userUpdate(id: Long, data: Map<String, Any?>) {
        db.run("UPDATE users SET full_name=?,role=?,email=?,phone=?,is_active=?,updated_at=datetime('now') WHERE id=?",
            arrayOf(data["fullName"], data["role"], data["email"] ?: "", data["phone"] ?: "", if (data["isActive"] == false || data["isActive"] == 0) 0 else 1, id))
    }
    fun userToggleLock(id: Long, locked: Boolean) {
        db.run("UPDATE users SET is_locked=?,locked_until=CASE WHEN ?=1 THEN datetime('now','+15 minutes') ELSE NULL END,updated_at=datetime('now') WHERE id=?", arrayOf(if (locked) 1 else 0, if (locked) 1 else 0, id))
    }
    fun userResetPassword(id: Long, password: String) {
        val (stored, salt) = Crypto.hashPassword(password)
        db.run("UPDATE users SET password_hash=?,password_salt=?,must_change_pwd=1,failed_attempts=0,is_locked=0,updated_at=datetime('now') WHERE id=?", arrayOf(stored, salt, id))
        auth.audit("RESET_PASSWORD", "users", id, "Password reset")
    }
    fun auditList(search: String? = null, page: Int? = null, pageSize: Int? = null): PageResult {
        val where = mutableListOf("1=1"); val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) { where += "(username LIKE ? OR action LIKE ? OR module LIKE ? OR description LIKE ?)"; val t = like(search); repeat(4) { params += t } }
        val w = where.joinToString(" AND ")
        return page("SELECT * FROM audit_log WHERE $w ORDER BY id DESC", "SELECT COUNT(*) FROM audit_log WHERE $w", params, page, pageSize)
    }
    fun settingsLoad(): Map<String, Any?> = db.one("SELECT * FROM settings WHERE id=1") ?: emptyMap()
    fun settingsSave(data: Map<String, Any?>) {
        db.run("UPDATE settings SET mahallu_name=?,address=?,phone=?,email=?,financial_year_start=?,currency_symbol=?,theme=?,language=?,auto_backup=?,backup_interval_hours=?,receipt_prefix=?,wakf_reg_no=?,society_reg_no=?,village=?,panchayath=?,taluk=?,district=?,pincode=?,state=?,subscription_monthly_amount=?,subscription_frequency=?,updated_at=datetime('now') WHERE id=1",
            arrayOf(data["mahalluName"] ?: data["mahallu_name"] ?: "Minz Mahallu", data["address"] ?: "", data["phone"] ?: "", data["email"] ?: "", data["financialYearStart"] ?: data["financial_year_start"] ?: "04-01", data["currencySymbol"] ?: data["currency_symbol"] ?: "₹", data["theme"] ?: "light", data["language"] ?: "en", if (data["autoBackup"] == false || data["autoBackup"] == 0) 0 else 1, data["backupIntervalHours"] ?: data["backup_interval_hours"] ?: 24, data["receiptPrefix"] ?: data["receipt_prefix"] ?: "RCP", data["wakfRegNo"] ?: data["wakf_reg_no"] ?: "", data["societyRegNo"] ?: data["society_reg_no"] ?: "", data["village"] ?: "", data["panchayath"] ?: "", data["taluk"] ?: "", data["district"] ?: "", data["pincode"] ?: "", data["state"] ?: "", data["subscriptionMonthlyAmount"] ?: data["subscription_monthly_amount"] ?: 100, data["subscriptionFrequency"] ?: data["subscription_frequency"] ?: "Monthly"))
        Format.currencySymbol = (data["currencySymbol"] ?: data["currency_symbol"] ?: "₹").toString()
        auth.audit("UPDATE", "settings", 1, "Settings saved")
    }
    fun backupCreate(dir: File): File {
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dest = File(dir, "mms-backup-$stamp.mmbak")
        check(db.backupTo(dest)) { "Backup failed" }
        auth.audit("BACKUP", "backup", null, dest.name); return dest
    }
    fun backupList(dir: File): List<Map<String, Any?>> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.extension in listOf("mmbak", "db") || f.name.endsWith(".mmbak") }?.sortedByDescending { it.lastModified() }?.map { mapOf("name" to it.name, "path" to it.absolutePath, "size" to it.length(), "modified" to it.lastModified()) }.orEmpty()
    }
    fun backupRestore(file: File): Boolean {
        val ok = db.restoreFrom(file); if (ok) auth.audit("RESTORE", "backup", null, file.name); return ok
    }
    fun globalSearch(q: String): List<Map<String, Any?>> {
        if (q.isBlank()) return emptyList()
        val t = like(q); val out = mutableListOf<Map<String, Any?>>()
        db.all("SELECT id,family_number AS code,house_name AS title,'family' AS kind FROM families WHERE family_number LIKE ? OR house_name LIKE ? OR phone LIKE ? LIMIT 8", arrayOf(t, t, t)).forEach { out += it }
        db.all("SELECT id,member_code AS code,name AS title,'member' AS kind FROM members WHERE name LIKE ? OR member_code LIKE ? OR mobile LIKE ? LIMIT 8", arrayOf(t, t, t)).forEach { out += it }
        db.all("SELECT id,receipt_number AS code,donor_name AS title,'donation' AS kind FROM donations WHERE donor_name LIKE ? OR receipt_number LIKE ? LIMIT 6", arrayOf(t, t)).forEach { out += it }
        return out
    }
    fun appInfo() = mapOf("name" to "Minz Mahallu Management System", "version" to "2.0.0", "platform" to "Android")

    // ------------------------------------------------------- detail / receipt helpers

    fun certificateGet(id: Long) = db.one("SELECT * FROM certificates WHERE id=?", arrayOf(id.toString()))

    fun userGet(id: Long) = db.one("SELECT id,username,full_name,role,email,phone,is_active,is_locked,last_login_at,must_change_pwd,created_at FROM users WHERE id=?", arrayOf(id.toString()))

    fun subscriptionPayments(subId: Long): List<Map<String, Any?>> {
        return try {
            db.all("SELECT * FROM subscription_payments WHERE subscription_id=? ORDER BY payment_date DESC, id DESC", arrayOf(subId.toString()))
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun familyDetail(id: Long): Map<String, Any?> {
        val fam = familyGet(id) ?: error("Family not found")
        val members = db.all("SELECT * FROM members WHERE family_id=? ORDER BY is_head DESC, name ASC", arrayOf(id.toString()))
        val sub = db.one("SELECT s.*, p.name AS plan_name FROM subscriptions s LEFT JOIN subscription_plans p ON p.id=s.plan_id WHERE s.family_id=? LIMIT 1", arrayOf(id.toString()))
        return mapOf("family" to fam, "members" to members, "subscription" to (sub ?: emptyMap<String, Any?>()))
    }

    fun memberFull(id: Long): Map<String, Any?>? =
        db.one("SELECT m.*, f.family_number, f.house_name, f.ward, f.area, f.phone AS family_phone FROM members m LEFT JOIN families f ON f.id=m.family_id WHERE m.id=?", arrayOf(id.toString()))

    fun auditModules(): List<String> =
        db.all("SELECT DISTINCT module AS m FROM audit_log WHERE module IS NOT NULL AND module != '' ORDER BY module").map { Format.str(it, "m") }

    fun voidedTransactions(): List<Map<String, Any?>> =
        db.all("SELECT t.*, u.username AS voided_by_name, u2.username AS created_by_name FROM transactions t LEFT JOIN users u ON u.id=t.voided_by LEFT JOIN users u2 ON u2.id=t.created_by WHERE t.status='Void' ORDER BY t.voided_at DESC, t.id DESC")

    // ------------------------------------------------------- WhatsApp / receipts

    fun familiesWithPhones(search: String? = null, limit: Int = 200): List<Map<String, Any?>> {
        val where = mutableListOf("f.status='Active'", "(COALESCE(f.phone,'') != '' OR COALESCE(f.whatsapp_phone,'') != '')")
        val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) {
            where += "(f.family_number LIKE ? OR f.house_name LIKE ? OR f.phone LIKE ? OR f.whatsapp_phone LIKE ?)"
            val t = like(search); repeat(4) { params += t }
        }
        params += limit.toString()
        return db.all(
            "SELECT f.id, f.family_number, f.house_name, f.ward, f.area, f.phone, f.whatsapp_phone, " +
                "(SELECT m.name FROM members m WHERE m.family_id=f.id AND IFNULL(m.archive_state,0)=0 AND (m.is_head=1 OR m.relationship='Head') LIMIT 1) AS head_name " +
                "FROM families f WHERE ${where.joinToString(" AND ")} ORDER BY f.family_number ASC LIMIT ?",
            params.toTypedArray()
        )
    }

    fun recentReceipts(limit: Int = 40): List<Map<String, Any?>> {
        return try {
            db.all(
                "SELECT 'donation' AS kind, d.id AS ref_id, d.receipt_number AS receipt, d.donor_name AS party, " +
                    "d.amount AS amount, d.donation_date AS date, d.payment_method AS method, " +
                    "COALESCE(c.name,'') AS category, COALESCE(f.phone,'') AS phone, COALESCE(f.whatsapp_phone,'') AS wa " +
                    "FROM donations d LEFT JOIN donation_categories c ON c.id=d.category_id LEFT JOIN families f ON f.id=d.family_id " +
                    "WHERE d.receipt_number IS NOT NULL AND d.receipt_number != '' " +
                    "UNION ALL " +
                    "SELECT 'subscription', s.id, s.receipt_number, COALESCE(NULLIF(f.house_name,''), f.family_number), " +
                    "s.amount_paid, s.payment_date, s.payment_method, 'Subscription', COALESCE(f.phone,''), COALESCE(f.whatsapp_phone,'') " +
                    "FROM subscriptions s LEFT JOIN families f ON f.id=s.family_id " +
                    "WHERE s.receipt_number IS NOT NULL AND s.receipt_number != '' " +
                    "ORDER BY date DESC LIMIT ?",
                arrayOf(limit.toString())
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun receiptText(kind: String, refId: Long): String {
        val s = settingsLoad()
        val org = Format.str(s, "mahallu_name").ifBlank { "Minz Mahallu" }
        return if (kind == "donation") {
            val d = donationGet(refId) ?: return "Receipt not found"
            buildString {
                appendLine("*$org*")
                appendLine("Donation Receipt")
                appendLine("------------------------")
                appendLine("Receipt : ${Format.str(d, "receipt_number")}")
                appendLine("Date    : ${Format.str(d, "donation_date")}")
                appendLine("Donor   : ${Format.str(d, "donor_name")}")
                appendLine("Category: ${Format.str(d, "category_name")}")
                appendLine("Amount  : ${Format.money(Format.num(d, "amount"))}")
                appendLine("Method  : ${Format.str(d, "payment_method")}")
                appendLine("------------------------")
                appendLine("Jazakumullahu Khairan!")
            }
        } else {
            val d = subscriptionGet(refId) ?: return "Receipt not found"
            buildString {
                appendLine("*$org*")
                appendLine("Subscription Receipt")
                appendLine("------------------------")
                appendLine("Receipt : ${Format.str(d, "receipt_number")}")
                appendLine("Date    : ${Format.str(d, "payment_date")}")
                appendLine("Family  : ${Format.str(d, "house_name")} (${Format.str(d, "family_number")})")
                appendLine("Amount  : ${Format.money(Format.num(d, "amount_paid"))}")
                appendLine("Method  : ${Format.str(d, "payment_method")}")
                appendLine("Status  : ${Format.str(d, "status")}")
                appendLine("------------------------")
                appendLine("Jazakumullahu Khairan!")
            }
        }
    }

    // ------------------------------------------------------- record actions

    fun certificateSetStatus(id: Long, status: String) {
        require(status in listOf("Issued", "Revoked", "Expired")) { "Invalid status" }
        db.run("UPDATE certificates SET status=? WHERE id=?", arrayOf(status, id))
        auth.audit(if (status == "Revoked") "REVOKE" else "UPDATE", "certificates", id, "Certificate $status")
    }

    fun certificateReprint(id: Long) {
        db.run("UPDATE certificates SET reprint_count=COALESCE(reprint_count,0)+1 WHERE id=?", arrayOf(id))
        auth.audit("REPRINT", "certificates", id, "Certificate reprinted")
    }

    fun tokenSetAmount(id: Long, amount: Double) {
        require(amount >= 0) { "Amount cannot be negative" }
        db.run("UPDATE tokens SET amount=? WHERE id=?", arrayOf(amount, id))
    }

    fun tokenEventDelete(id: Long) {
        val collected = (db.scalar("SELECT COUNT(*) FROM tokens WHERE event_id=? AND status='Collected'", arrayOf(id.toString())) as? Number)?.toLong() ?: 0L
        if (collected > 0) error("Cannot delete: $collected token(s) already collected")
        db.transaction {
            db.run("DELETE FROM tokens WHERE event_id=?", arrayOf(id))
            db.run("DELETE FROM token_events WHERE id=?", arrayOf(id))
        }
        auth.audit("DELETE", "tokens", id, "Deleted token event")
    }

    fun backupDelete(file: File): Boolean {
        return try {
            val ok = file.delete()
            try { File(file.path + "-wal").delete() } catch (_: Exception) { }
            try { File(file.path + "-shm").delete() } catch (_: Exception) { }
            if (ok) auth.audit("DELETE_BACKUP", "backup", null, file.name)
            ok
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------- reports

    fun defaultersList(): List<Map<String, Any?>> {
        return try {
            db.all("SELECT * FROM v_defaulters ORDER BY due_amount DESC")
        } catch (_: Exception) {
            db.all(
                "SELECT f.id AS family_id, f.family_number, f.house_name, f.phone, " +
                    "COUNT(s.id) AS pending_count, COALESCE(SUM(s.amount - s.amount_paid),0) AS due_amount " +
                    "FROM families f LEFT JOIN subscriptions s ON s.family_id=f.id AND s.status IN ('Pending','Overdue','Partial') " +
                    "WHERE f.status='Active' GROUP BY f.id HAVING pending_count > 0 ORDER BY due_amount DESC"
            )
        }
    }

    fun reportDonationsByCategory(from: String, to: String): List<Map<String, Any?>> =
        db.all(
            "SELECT c.name AS category, COUNT(d.id) AS count, COALESCE(SUM(d.amount),0) AS total " +
                "FROM donation_categories c LEFT JOIN donations d ON d.category_id=c.id AND d.donation_date BETWEEN ? AND ? " +
                "GROUP BY c.id, c.name ORDER BY total DESC",
            arrayOf(from, to)
        )

    fun reportDonationsByMonth(months: Int = 12): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        for (i in (months - 1) downTo 0) {
            val key = db.scalar("SELECT strftime('%Y-%m', date('now', ?))", arrayOf("-${i} months"))?.toString() ?: continue
            val label = db.scalar("SELECT strftime('%b %Y', date('now', ?))", arrayOf("-${i} months"))?.toString() ?: key
            val total = (db.scalar("SELECT COALESCE(SUM(amount),0) FROM donations WHERE strftime('%Y-%m',donation_date)=?", arrayOf(key)) as? Number)?.toDouble() ?: 0.0
            val count = (db.scalar("SELECT COUNT(*) FROM donations WHERE strftime('%Y-%m',donation_date)=?", arrayOf(key)) as? Number)?.toLong() ?: 0L
            out += mapOf("month" to label, "key" to key, "total" to total, "count" to count)
        }
        return out
    }

    fun reportMonthlyFinance(months: Int = 12): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        for (i in (months - 1) downTo 0) {
            val key = db.scalar("SELECT strftime('%Y-%m', date('now', ?))", arrayOf("-${i} months"))?.toString() ?: continue
            val label = db.scalar("SELECT strftime('%b %Y', date('now', ?))", arrayOf("-${i} months"))?.toString() ?: key
            fun sum(sql: String) = (db.scalar(sql, arrayOf(key)) as? Number)?.toDouble() ?: 0.0
            val income = sum("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='Income' AND (status IS NULL OR status!='Void') AND strftime('%Y-%m',txn_date)=?")
            val expense = sum("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='Expense' AND (status IS NULL OR status!='Void') AND strftime('%Y-%m',txn_date)=?")
            val don = sum("SELECT COALESCE(SUM(amount),0) FROM donations WHERE strftime('%Y-%m',donation_date)=?")
            val sub = sum("SELECT COALESCE(SUM(amount_paid),0) FROM subscriptions WHERE strftime('%Y-%m',payment_date)=?")
            out += mapOf("month" to label, "income" to income, "expense" to expense, "donations" to don, "subscriptions" to sub, "net" to (income + don + sub - expense))
        }
        return out
    }

    fun reportWelfareSummary(): List<Map<String, Any?>> =
        db.all("SELECT status, COUNT(*) AS count, COALESCE(SUM(amount_requested),0) AS requested, COALESCE(SUM(amount_approved),0) AS approved FROM welfare_requests GROUP BY status ORDER BY count DESC")

    fun reportCertificateLog(): List<Map<String, Any?>> =
        db.all("SELECT certificate_number, type, issued_to, issued_date, status, verification_code FROM certificates ORDER BY issued_date DESC, id DESC LIMIT 500")

    fun reportMemberRoll(): List<Map<String, Any?>> {
        return try {
            db.all("SELECT member_code, name, gender, mobile, status, family_number, house_name, ward FROM v_member_directory ORDER BY family_number ASC, name ASC LIMIT 2000")
        } catch (_: Exception) {
            db.all("SELECT m.member_code, m.name, m.gender, m.mobile, m.status, f.family_number, f.house_name, f.ward FROM members m LEFT JOIN families f ON f.id=m.family_id ORDER BY f.family_number ASC, m.name ASC LIMIT 2000")
        }
    }

    fun reportFamilyDirectory(): List<Map<String, Any?>> =
        db.all(
            "SELECT f.family_number, f.house_name, f.ward, f.area, f.phone, f.status, " +
                "(SELECT COUNT(*) FROM members m WHERE m.family_id=f.id AND IFNULL(m.archive_state,0)=0) AS members " +
                "FROM families f ORDER BY f.family_number ASC LIMIT 2000"
        )

    fun reportCollectionRegister(from: String, to: String): List<Map<String, Any?>> =
        db.all(
            "SELECT s.receipt_number AS receipt, s.payment_date AS date, f.family_number AS family, f.house_name AS house, " +
                "s.amount_paid AS amount, s.payment_method AS method, s.status AS status " +
                "FROM subscriptions s LEFT JOIN families f ON f.id=s.family_id " +
                "WHERE s.payment_date BETWEEN ? AND ? AND s.amount_paid > 0 ORDER BY s.payment_date DESC LIMIT 2000",
            arrayOf(from, to)
        )
}
