package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.attendance.AttendanceApiService
import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.core.network.ApiConfig
import com.nomadclub.cashchat.shared.core.network.AuthenticatedApiClient
import com.nomadclub.cashchat.shared.core.network.TokenProvider
import com.nomadclub.cashchat.shared.points.LocalPointsRepository
import com.nomadclub.cashchat.shared.points.PointsRepository
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * shared 도메인 DI. 플랫폼은 ApiConfig, TokenProvider, HttpClientEngine 을 제공해야 한다.
 *  - Android: AppModule 에서 baseUrl(BuildConfig.BASE_URL), DataStoreTokenProvider, OkHttp 엔진 제공
 *  - iOS: Koin start 시 Darwin 엔진 + KeychainTokenProvider 제공
 */
fun sharedModule(
    baseUrl: String,
    tokenProvider: TokenProvider,
    engineProvider: () -> HttpClientEngine,
) = module {
    single { ApiConfig(baseUrl) }
    single { tokenProvider }
    single { AuthenticatedApiClient(get(), get(), engineProvider()) }
    single<PointsRepository> { LocalPointsRepository() }
    single { AttendanceApiService(get(), get<AuthenticatedApiClient>().httpClient) }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { AttendanceStore(get(), get(), get()) }
}
