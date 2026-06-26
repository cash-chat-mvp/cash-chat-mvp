package com.wnl.cashchat.api.domain.offerwall.properties

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.offerwall.tnk")
data class TnkOfferwallProperties(
    /** 플랫폼별 md_chk 검증용 공유 시크릿. TNK 가 Android/iOS 앱마다 다른 앱키를 발급한다. */
    val android: Platform = Platform(),
    val ios: Platform = Platform(),

    @field:Positive
    val pointToCoinRatio: Double = 1.0,

    val ack: Ack = Ack(),
) {
    data class Platform(
        /** prod 는 반드시 주입. 미설정 시 빈 값이라 해당 플랫폼 콜백이 모두 서명 실패로 거절된다(fail-closed). */
        val appKey: String = "",
    )

    data class Ack(
        val successBody: String = "SUCCESS",
    )

    fun appKeyFor(platform: OfferwallPlatform): String = when (platform) {
        OfferwallPlatform.ANDROID -> android.appKey
        OfferwallPlatform.IOS -> ios.appKey
    }
}
