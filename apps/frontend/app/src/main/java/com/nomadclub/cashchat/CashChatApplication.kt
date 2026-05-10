package com.nomadclub.cashchat

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.nomadclub.cashchat.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CashChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CashChatApplication)
            modules(appModule)
        }
        MobileAds.initialize(this)
    }
}
