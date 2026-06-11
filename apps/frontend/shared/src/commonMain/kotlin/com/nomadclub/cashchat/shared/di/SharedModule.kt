package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.ads.AdRewardStore
import com.nomadclub.cashchat.shared.ads.AdsApi
import com.nomadclub.cashchat.shared.attendance.AttendanceApi
import com.nomadclub.cashchat.shared.chat.ApiChatGateway
import com.nomadclub.cashchat.shared.chat.ChatApi
import com.nomadclub.cashchat.shared.chat.ChatGateway
import com.nomadclub.cashchat.shared.chat.ChatStore
import com.nomadclub.cashchat.shared.core.network.TokenProvider
import com.nomadclub.cashchat.shared.core.network.createCashChatHttpClient
import com.nomadclub.cashchat.shared.energy.EnergyApi
import com.nomadclub.cashchat.shared.evolution.EvolutionApi
import com.nomadclub.cashchat.shared.evolution.EvolutionStore
import com.nomadclub.cashchat.shared.hud.HudStore
import com.nomadclub.cashchat.shared.wallet.PointsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * shared 데이터 레이어 Koin 모듈.
 * 사용처(Android/iOS)는 baseUrl과 TokenProvider 구현을 먼저 등록해야 한다.
 */
fun sharedDataModule(baseUrl: String) = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { createCashChatHttpClient(baseUrl, get<TokenProvider>()) }

    single { ChatApi(get(), baseUrl) }
    single { EnergyApi(get(), baseUrl) }
    single { EvolutionApi(get(), baseUrl) }
    single { AdsApi(get(), baseUrl) }
    single { PointsApi(get(), baseUrl) }
    single { AttendanceApi(get(), baseUrl) }

    single<ChatGateway> { ApiChatGateway(get()) }
    single { ChatStore(get(), get()) }
    single { HudStore(get(), get(), get(), get()) }
    single { EvolutionStore(get()) }
    single {
        val adsApi = get<AdsApi>()
        val energyApi = get<EnergyApi>()
        AdRewardStore(
            fetchQuota = { adsApi.getQuota() },
            issueNonce = { adsApi.issueNonce() },
            fetchEnergy = { energyApi.getMyEnergy() },
            scope = get(),
        )
    }
}
