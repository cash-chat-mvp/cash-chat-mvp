package com.nomadclub.cashchat

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.nomadclub.cashchat.core.data.DataStoreTokenProvider
import com.nomadclub.cashchat.core.data.TokenDataStore
import com.nomadclub.cashchat.di.appModule
import com.nomadclub.cashchat.shared.di.sharedModule
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CashChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CashChatApplication)
            modules(
                appModule,
                sharedModule(
                    baseUrl = BuildConfig.BASE_URL,
                    tokenProvider = DataStoreTokenProvider(TokenDataStore(this@CashChatApplication)),
                    engineProvider = { OkHttp.create() },
                ),
            )
        }
        MobileAds.initialize(this)
    }
}
