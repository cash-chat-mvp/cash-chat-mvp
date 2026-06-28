package com.nomadclub.cashchat

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.tnkfactory.ad.TnkSession
import com.nomadclub.cashchat.config.RemoteConfigManager
import com.nomadclub.cashchat.di.appModule
import com.nomadclub.cashchat.shared.di.sharedDataModule
import com.nomadclub.cashchat.shared.localllm.AndroidLocalLlmContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CashChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidLocalLlmContext.init(this)
        val koin = startKoin {
            androidContext(this@CashChatApplication)
            modules(appModule, sharedDataModule(BuildConfig.BASE_URL))
        }.koin
        // Firebase Remote Config 초기화 (캐시 즉시 반영 + 최신 값 fetch).
        // Firebase 자체는 google-services 플러그인이 ContentProvider로 자동 초기화한다.
        koin.get<RemoteConfigManager>().initialize()
        MobileAds.initialize(this)
        // TNK 오퍼월 SDK 초기화 (앱 시작 시 1회)
        TnkSession.applicationStarted(this)
    }
}
