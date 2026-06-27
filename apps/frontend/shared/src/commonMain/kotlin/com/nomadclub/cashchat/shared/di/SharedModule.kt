package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.ads.AdRewardStore
import com.nomadclub.cashchat.shared.ads.AdsApi
import com.nomadclub.cashchat.shared.attendance.AttendanceApi
import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.chat.ApiChatGateway
import com.nomadclub.cashchat.shared.chat.ChatApi
import com.nomadclub.cashchat.shared.chat.ChatGateway
import com.nomadclub.cashchat.shared.chat.ChatStore
import com.nomadclub.cashchat.shared.core.network.TokenProvider
import com.nomadclub.cashchat.shared.core.network.createCashChatHttpClient
import com.nomadclub.cashchat.shared.core.network.createModelDownloadHttpClient
import com.nomadclub.cashchat.shared.energy.EnergyApi
import com.nomadclub.cashchat.shared.evolution.EvolutionApi
import com.nomadclub.cashchat.shared.evolution.EvolutionStore
import com.nomadclub.cashchat.shared.hud.HudStore
import com.nomadclub.cashchat.shared.localllm.ChatModeStore
import com.nomadclub.cashchat.shared.localllm.DEFAULT_GEMMA_SPEC
import com.nomadclub.cashchat.shared.localllm.GemmaModelSpec
import com.nomadclub.cashchat.shared.localllm.JsonFileLocalChatHistory
import com.nomadclub.cashchat.shared.localllm.KtorModelDownloader
import com.nomadclub.cashchat.shared.localllm.LocalChatHistory
import com.nomadclub.cashchat.shared.localllm.ModelDownloadStore
import com.nomadclub.cashchat.shared.localllm.ModelDownloader
import com.nomadclub.cashchat.shared.points.PointsRepository
import com.nomadclub.cashchat.shared.points.RemotePointsRepository
import com.nomadclub.cashchat.shared.session.SessionResetter
import com.nomadclub.cashchat.shared.wallet.PointsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * shared 데이터 레이어 Koin 모듈.
 * 사용처(Android/iOS)는 baseUrl과 TokenProvider 구현을 먼저 등록해야 한다.
 */
fun sharedDataModule(
    rawBaseUrl: String,
    // 온디바이스 모델 다운로드 URL 오버라이드(Remote Config 주입). 비어 있으면 빌트인 기본값 사용.
    // HF CDN(IPv4 전용·US 리전) 대신 자체 듀얼스택 CDN 으로 앱 업데이트 없이 바꾸기 위함.
    gemmaModelUrl: String? = null,
) = module {
    // API 들이 "$baseUrl/api/..." 로 경로를 이어 붙이므로 끝의 '/'를 제거해
    // "https://host//api/..." 같은 이중 슬래시(서버 401 유발)를 방지한다.
    val baseUrl = rawBaseUrl.trimEnd('/')

    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { createCashChatHttpClient(baseUrl, get<TokenProvider>()) }

    single { ChatApi(get(), baseUrl) }
    single { EnergyApi(get(), baseUrl) }
    single { EvolutionApi(get(), baseUrl) }
    single { AdsApi(get(), baseUrl) }
    single { PointsApi(get(), baseUrl) }
    single { AttendanceApi(get(), baseUrl) }
    single { com.nomadclub.cashchat.shared.offerwall.OfferwallApi(get(), baseUrl) }
    // 혜택존(출석/코인) — BE GET /api/points/me 잔액을 단일 소스로 사용
    single<PointsRepository> { RemotePointsRepository(get()) }
    single { AttendanceStore(get(), get(), get()) }
    single { com.nomadclub.cashchat.shared.shop.ShopApi(get(), baseUrl) }
    single { com.nomadclub.cashchat.shared.energy.EnergyTopupApi(get(), baseUrl) }

    single<ChatGateway> { ApiChatGateway(get()) }
    single { ChatStore(get(), get(), get()) }
    single { HudStore(get(), get(), get(), get()) }
    single { ChatModeStore() }
    single<GemmaModelSpec> {
        val override = gemmaModelUrl?.takeIf { it.isNotBlank() }
        if (override != null) DEFAULT_GEMMA_SPEC.copy(url = override) else DEFAULT_GEMMA_SPEC
    }
    // 모델 다운로드는 인증/JSON 협상이 박힌 API 클라이언트가 아니라 전용 클라이언트를 쓴다.
    single<ModelDownloader> { KtorModelDownloader(createModelDownloadHttpClient()) }
    single {
        ModelDownloadStore(
            spec = get(),
            downloader = get(),
            scope = get(),
        )
    }
    single<LocalChatHistory> { JsonFileLocalChatHistory() }
    single<com.nomadclub.cashchat.shared.roulette.RouletteRepository> {
        com.nomadclub.cashchat.shared.roulette.FakeRouletteRepository()
    }
    single {
        val hud = get<HudStore>()
        com.nomadclub.cashchat.shared.roulette.RouletteStore(
            repo = get(),
            onEnergyChanged = { hud.refreshEnergyOnly() },
        )
    }
    single<com.nomadclub.cashchat.shared.invite.InviteRepository> {
        com.nomadclub.cashchat.shared.invite.FakeInviteRepository()
    }
    single {
        val hud = get<HudStore>()
        com.nomadclub.cashchat.shared.invite.InviteStore(
            repo = get(),
            onRewardChanged = { hud.refreshEnergyOnly() },
        )
    }
    single { EvolutionStore(get()) }
    single {
        val adsApi = get<AdsApi>()
        AdRewardStore(
            fetchQuota = { adsApi.getQuota() },
            issueNonce = { adsApi.issueNonce() },
            scope = get(),
        )
    }
    // 로그아웃/세션 만료 시 사용자별 스토어를 일괄 초기화 (계정 전환 후 이전 사용자 데이터 노출 방지)
    single { SessionResetter(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
