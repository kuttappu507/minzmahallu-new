package com.mms.minzmahallu

import android.app.Application
import com.mms.minzmahallu.data.db.DatabaseManager
import com.mms.minzmahallu.data.repository.MmsRepository
import com.mms.minzmahallu.i18n.I18n

class MmsApp : Application() {
    lateinit var db: DatabaseManager
        private set
    lateinit var repo: MmsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        I18n.init(this)
        db = DatabaseManager(this)
        db.open()
        repo = MmsRepository(db)
    }

    companion object {
        lateinit var instance: MmsApp
            private set
    }
}
