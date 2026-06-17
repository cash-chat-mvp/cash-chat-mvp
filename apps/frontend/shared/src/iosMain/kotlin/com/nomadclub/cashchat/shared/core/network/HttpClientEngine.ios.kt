package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS는 Darwin(NSURLSession) 엔진을 사용한다.
 * NSURLSession은 HTTP 버전 강제가 제한적이라 기본값을 쓰되, SSE 동작은 Android와 별개로
 * 추후 검증이 필요하다. (RST_STREAM 문제는 OkHttp/HTTP-2 경로에서 관찰됨)
 */
internal actual fun http1ClientEngine(): HttpClientEngine = Darwin.create { }
