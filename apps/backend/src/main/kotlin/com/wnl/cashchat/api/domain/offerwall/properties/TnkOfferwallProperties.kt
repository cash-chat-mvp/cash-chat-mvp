package com.wnl.cashchat.api.domain.offerwall.properties

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.offerwall.tnk")
data class TnkOfferwallProperties(
    /** md_chk 검증용 공유 시크릿. prod 는 반드시 주입, 미설정 시 빈 값이라 모든 콜백이 서명 실패로 거절된다. */
    val appKey: String = "",

    @field:Positive
    val pointToCoinRatio: Double = 1.0,

    val ack: Ack = Ack(),
) {
    data class Ack(
        val successBody: String = "SUCCESS",
    )
}
