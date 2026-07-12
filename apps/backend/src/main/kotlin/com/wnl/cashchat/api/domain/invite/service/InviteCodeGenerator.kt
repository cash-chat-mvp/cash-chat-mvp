package com.wnl.cashchat.api.domain.invite.service

import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * 추천 코드 생성기. 혼동되기 쉬운 문자(O/0/I/1/L)를 제외한 대문자+숫자에서 균일 추출한다.
 * 충돌 회피(재시도)는 호출 측(InviteService.getOrCreateCode)이 UNIQUE 제약으로 처리한다.
 */
@Component
class InviteCodeGenerator {
    fun generate(length: Int): String {
        require(length > 0) { "length must be positive" }
        return buildString(length) {
            repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
    }

    private companion object {
        const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
    }
}
