package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Protocol

/**
 * Android는 OkHttp 엔진을 HTTP/1.1로 고정한다.
 * nginx HTTP/2 + SSE 환경에서 응답 종료 직후 RST_STREAM(INTERNAL_ERROR)으로 스트림이
 * 끊겨 "응답이 끊겼어요"가 항상 뜨던 문제를 방지한다.
 */
internal actual fun http1ClientEngine(): HttpClientEngine =
    OkHttp.create {
        config { protocols(listOf(Protocol.HTTP_1_1)) }
    }
