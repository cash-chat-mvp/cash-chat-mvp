package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.core.network.TokenProvider
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * iOS 앱 시작 시 1회 호출. Swift 에서 KoinIosKt.doInitKoin(baseUrl:tokenProvider:) 로 접근.
 *
 * 브랜치 DI 규약: sharedDataModule(baseUrl) 는 소비측이 먼저 TokenProvider 를 등록한다는
 * 전제로 동작한다. engineProvider 파라미터 없음(Darwin 은 actual 로 배선됨).
 */
fun doInitKoin(baseUrl: String, tokenProvider: TokenProvider) {
    startKoin {
        modules(
            module { single<TokenProvider> { tokenProvider } },
            sharedDataModule(baseUrl),
        )
    }
}
