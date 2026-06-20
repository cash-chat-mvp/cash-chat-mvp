package com.nomadclub.cashchat

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.tnkfactory.ad.TnkSession
import com.nomadclub.cashchat.di.appModule
import com.nomadclub.cashchat.shared.di.sharedDataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CashChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CashChatApplication)
            modules(appModule, sharedDataModule(BuildConfig.BASE_URL))
        }
        MobileAds.initialize(this)
        // TNK 오퍼월 SDK 초기화 (앱 시작 시 1회)
        TnkSession.applicationStarted(this)
    }
}
