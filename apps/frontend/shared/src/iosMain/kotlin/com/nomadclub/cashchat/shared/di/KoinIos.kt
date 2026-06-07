package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.core.network.TokenProvider
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.context.startKoin

/** iOS 앱 시작 시 1회 호출. Swift 에서 KoinIosKt.doInitKoin(...) 으로 접근. */
fun doInitKoin(baseUrl: String, tokenProvider: TokenProvider) {
    startKoin {
        modules(
            sharedModule(
                baseUrl = baseUrl,
                tokenProvider = tokenProvider,
                engineProvider = { Darwin.create() },
            )
        )
    }
}
