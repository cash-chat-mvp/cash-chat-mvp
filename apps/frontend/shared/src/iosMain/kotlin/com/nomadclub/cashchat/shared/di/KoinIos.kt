package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.core.network.TokenProvider
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * iOS 앱 시작 시 1회 호출. Swift 에서 KoinIosKt.doInitKoin(baseUrl:tokenProvider:adChatInterval:gemmaEngine:) 로 접근.
 *
 * 브랜치 DI 규약: sharedDataModule(baseUrl) 는 소비측이 먼저 TokenProvider 를 등록한다는
 * 전제로 동작한다. Gemma 온디바이스 엔진은 Swift(LiteRT-LM Metal) 구현을 SwiftBackedLocalLlmEngine
 * 으로 감싸 [gemmaEngine] 으로 주입한다.
 */
fun doInitKoin(
    baseUrl: String,
    tokenProvider: TokenProvider,
    adChatInterval: Long,
    gemmaEngine: com.nomadclub.cashchat.shared.localllm.LocalLlmEngine,
    // Remote Config 의 자체 모델 CDN URL. 빈 문자열/없음이면 빌트인 기본값 사용.
    gemmaModelUrl: String? = null,
) {
    startKoin {
        modules(
            module {
                single<TokenProvider> { tokenProvider }
                single<com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider> {
                    com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider { adChatInterval }
                }
                single<com.nomadclub.cashchat.shared.localllm.LocalLlmEngine> { gemmaEngine }
                // LocalChatHistory/GemmaModelSpec/CoroutineScope 는 sharedDataModule 에서 등록됨.
                single {
                    com.nomadclub.cashchat.shared.localllm.LocalChatStore(
                        engine = get(),
                        history = get(),
                        scope = get(),
                        modelSpec = get(),
                    )
                }
            },
            sharedDataModule(baseUrl, gemmaModelUrl),
        )
    }
}
