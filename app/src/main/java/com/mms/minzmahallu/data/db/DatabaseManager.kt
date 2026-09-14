package com.mms.minzmahallu.data.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File

/**
 * SQLite connection mirroring Electron better-sqlite3 layer.
 * Schema + seed from assets/sql, then numbered migrations.
 */
class DatabaseManager(private val context: Context) {
    private var db: SQLiteDatabase? = null
    private val dbFile: File get() = context.getDatabasePath(DB_NAME)

    fun open() {
        if (db?.isOpen == true) return
        dbFile.parentFile?.mkdirs()
        val fresh = !dbFile.exists()
        db = SQLiteDatabase.openOrCreateDatabase(dbFile, null).also {
            it.execSQL("PRAGMA foreign_keys = ON")
            it.execSQL("PRAGMA journal_mode = WAL")
            it.execSQL("PRAGMA synchronous = NORMAL")
            it.execSQL("PRAGMA encoding = 'UTF-8'")
        }
        if (fresh) {
            execScript(readAsset("sql/schema.sql"))
            execScript(readAsset("sql/seed.sql"))
            markAllMigrationsApplied()
        } else {
            applyPendingMigrations()
        }
        ensureRuntimeSchema()
        Log.i(TAG, "DB ready at ${dbFile.absolutePath}")
    }

    fun close() { db?.close(); db = null }

    fun get(): SQLiteDatabase = db ?: error("Database not open")

