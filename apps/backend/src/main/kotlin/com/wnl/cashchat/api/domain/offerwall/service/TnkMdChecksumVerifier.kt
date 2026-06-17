package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.properties.TnkOfferwallProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * TNK 콜백의 md_chk 를 검증한다. md_chk == MD5(appKey + md_user_nm + seq_id) (lowercase hex).
 * appKey 는 공유 시크릿이므로, 이를 모르면 md_user_nm(토큰)·seq_id 를 위조해도 유효한 md_chk 를 만들 수 없다.
 * 정확한 연결 순서/인코딩은 TNK 확인 후 확정(spec "검증 필요 항목").
 */
@Component
class TnkMdChecksumVerifier(
    private val tnkOfferwallProperties: TnkOfferwallProperties,
) {
    fun isValid(params: TnkOfferwallCallbackParams): Boolean {
        val expected = md5Hex(tnkOfferwallProperties.appKey + params.mdUserNm + params.seqId)
        return expected.equals(params.mdChk, ignoreCase = true)
    }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
