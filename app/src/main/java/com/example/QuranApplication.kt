package com.example

import android.app.Application
import com.example.data.local.DatabaseProvider

class QuranApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DatabaseProvider.init(this)
    }
}
