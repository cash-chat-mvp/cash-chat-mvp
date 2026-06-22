package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallPlatform
import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * TNK 콜백의 md_chk 를 플랫폼별 앱키로 검증한다. md_chk == MD5(appKey + md_user_nm + seq_id) (lowercase hex).
 * 플랫폼은 콜백 경로로 확정되므로 서명검증 시점에 어떤 앱키를 쓸지 결정돼 있다.
 */
@Component
class TnkMdChecksumVerifier(
    private val tnkOfferwallProperties: TnkOfferwallProperties,
) {
    fun isValid(platform: OfferwallPlatform, params: TnkOfferwallCallbackParams): Boolean {
        val appKey = tnkOfferwallProperties.appKeyFor(platform)
        // app_key 가 비어 있으면 공격자도 동일 해시를 계산할 수 있어 fail-open 이 된다. 미설정 시 거절(fail-closed).
        if (appKey.isBlank()) return false
        val expected = md5Hex(appKey + params.mdUserNm + params.seqId)
        return expected.equals(params.mdChk, ignoreCase = true)
    }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
