package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS는 Darwin(NSURLSession) 엔진을 사용한다.
 *
 * NSURLSession은 HTTP 버전을 강제할 수 없어 서버와 HTTP/2로 협상한다. nginx가 SSE 응답을
 * HTTP/2로 중계할 때 스트림을 리셋(-1005 "network connection lost")해 채팅 스트리밍이 끊기는
 * 문제가 있다(Android의 RST_STREAM 문제와 동일 계열). Android는 OkHttp로 HTTP/1.1을 강제해
 * 회피했지만 NSURLSession은 그게 불가능하고, CIO 엔진은 native 에서 TLS를 지원하지 않는다.
 * → 채팅 SSE 안정화는 서버(nginx)의 HTTP/2 SSE 처리로 해결해야 한다(별도 백엔드 이슈).
 */
internal actual fun http1ClientEngine(): HttpClientEngine = Darwin.create { }
