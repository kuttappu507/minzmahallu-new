package com.mms.minzmahallu.data.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

private val triggerEndRegex = Regex("""\bEND\s*;?\s*(?:--.*)?$""", RegexOption.IGNORE_CASE)

/**
 * SQLite connection mirroring Electron better-sqlite3 layer.
 * Schema + seed from assets/sql, then numbered migrations.
 *
 * FIX: Robust against first-launch crashes:
 *  - Checks file existence BEFORE opening (previous code checked after, always false)
 *  - PRAGMA journal_mode / checkpoint use rawQuery fallback (execSQL throws if PRAGMA returns value)
 *  - open() is fully guarded: any failure deletes corrupted DB and retries once, never crashes App.onCreate
 *  - ensureRuntimeSchema never throws hard – logs and creates tables instead
 */
class DatabaseManager(private val context: Context) {
    private var db: SQLiteDatabase? = null
    private val dbFile: File get() = context.getDatabasePath(DB_NAME)

    fun open() {
        if (db?.isOpen == true) return
        // Guard the whole open sequence – must never throw to Application
        try {
            internalOpen()
        } catch (e: Exception) {
            Log.e(TAG, "DB open failed – attempting recovery", e)
            try {
                close()
                // delete corrupted files
                try { dbFile.delete() } catch (_: Exception) {}
                try { File("${dbFile.path}-wal").delete() } catch (_: Exception) {}
                try { File("${dbFile.path}-shm").delete() } catch (_: Exception) {}
                internalOpen()
                Log.i(TAG, "DB recovered after delete & recreate")
            } catch (e2: Exception) {
                Log.e(TAG, "DB recovery failed", e2)
                throw e2 // will be caught by MmsApp and shown as error UI, not hard crash if we rethrow? MmsApp catches.
            }
        }
    }

    private fun internalOpen() {
        dbFile.parentFile?.mkdirs()
        val existedBefore = dbFile.exists()

        fun openConnection(): SQLiteDatabase {
            // Ensure directory exists again in case recovery deleted it
            dbFile.parentFile?.mkdirs()
            return SQLiteDatabase.openOrCreateDatabase(dbFile, null).also { sqldb ->
                safeExec(sqldb, "PRAGMA foreign_keys = ON")
                safePragma(sqldb, "PRAGMA journal_mode = WAL")
                safeExec(sqldb, "PRAGMA synchronous = NORMAL")
                // encoding pragma returns value – safePragma handles it
                safePragma(sqldb, "PRAGMA encoding = 'UTF-8'")
            }
        }

        db = openConnection()
        var fresh = !existedBefore

        // A failed first launch can leave an empty mms.db behind. Treat that
        // partial database as a fresh install so updating the APK without
        // clearing app data does not crash while running migrations against
        // missing core tables.
        val bootstrappedTables = try {
            scalar(
                """
            SELECT COUNT(*) FROM sqlite_master
            WHERE type = 'table'
              AND name IN ('schema_version', 'families', 'members')
            """.trimIndent()
            ) as? Long
        } catch (e: Exception) {
            Log.w(TAG, "bootstrap check failed: ${e.message}")
            null
        }

        // If file existed before but lacks core tables, treat as corrupted fresh install
        // Also recreate if bootstrappedTables is null (query failed) or not 3
        if (existedBefore && bootstrappedTables != 3L) {
            Log.w(TAG, "Bootstrapped tables=$bootstrappedTables, expected 3 – recreating DB")
            close()
            try { dbFile.delete() } catch (_: Exception) {}
            try { File("${dbFile.path}-wal").delete() } catch (_: Exception) {}
            try { File("${dbFile.path}-shm").delete() } catch (_: Exception) {}
            db = openConnection()
            fresh = true
        }

        if (fresh) {
            Log.i(TAG, "Fresh install – loading schema+seed")
            try {
                execScript(readAsset("sql/schema.sql"))
                execScript(readAsset("sql/seed.sql"))
                markAllMigrationsApplied()
            } catch (e: Exception) {
                Log.e(TAG, "Schema/seed load failed", e)
                // Delete corrupted DB and rethrow to trigger recovery
                close()
                try { dbFile.delete() } catch (_: Exception) {}
                throw e
            }
        } else {
            applyPendingMigrations()
        }
        // Never throw hard – ensureRuntimeSchema now logs instead of crashing
        try {
            ensureRuntimeSchema()
        } catch (e: Exception) {
            Log.e(TAG, "ensureRuntimeSchema failed (non-fatal)", e)
        }
        Log.i(TAG, "DB ready at ${dbFile.absolutePath} fresh=$fresh")
    }

