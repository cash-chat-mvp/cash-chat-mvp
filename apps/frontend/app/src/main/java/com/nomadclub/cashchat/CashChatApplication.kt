package com.nomadclub.cashchat

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.nomadclub.cashchat.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class CashChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Koin DI 초기화
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@CashChatApplication)
            modules(appModule)
        }

        // AdMob SDK 초기화 — 콜백 방식으로 완료 후 로그 출력
        // 광고 로드는 초기화 완료 전에 요청해도 SDK가 내부 큐잉 처리하지만,
        // RewardedAdManager.preload()는 ChatScreen 진입 시점(초기화 완료 이후)에 호출되므로 실질적 race condition 없음
        MobileAds.initialize(this) { initStatus ->
            val statusMap = initStatus.adapterStatusMap.entries.joinToString { "${it.key}: ${it.value.initializationState}" }
            Log.d("CashChatApp", "AdMob 초기화 완료 — $statusMap")
        }
    }
}
