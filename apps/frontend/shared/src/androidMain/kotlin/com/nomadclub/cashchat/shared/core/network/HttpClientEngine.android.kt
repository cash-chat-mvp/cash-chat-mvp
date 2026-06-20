package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android 기본 엔진(OkHttp). HTTP 버전은 강제하지 않고 서버와 협상한다(HTTP/2 가능).
 *
 * 과거에는 nginx HTTP/2 SSE 종료 시 RST_STREAM(INTERNAL_ERROR) 회피를 위해 HTTP/1.1을
 * 강제했으나, 이제 백엔드(PR #189/CC-311)의 `event: done` 신호로 정상 종료를 구분하고
 * done 이후 리셋을 흡수하므로(see [ChatApi.streamMessage]) 버전 강제가 불필요하다.
 */
internal actual fun defaultClientEngine(): HttpClientEngine = OkHttp.create { }