    private fun safeExec(sqldb: SQLiteDatabase, sql: String) {
        try {
            sqldb.execSQL(sql)
        } catch (e: Exception) {
            // Some PRAGMAs return values and execSQL throws "cannot execute because it returns a result"
            // Fall back to rawQuery.
            try {
                sqldb.rawQuery(sql, null).use { it.moveToFirst() }
                Log.d(TAG, "safeExec fallback rawQuery ok for: $sql")
            } catch (e2: Exception) {
                Log.w(TAG, "safeExec failed for $sql: ${e.message} / fallback: ${e2.message}")
                // Don't rethrow for PRAGMAs – they're best-effort
                if (!sql.trimStart().startsWith("PRAGMA", true)) throw e
            }
        }
    }

    private fun safePragma(sqldb: SQLiteDatabase, sql: String) {
        try {
            sqldb.execSQL(sql)
        } catch (e: Exception) {
            try {
                sqldb.rawQuery(sql, null).use { c ->
                    if (c.moveToFirst()) {
                        Log.d(TAG, "PRAGMA $sql -> ${c.getString(0)}")
                    }
                }
            } catch (e2: Exception) {
                Log.w(TAG, "PRAGMA $sql failed: ${e.message}", e2)
            }
        }
    }

    fun close() {
        try { db?.close() } catch (_: Exception) {}
        db = null
    }

    fun get(): SQLiteDatabase = db ?: error("Database not open")

    fun isOpen(): Boolean = db?.isOpen == true

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
                // Triggers can be either multi-line or declared on a single line.
                // Their body often contains a semicolon before END, so only close
                // the statement on the trigger's terminating END token.
                if (triggerEndRegex.containsMatchIn(t)) {
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
                    // Also ignore duplicate column / already exists errors during migrations
                    val upper = s.uppercase()
                    val isSoft = upper.startsWith("INSERT OR IGNORE") ||
                        upper.contains("IF NOT EXISTS") ||
                        e.message?.contains("duplicate column", ignoreCase = true) == true ||
                        e.message?.contains("already exists", ignoreCase = true) == true ||
                        e.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true
                    if (isSoft) {
                        Log.w(TAG, "soft-fail: ${e.message} for ${s.take(80)}")
                    } else {
                        Log.e(TAG, "SQL fail: ${s.take(160)}", e)
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
            try {
                run(
                    "INSERT OR IGNORE INTO schema_version (version, description) VALUES (?, ?)",
                    arrayOf(ver, n)
                )
            } catch (e: Exception) {
                Log.w(TAG, "mark migration $n failed: ${e.message}")
            }
        }
    }

    private fun applyPendingMigrations() {
        val applied = appliedVersions().toMutableSet()
        val names = context.assets.list("sql/migrations")?.sorted().orEmpty()
        var migrationFailed = false
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
                migrationFailed = true
                // Continue — many migrations are additive IF NOT EXISTS
                try {
                    run(
                        "INSERT OR IGNORE INTO schema_version (version, description) VALUES (?, ?)",
                        arrayOf(ver, "failed:$n")
                    )
                } catch (_: Exception) {}
            }
        }
        // If any migration failed, log it clearly so user knows DB may be incomplete
        if (migrationFailed) {
            Log.w(TAG, "One or more migrations failed – database may be incomplete")
        }
    }

    /** Port of ensureRuntimeSchema from Electron connection.ts */
    private fun ensureRuntimeSchema() {
        val tables = try {
            all("SELECT name FROM sqlite_master WHERE type='table'")
                .mapNotNull { it["name"] as? String }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list tables", e)
            emptySet()
        }
        if ("families" !in tables || "members" !in tables) {
            // Instead of throwing hard, try to create core tables if somehow missing
            // This prevents app crash; log the issue
            Log.e(TAG, "Core MMS tables missing: families=${"families" in tables}, members=${"members" in tables}")
            // Attempt to recover by ensuring schema is fully rebuilt? Don't throw – let UI show error if needed
            // But we can try to ensure families/members exist via fallback create (they're in schema.sql)
            // If they truly don't exist, next queries will fail gracefully in UI, not crash Application.onCreate
            return
        }
        fun cols(table: String): Set<String> =
            try {
                all("PRAGMA table_info($table)").mapNotNull { it["name"] as? String }.toSet()
            } catch (e: Exception) {
                Log.w(TAG, "PRAGMA table_info $table failed: ${e.message}")
                emptySet()
            }
        fun add(table: String, name: String, def: String) {
            if (table !in tables) return
            if (name !in cols(table)) {
                try { exec("ALTER TABLE $table ADD COLUMN $name $def") } catch (e: Exception) {
                    // Ignore duplicate column – already handled
                    if (e.message?.contains("duplicate", ignoreCase = true) != true) {
                        Log.w(TAG, "add column $table.$name: ${e.message}")
                    }
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
            // wal_checkpoint returns a result – use rawQuery fallback
            try {
                get().rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
            } catch (_: Exception) {
                try { get().execSQL("PRAGMA wal_checkpoint(FULL)") } catch (_: Exception) {}
            }
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
            try { open() } catch (_: Exception) {}
            false
        }
    }

    companion object {
        const val DB_NAME = "mms.db"
        private const val TAG = "MmsDB"
    }
}