    fun all(sql: String, args: Array<String> = emptyArray()): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        get().rawQuery(sql, args).use { c ->
            val cols = c.columnNames
            while (c.moveToNext()) {
                val row = linkedMapOf<String, Any?>()
                cols.forEachIndexed { i, name ->
                    row[name] = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                        else -> c.getString(i)
                    }
                }
                out += row
            }
        }
        return out
    }

    fun one(sql: String, args: Array<String> = emptyArray()): Map<String, Any?>? =
        all(sql, args).firstOrNull()

    fun scalar(sql: String, args: Array<String> = emptyArray()): Any? {
        get().rawQuery(sql, args).use { c ->
            if (!c.moveToFirst()) return null
            return when (c.getType(0)) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_INTEGER -> c.getLong(0)
                Cursor.FIELD_TYPE_FLOAT -> c.getDouble(0)
                else -> c.getString(0)
            }
        }
    }

    fun run(sql: String, args: Array<Any?> = emptyArray()): Long {
        val s = get().compileStatement(sql)
        args.forEachIndexed { i, v ->
            val idx = i + 1
            when (v) {
                null -> s.bindNull(idx)
                is Int -> s.bindLong(idx, v.toLong())
                is Long -> s.bindLong(idx, v)
                is Float -> s.bindDouble(idx, v.toDouble())
                is Double -> s.bindDouble(idx, v)
                is Boolean -> s.bindLong(idx, if (v) 1 else 0)
                is ByteArray -> s.bindBlob(idx, v)
                else -> s.bindString(idx, v.toString())
            }
        }
        return if (sql.trimStart().startsWith("INSERT", true)) s.executeInsert()
        else { s.executeUpdateDelete().toLong() }
    }

    fun exec(sql: String) = get().execSQL(sql)

    fun transaction(block: () -> Unit) {
        val d = get()
        d.beginTransaction()
        try {
            block()
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun execScript(script: String) {
        // Split on semicolons carefully — keep PRAGMA / multi-line statements
        val cleaned = script
            .lines()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
        val parts = mutableListOf<String>()
        val buf = StringBuilder()
        var inTrigger = false
        for (line in cleaned.lines()) {
            val t = line.trim()
            if (t.uppercase().startsWith("CREATE TRIGGER")) inTrigger = true
            buf.append(line).append('\n')
            if (inTrigger) {
                if (t.uppercase() == "END;" || t.uppercase() == "END") {
                    parts += buf.toString()
                    buf.clear()
                    inTrigger = false
                }
            } else if (t.endsWith(";")) {
                parts += buf.toString()
                buf.clear()
            }
        }
        if (buf.isNotBlank()) parts += buf.toString()
        val d = get()
        d.beginTransaction()
        try {
            for (p in parts) {
                val s = p.trim().trimEnd(';').trim()
                if (s.isEmpty()) continue
                try {
                    d.execSQL(s)
                } catch (e: Exception) {
                    // INSERT OR IGNORE / IF NOT EXISTS should not fail hard
                    if (s.uppercase().startsWith("INSERT OR IGNORE") ||
                        s.uppercase().contains("IF NOT EXISTS")
                    ) {
                        Log.w(TAG, "soft-fail: ${e.message}")
                    } else {
                        Log.e(TAG, "SQL fail: ${s.take(120)}", e)
                        throw e
                    }
                }
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    private fun appliedVersions(): Set<Int> {
        return try {
            all("SELECT version FROM schema_version").mapNotNull {
                (it["version"] as? Number)?.toInt()
            }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun markAllMigrationsApplied() {
        val names = context.assets.list("sql/migrations")?.sorted().orEmpty()
        for (n in names) {
            val m = Regex("""V(\d+)_""").find(n) ?: continue
            val ver = m.groupValues[1].toInt()
            run(
                "INSERT OR IGNORE INTO schema_version (version, description) VALUES (?, ?)",
                arrayOf(ver, n)
            )
        }
    }

    private fun applyPendingMigrations() {
        val applied = appliedVersions().toMutableSet()
        val names = context.assets.list("sql/migrations")?.sorted().orEmpty()
        for (n in names) {
            if (!n.endsWith(".sql") || n.startsWith("README")) continue
            val m = Regex("""V(\d+)_""").find(n) ?: continue
            val ver = m.groupValues[1].toInt()
            if (ver in applied) continue
            Log.i(TAG, "Applying migration $n")
            try {
                execScript(readAsset("sql/migrations/$n"))
                run(
                    "INSERT OR IGNORE INTO schema_version (version, description) VALUES (?, ?)",
                    arrayOf(ver, n)
                )
                applied += ver
            } catch (e: Exception) {
                Log.e(TAG, "Migration $n failed", e)
                // Continue — many migrations are additive IF NOT EXISTS
                run(
                    "INSERT OR IGNORE INTO schema_version (version, description) VALUES (?, ?)",
                    arrayOf(ver, "failed:$n")
                )
            }
        }
    }

    /** Port of ensureRuntimeSchema from Electron connection.ts */
    private fun ensureRuntimeSchema() {
        val tables = all("SELECT name FROM sqlite_master WHERE type='table'")
            .mapNotNull { it["name"] as? String }.toSet()
        if ("families" !in tables || "members" !in tables) {
            throw IllegalStateException("Core MMS tables missing")
        }
        fun cols(table: String): Set<String> =
            all("PRAGMA table_info($table)").mapNotNull { it["name"] as? String }.toSet()
        fun add(table: String, name: String, def: String) {
            if (table !in tables) return
            if (name !in cols(table)) {
                try { exec("ALTER TABLE $table ADD COLUMN $name $def") } catch (e: Exception) {
                    Log.w(TAG, "add column $table.$name: ${e.message}")
                }
            }
        }
        val fields = listOf(
            Triple("settings", "subscription_monthly_amount", "REAL NOT NULL DEFAULT 100"),
            Triple("settings", "subscription_frequency", "TEXT NOT NULL DEFAULT 'Monthly'"),
            Triple("settings", "subscription_quarterly_amount", "REAL NOT NULL DEFAULT 300"),
            Triple("settings", "backup_keep_count", "INTEGER NOT NULL DEFAULT 30"),
            Triple("settings", "affiliation_number", "TEXT"),
            Triple("settings", "committee_term_start", "TEXT"),
            Triple("settings", "committee_term_end", "TEXT"),
            Triple("settings", "wakf_reg_no", "TEXT"),
            Triple("settings", "society_reg_no", "TEXT"),
            Triple("settings", "village", "TEXT"),
            Triple("settings", "panchayath", "TEXT"),
            Triple("settings", "taluk", "TEXT"),
            Triple("settings", "district", "TEXT"),
            Triple("settings", "pincode", "TEXT"),
            Triple("settings", "state", "TEXT"),
            Triple("settings", "demo_data", "INTEGER NOT NULL DEFAULT 0"),
            Triple("settings", "device_fingerprint", "TEXT"),
            Triple("settings", "qr_signing_key", "TEXT"),
            Triple("settings", "backup_mirror_dir", "TEXT"),
            Triple("deaths", "place_of_death", "TEXT"),
            Triple("deaths", "address", "TEXT"),
            Triple("deaths", "registration_date", "TEXT"),
            Triple("deaths", "updated_at", "TEXT"),
            Triple("families", "archived_at", "TEXT"),
            Triple("families", "archived_by", "INTEGER"),
            Triple("families", "archive_reason", "TEXT"),
            Triple("families", "whatsapp_phone", "TEXT"),
            Triple("families", "whatsapp_enabled", "INTEGER NOT NULL DEFAULT 1"),
            Triple("members", "archive_state", "INTEGER NOT NULL DEFAULT 0"),
            Triple("members", "archive_source", "TEXT"),
            Triple("members", "archived_at", "TEXT"),
            Triple("members", "archived_by", "INTEGER"),
            Triple("members", "archive_reason", "TEXT"),
            Triple("members", "father_name", "TEXT"),
            Triple("members", "father_id", "INTEGER"),
            Triple("members", "mother_id", "INTEGER"),
            Triple("members", "spouse_id", "INTEGER"),
            Triple("donations", "transaction_ref", "TEXT"),
            Triple("donations", "updated_at", "TEXT"),
            Triple("donations", "verification_code", "TEXT"),
            Triple("transactions", "transaction_ref", "TEXT"),
            Triple("transactions", "updated_at", "TEXT"),
            Triple("transactions", "voucher_no", "TEXT"),
            Triple("transactions", "bill_no", "TEXT"),
            Triple("transactions", "payee", "TEXT"),
            Triple("transactions", "status", "TEXT NOT NULL DEFAULT 'Posted'"),
            Triple("transactions", "voided_at", "TEXT"),
            Triple("transactions", "voided_by", "INTEGER"),
            Triple("transactions", "void_reason", "TEXT"),
            Triple("transactions", "category", "TEXT"),
            Triple("transactions", "asset_id", "INTEGER"),
            Triple("marriages", "updated_at", "TEXT"),
            Triple("welfare_requests", "request_date", "TEXT"),
            Triple("welfare_requests", "rejection_reason", "TEXT"),
            Triple("welfare_requests", "processed_by", "INTEGER"),
            Triple("welfare_requests", "processed_date", "TEXT"),
            Triple("welfare_requests", "minutes_date", "TEXT"),
            Triple("certificates", "status", "TEXT NOT NULL DEFAULT 'Issued'"),
            Triple("certificates", "verification_code", "TEXT"),
            Triple("certificates", "reprint_count", "INTEGER NOT NULL DEFAULT 0"),
            Triple("audit_log", "metadata", "TEXT"),
            Triple("audit_log", "prev_hash", "TEXT"),
            Triple("audit_log", "entry_hash", "TEXT"),
            Triple("subscriptions", "arrears", "REAL NOT NULL DEFAULT 0"),
            Triple("subscriptions", "advance", "REAL NOT NULL DEFAULT 0"),
            Triple("subscriptions", "verification_code", "TEXT"),
        )
        fields.forEach { (t, n, d) -> add(t, n, d) }

        // Ensure staff / committee / tokens / assets tables exist (from migrations)
        ensureTable("""
            CREATE TABLE IF NOT EXISTS staff (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              staff_code TEXT UNIQUE,
              member_id INTEGER,
              name TEXT NOT NULL,
              role TEXT,
              phone TEXT, email TEXT, address TEXT,
              joined_date TEXT, salary REAL DEFAULT 0,
              payment_frequency TEXT DEFAULT 'Monthly',
              status TEXT NOT NULL DEFAULT 'Active',
              notes TEXT,
              archive_state INTEGER NOT NULL DEFAULT 0,
              archive_source TEXT, archived_at TEXT, archived_by INTEGER, archive_reason TEXT,
              created_at TEXT NOT NULL DEFAULT (datetime('now')),
              updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """.trimIndent())
        ensureTable("""
            CREATE TABLE IF NOT EXISTS staff_payments (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              staff_id INTEGER NOT NULL,
              amount REAL NOT NULL,
              payment_date TEXT NOT NULL,
              period_start TEXT, period_end TEXT,
              payment_method TEXT, receipt_number TEXT,
              remarks TEXT, status TEXT DEFAULT 'Paid',
              cancelled_at TEXT, cancel_reason TEXT,
              created_by INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now')),
              FOREIGN KEY (staff_id) REFERENCES staff(id)
            )
        """.trimIndent())
        ensureTable("""
            CREATE TABLE IF NOT EXISTS committee_members (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              committee_code TEXT UNIQUE,
              member_id INTEGER,
              name TEXT NOT NULL,
              position TEXT, committee_type TEXT,
              phone TEXT, email TEXT, address TEXT,
              term_start TEXT, term_end TEXT,
              status TEXT NOT NULL DEFAULT 'Active',
              notes TEXT,
              archive_state INTEGER NOT NULL DEFAULT 0,
              archive_source TEXT, archived_at TEXT, archived_by INTEGER, archive_reason TEXT,
              created_at TEXT NOT NULL DEFAULT (datetime('now')),
              updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """.trimIndent())
        ensureTable("""
            CREATE TABLE IF NOT EXISTS token_events (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              event_type TEXT,
              event_date TEXT,
              venue TEXT,
              description TEXT,
              status TEXT DEFAULT 'Active',
              target_amount REAL DEFAULT 0,
              created_at TEXT NOT NULL DEFAULT (datetime('now')),
              updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """.trimIndent())
        ensureTable("""
            CREATE TABLE IF NOT EXISTS tokens (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              event_id INTEGER NOT NULL,
              family_id INTEGER,
              token_code TEXT,
              amount REAL DEFAULT 0,
              amount_collected REAL DEFAULT 0,
              status TEXT DEFAULT 'Pending',
              collected_at TEXT, collected_by INTEGER,
              cancelled_at TEXT, cancel_reason TEXT,
              replaced_by INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now')),
              FOREIGN KEY (event_id) REFERENCES token_events(id),
              FOREIGN KEY (family_id) REFERENCES families(id)
            )
        """.trimIndent())
        ensureTable("""
            CREATE TABLE IF NOT EXISTS assets (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              asset_code TEXT UNIQUE,
              name TEXT NOT NULL,
              category TEXT, reference_no TEXT, location TEXT,
              acquisition_date TEXT, acquisition_cost REAL DEFAULT 0,
              current_value REAL DEFAULT 0,
              status TEXT DEFAULT 'In use',
              condition_note TEXT, custodian TEXT,
              income_generating INTEGER DEFAULT 0,
              tenant_name TEXT, monthly_rent REAL DEFAULT 0,
              agreement_start TEXT, agreement_end TEXT,
              notes TEXT,
              created_at TEXT NOT NULL DEFAULT (datetime('now')),
              updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """.trimIndent())
        ensureTable("""
            CREATE TABLE IF NOT EXISTS subscription_payments (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              subscription_id INTEGER NOT NULL,
              amount REAL NOT NULL,
              payment_date TEXT NOT NULL,
              payment_method TEXT, receipt_number TEXT,
              transaction_ref TEXT, remarks TEXT,
              arrears_cleared REAL DEFAULT 0,
              advance_added REAL DEFAULT 0,
              verification_code TEXT,
              status TEXT DEFAULT 'Posted',
              cancelled_at TEXT, cancel_reason TEXT,
              collected_by INTEGER,
              created_at TEXT NOT NULL DEFAULT (datetime('now')),
              FOREIGN KEY (subscription_id) REFERENCES subscriptions(id)
            )
        """.trimIndent())
        ensureTable("""
            CREATE TABLE IF NOT EXISTS family_history (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              family_id INTEGER, action TEXT, reason TEXT,
              actor_id INTEGER, actor_name TEXT,
              metadata TEXT,
              created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """.trimIndent())
        ensureTable("""
            CREATE TABLE IF NOT EXISTS member_history (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              member_id INTEGER, action TEXT, reason TEXT,
              actor_id INTEGER, actor_name TEXT,
              metadata TEXT,
              created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """.trimIndent())
    }

    private fun ensureTable(createSql: String) {
        try { exec(createSql) } catch (e: Exception) {
            Log.w(TAG, "ensureTable: ${e.message}")
        }
    }

    fun backupTo(dest: File): Boolean {
        return try {
            get().execSQL("PRAGMA wal_checkpoint(FULL)")
            dbFile.copyTo(dest, overwrite = true)
            val wal = File(dbFile.path + "-wal")
            val shm = File(dbFile.path + "-shm")
            if (wal.exists()) wal.copyTo(File(dest.path + "-wal"), true)
            if (shm.exists()) shm.copyTo(File(dest.path + "-shm"), true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "backup failed", e); false
        }
    }

    fun restoreFrom(src: File): Boolean {
        return try {
            close()
            src.copyTo(dbFile, overwrite = true)
            open()
            true
        } catch (e: Exception) {
            Log.e(TAG, "restore failed", e)
            open()
            false
        }
    }

    companion object {
        const val DB_NAME = "mms.db"
        private const val TAG = "MmsDB"
    }
}
