package com.mms.minzmahallu

import android.app.Application
import android.util.Log
import com.mms.minzmahallu.data.db.DatabaseManager
import com.mms.minzmahallu.data.repository.MmsRepository
import com.mms.minzmahallu.i18n.I18n

class MmsApp : Application() {
    lateinit var db: DatabaseManager
        private set
    lateinit var repo: MmsRepository
        private set

    // Expose init error so UI can show a non-crash error screen instead of dying
    var initError: Throwable? = null
        private set
    var isDbReady: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Global crash handler – log instead of silent death, helps field debugging
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e("MmsApp", "Uncaught on ${t.name}", e)
            previous?.uncaughtException(t, e)
        }

        // I18n must never crash app – guard it
        try {
            I18n.init(this)
        } catch (e: Exception) {
            Log.e("MmsApp", "I18n init failed", e)
        }

        // DB + Repo – must never throw to Application, otherwise app "fails to open"
        try {
            db = DatabaseManager(this)
            db.open()
            // Quick sanity check – if DB not actually open, treat as not ready
            if (!db.isOpen()) {
                Log.e("MmsApp", "DB not open after open()")
                isDbReady = false
            } else {
                isDbReady = true
            }
            repo = MmsRepository(db)
            Log.i("MmsApp", "DB ready=${isDbReady}")
        } catch (e: Throwable) {
            Log.e("MmsApp", "DB init failed – app will show error UI instead of crashing", e)
            initError = e
            isDbReady = false
            // Still create a usable repo with a fresh empty DB if possible, so that login/setup can still be attempted
            // If db not initialized, create a bare one and mark ready false; UI will check initError
            try {
                if (!::db.isInitialized) {
                    db = DatabaseManager(this)
                    // don't call open again – just keep it closed; repo will handle gracefully
                }
                if (!::repo.isInitialized) {
                    // Create repo anyway – its calls will fail gracefully and be shown as toasts
                    repo = MmsRepository(db)
                }
            } catch (e2: Throwable) {
                Log.e("MmsApp", "Fallback repo init also failed", e2)
            }
        }
    }

    companion object {
        lateinit var instance: MmsApp
            private set
    }
}
