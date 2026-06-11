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

    single { AuthInterceptor(get()) }

    single { TokenAuthenticator(get(), BuildConfig.BASE_URL) }

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

    single { AuthRepository(get(), get()) }

    viewModel { AuthViewModel(get()) }

    viewModel { SettingsViewModel(get()) }

    // shared 데이터 레이어 (CC-348)
    single<TokenProvider> { DataStoreTokenProvider(get(), get()) }
}
