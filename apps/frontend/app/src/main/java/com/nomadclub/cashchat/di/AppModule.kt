package com.nomadclub.cashchat.di

import com.nomadclub.cashchat.BuildConfig
import com.nomadclub.cashchat.core.data.ThemePreferenceStore
import com.nomadclub.cashchat.core.data.TokenDataStore
import com.nomadclub.cashchat.core.network.ApiService
import com.nomadclub.cashchat.core.network.AuthInterceptor
import com.nomadclub.cashchat.core.network.DataStoreTokenProvider
import com.nomadclub.cashchat.core.network.TokenAuthenticator
import com.nomadclub.cashchat.data.repository.AuthRepository
import com.nomadclub.cashchat.shared.core.network.TokenProvider
import com.nomadclub.cashchat.shared.localllm.LocalChatStore
import com.nomadclub.cashchat.shared.localllm.LocalLlmEngine
import com.nomadclub.cashchat.shared.session.SessionResetter
import com.nomadclub.cashchat.feature.auth.AuthViewModel
import com.nomadclub.cashchat.feature.settings.SettingsViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val appModule = module {

    single { TokenDataStore(androidContext()) }

    single { ThemePreferenceStore(androidContext()) }

    single { com.nomadclub.cashchat.core.data.CharacterPreferenceStore(androidContext()) }

    single { AuthInterceptor(get()) }

    // 토큰 refresh 전역 직렬화 락 (Retrofit ↔ Ktor 경로 공유)
    single { com.nomadclub.cashchat.core.network.TokenRefreshGate() }

    single { TokenAuthenticator(get(), BuildConfig.BASE_URL, get()) }

    single {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")  // Bearer 토큰이 로그에 노출되지 않도록
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor(get<AuthInterceptor>())
            .authenticator(get<TokenAuthenticator>())
            .addInterceptor(logging)
            .build()
    }

    single {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    // SessionResetter는 lazy로 전달해 DI 순환(HttpClient→TokenProvider→AuthRepository→SessionResetter
    // →PointsRepository→PointsApi→HttpClient)을 끊는다.
    single { AuthRepository(get(), get(), get(), lazy { get<SessionResetter>() }, lazy { get<LocalChatStore>() }) }

    viewModel { AuthViewModel(get()) }

    viewModel { SettingsViewModel(get()) }

    viewModel { com.nomadclub.cashchat.feature.chat.ChatViewModel(get(), get(), get()) }

    single<LocalLlmEngine> { com.nomadclub.cashchat.localllm.UnavailableLocalLlmEngine() }
    single {
        LocalChatStore(
            engine = get(),
            history = get(),
            scope = get(),
            modelSpec = get(),
        )
    }
    viewModel {
        com.nomadclub.cashchat.feature.chat.LocalChatViewModel(
            modeStore = get(),
            downloadStore = get(),
            localChatStore = get(),
            gemmaSpec = get(),
            engineAvailability = com.nomadclub.cashchat.feature.chat.GemmaEngineAvailability.Unavailable(
                "Gemma 실행 엔진은 아직 이 Android 빌드에 포함되지 않았어요.",
            ),
        )
    }

    viewModel { com.nomadclub.cashchat.feature.chat.evolution.EvolutionViewModel(get(), get()) }

    viewModel { com.nomadclub.cashchat.feature.rewards.BenefitRewardViewModel(get(), get()) }

    viewModel { com.nomadclub.cashchat.feature.rewards.RouletteViewModel(get()) }

    viewModel { com.nomadclub.cashchat.feature.rewards.InviteViewModel(get()) }

    // shared 데이터 레이어 (CC-348)
    single<TokenProvider> { DataStoreTokenProvider(get(), get()) }

    // Firebase Remote Config / Analytics
    single { com.nomadclub.cashchat.config.RemoteConfigManager(appVersionName = BuildConfig.VERSION_NAME) }
    single { com.nomadclub.cashchat.config.AnalyticsManager(androidContext()) }

    // AppConfig: RC(활성값)→BuildConfig→테스트ID 계층 폴백
    single { com.nomadclub.cashchat.config.AppConfig.resolve(get()) }
    // 네이티브 광고 삽입 빈도(ad_chat_interval). ChatStore(shared)가 get()으로 요구한다.
    single<com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider> {
        com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider {
            get<com.nomadclub.cashchat.config.AppConfig>().adChatInterval
        }
    }
    single<com.nomadclub.cashchat.ads.RewardedAdPresenter> { com.nomadclub.cashchat.ads.RewardedAdManager(get()) }
    single { com.nomadclub.cashchat.ads.NativeAdManager(get()) }

    single { com.nomadclub.cashchat.offerwall.TnkOfferwallManager(get()) }
}
