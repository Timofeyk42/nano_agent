package com.example.nanoagent

import android.app.Application
import com.example.nanoagent.data.AppDatabase

class NanoAgentApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: NanoAgentApp
            private set
    }
}
