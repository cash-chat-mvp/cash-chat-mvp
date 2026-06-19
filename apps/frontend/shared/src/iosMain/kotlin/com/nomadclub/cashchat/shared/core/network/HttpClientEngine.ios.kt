package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS 기본 엔진(Darwin/NSURLSession). NSURLSession은 HTTP 버전을 강제할 수 없어 서버와
 * HTTP/2로 협상한다.
 *
 * 과거 nginx HTTP/2 SSE 종료 시 -1005("network connection lost")로 채팅 스트림이 끊겨
 * iOS SSE가 막혀 있었으나(Android의 RST_STREAM과 동일 계열), 백엔드(PR #189/CC-311)의
 * `event: done` 명시 종료 신호 + 클라이언트의 done 이후 리셋 흡수([ChatApi.streamMessage])로
 * iOS도 동일 경로에서 정상 동작하게 됐다.
 */
internal actual fun defaultClientEngine(): HttpClientEngine = Darwin.create { }
