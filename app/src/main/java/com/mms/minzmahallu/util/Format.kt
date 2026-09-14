package com.mms.minzmahallu.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Format {
    // Asia/Kolkata – use TimeZone API compatible with API 26 without desugaring
    private val ist: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val df = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = ist }
    private val monthFmt = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = ist }
    var currencySymbol: String = "₹"

    fun today(): String = synchronized(df) { df.format(Date()) }
    fun year(): Int {
        val c = Calendar.getInstance(ist, Locale.US)
        return c.get(Calendar.YEAR)
    }
    fun monthKey(): String = synchronized(monthFmt) { monthFmt.format(Date()) }

    fun money(amount: Double?, symbol: String = currencySymbol): String {
        val n = amount ?: 0.0
        val fmt = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "$symbol${fmt.format(n)}"
    }

    fun moneyShort(amount: Double?): String {
        val n = amount ?: 0.0
        return when {
            kotlin.math.abs(n) >= 10_000_000 -> "${currencySymbol}%.1fCr".format(n / 10_000_000)
            kotlin.math.abs(n) >= 100_000 -> "${currencySymbol}%.1fL".format(n / 100_000)
            kotlin.math.abs(n) >= 1_000 -> "${currencySymbol}%.1fK".format(n / 1_000)
            else -> money(n)
        }
    }

    fun str(row: Map<String, Any?>, key: String, fallback: String = ""): String =
        row[key]?.toString() ?: fallback

    fun num(row: Map<String, Any?>, key: String): Double =
        when (val v = row[key]) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

    fun long(row: Map<String, Any?>, key: String): Long =
        when (val v = row[key]) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: 0L
            else -> 0L
        }

    fun nextCode(existing: List<String>, prefix: String, pad: Int = 4): String {
        var max = 0
        val re = Regex("""(\d+)\s*$""")
        for (n in existing) {
            val m = re.find(n) ?: continue
            max = maxOf(max, m.groupValues[1].toIntOrNull() ?: 0)
        }
        return "$prefix-${(max + 1).toString().padStart(pad, '0')}"
    }

    fun nextRegister(existing: List<String>, prefix: String, withYear: Boolean = true, pad: Int = 4): String {
        val year = year()
        val filtered = if (withYear) existing.filter { it.contains("$year") || it.startsWith("$prefix-") } else existing
        var max = 0
        val re = Regex("""(\d+)\s*$""")
        for (n in filtered) {
            val m = re.find(n) ?: continue
            max = maxOf(max, m.groupValues[1].toIntOrNull() ?: 0)
        }
        val body = (max + 1).toString().padStart(pad, '0')
        return if (withYear) "$prefix-$year-$body" else "$prefix-$body"
    }
}
