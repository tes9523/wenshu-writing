package com.wenshu.writing

import android.app.Application
import com.wenshu.writing.di.AppContainer

class WenShuApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
