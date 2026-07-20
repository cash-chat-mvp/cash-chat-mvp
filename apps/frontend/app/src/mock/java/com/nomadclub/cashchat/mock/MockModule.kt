package com.nomadclub.cashchat.mock

import com.nomadclub.cashchat.ads.RewardedAdPresenter
import com.nomadclub.cashchat.offerwall.OfferwallLauncher
import com.nomadclub.cashchat.shared.core.network.TokenProvider
import com.nomadclub.cashchat.shared.core.network.createCashChatHttpClient
import com.nomadclub.cashchat.shared.points.PointsRepository
import org.koin.dsl.module

/** 딱 3개만 override: HttpClient(MockEngine) / RewardedAdPresenter / OfferwallLauncher. */
val mockModule = module {
    single { MockBackendState() }
    // HttpClient override → 모든 *Api 가 Fake 백엔드를 침
    single {
        createCashChatHttpClient(
            baseUrl = "https://mock.local",
            tokenProvider = get<TokenProvider>(),
            engine = fakeBackendEngine(get<MockBackendState>()),
        )
    }
    single<RewardedAdPresenter> { FakeRewardedAdPresenter(get<MockBackendState>()) }
    single<OfferwallLauncher> { FakeOfferwallLauncher(get<MockBackendState>(), get<PointsRepository>()) }
}
