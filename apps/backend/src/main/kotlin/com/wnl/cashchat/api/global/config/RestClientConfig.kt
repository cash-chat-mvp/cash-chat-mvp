package com.wnl.cashchat.api.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class RestClientConfig {

    /**
     * 외부 OAuth/JWKS 호출용 공용 RestClient.
     *
     * 반드시 connect/read 타임아웃을 둔다: 이 클라이언트는 외부 IdP(Google/Apple) 호출에 쓰이며,
     * 호출부가 DB 트랜잭션과 가까운 곳에서 동작하므로 무한 대기가 발생하면 HikariCP 커넥션이
     * 반납되지 못한 채 묶여 풀 고갈로 이어진다. (참고: auth hang 장애 / Confluence FCTC 22216706 이슈2)
     */
    @Bean
    fun restClient(): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
        }
        return RestClient.builder()
            .requestFactory(requestFactory)
            .build()
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 3L
        const val READ_TIMEOUT_SECONDS = 5L
    }

}
