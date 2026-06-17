package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.persistence.repository.OfferwallUserTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OfferwallUserTokenService(
    private val offerwallUserTokenRepository: OfferwallUserTokenRepository,
) {
    /**
     * 사용자당 안정적 토큰을 get-or-create 한다. 멱등 INSERT(ON DUPLICATE KEY no-op)를 사용해
     * saveAndFlush 의 DataIntegrityViolationException 이 상위 트랜잭션을 rollback-only 로 만드는 문제를 피한다
     * (TnkOfferwallCallbackRepository 와 동일 패턴). 동시 최초 호출이 와도 user_id PK 충돌로 한 행만 남고,
     * 두 호출 모두 먼저 커밋된 토큰을 다시 읽어 반환한다(단일 생성 보장).
     */
    @Transactional
    fun tokenFor(userId: Long): String {
        offerwallUserTokenRepository.findByUserId(userId)?.let { return it.token }
        val newToken = UUID.randomUUID().toString().replace("-", "")
        offerwallUserTokenRepository.insertIfAbsent(userId, newToken)
        // 잠금 읽기로 최신 커밋 토큰을 확정 조회(동시 최초 호출 시 스냅샷 누락 방지).
        return offerwallUserTokenRepository.findForUpdate(userId)?.token
            ?: throw IllegalStateException("offerwall_user_tokens row must exist for userId=$userId")
    }

    @Transactional(readOnly = true)
    fun resolveUserId(token: String): Long? =
        offerwallUserTokenRepository.findByToken(token)?.userId
}
