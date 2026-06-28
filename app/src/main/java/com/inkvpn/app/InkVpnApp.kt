package com.inkvpn.app

import android.app.Application
import com.inkvpn.app.data.Repository

class InkVpnApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = Repository(this)
    }

    companion object {
        lateinit var instance: InkVpnApp
            private set
    }
}
