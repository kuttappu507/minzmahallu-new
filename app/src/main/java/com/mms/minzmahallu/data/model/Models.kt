package com.mms.minzmahallu.data.model

data class AuthUser(
    val id: Long,
    val username: String,
    val fullName: String,
    val role: String,
    val isActive: Boolean,
    val mustChangePwd: Boolean,
    val initials: String,
)

data class PageResult(
    val rows: List<Map<String, Any?>>,
    val total: Int,
)

data class DashboardSummary(
    val totalFamilies: Long = 0,
    val totalMembers: Long = 0,
    val activeMembers: Long = 0,
    val monthlyCollection: Double = 0.0,
    val pendingDues: Double = 0.0,
    val monthlyDonations: Double = 0.0,
    val welfareBeneficiaries: Long = 0,
    val marriagesThisYear: Long = 0,
    val deathsThisYear: Long = 0,
    val balance: Double = 0.0,
    val incomeThisMonth: Double = 0.0,
    val expenseThisMonth: Double = 0.0,
)

data class ChartPoint(val label: String, val value: Double, val value2: Double = 0.0)

data class ToastMsg(
    val id: Long,
    val message: String,
    val kind: Kind = Kind.Info,
) {
    enum class Kind { Info, Success, Warn, Error }
}

/** Navigation destinations matching Electron routes */
enum class Dest(
    val route: String,
    val titleKey: String,
    val tint: String,
    val section: String? = null,
) {
    Dashboard("dashboard", "nav_dashboard", "em"),
    Families("families", "nav_families", "em", "Management"),
    Members("members", "nav_members", "teal", "Management"),
    Staff("staff", "nav_staff", "vio", "Management"),
    Committee("committee", "nav_committee", "cyan", "Management"),
    Subscriptions("subscriptions", "nav_subscriptions", "gold", "Management"),
    Donations("donations", "nav_donations", "pink", "Management"),
    WhatsApp("whatsapp", "nav_whatsapp", "teal", "Management"),
    Accounting("accounting", "nav_accounting", "sky", "Finance"),
    Assets("assets", "nav_assets", "teal", "Finance"),
    Marriages("marriages", "nav_marriage", "vio", "Registers"),
    Deaths("deaths", "nav_death", "slate", "Registers"),
    Welfare("welfare", "nav_welfare", "orange", "Registers"),
    Certificates("certificates", "nav_certificates", "cyan", "Registers"),
    Tokens("tokens", "nav_tokens", "pink", "Registers"),
    Reports("reports", "nav_reports", "blue", "System"),
    Settings("settings", "nav_settings", "vio", "System"),
    Users("users", "nav_users", "blue", "System"),
    Audit("audit", "nav_audit", "gold", "System"),
    Backup("backup", "nav_backup", "teal", "System");

    companion object {
        val all = entries
        fun fromRoute(r: String) = entries.find { it.route == r } ?: Dashboard
    }
}
