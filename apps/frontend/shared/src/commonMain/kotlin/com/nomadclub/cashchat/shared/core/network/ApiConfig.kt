package com.nomadclub.cashchat.shared.core.network

/**
 * API 서버 기본 URL 보관. DI 로 주입.
 * Android BuildConfig.BASE_URL 은 끝에 `/` 가 붙으므로, 경로 결합 시 이중 슬래시(`//api/...`)가
 * 생기지 않도록 후행 슬래시를 제거해 정규화한다.
 */
class ApiConfig(baseUrl: String) {
    val baseUrl: String = baseUrl.trimEnd('/')
}
