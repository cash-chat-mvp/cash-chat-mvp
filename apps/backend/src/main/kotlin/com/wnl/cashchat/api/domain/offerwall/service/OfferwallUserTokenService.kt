package com.wnl.cashchat.api.domain.offerwall.service

import com.wnl.cashchat.api.domain.offerwall.persistence.entity.OfferwallUserToken
import com.wnl.cashchat.api.domain.offerwall.persistence.repository.OfferwallUserTokenRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class OfferwallUserTokenService(
    private val offerwallUserTokenRepository: OfferwallUserTokenRepository,
) {
    /**
     * 사용자당 안정적 토큰을 get-or-create 한다. UserPointService.createInitialPoint 와 동일 패턴:
     * 메서드에 @Transactional 을 두지 않아 saveAndFlush 가 자체 암시적 트랜잭션에서 실행된다.
     * 동시 최초 호출이 와도 PK(user_id) 충돌은 DataIntegrityViolationException 으로 흡수되고
     * (해당 암시적 트랜잭션만 롤백), 새 읽기로 기존 토큰을 다시 조회해 반환한다(단일 생성 보장).
     */
    fun tokenFor(userId: Long): String {
        offerwallUserTokenRepository.findByUserId(userId)?.let { return it.token }
        return try {
            offerwallUserTokenRepository.saveAndFlush(
                OfferwallUserToken(
                    userId = userId,
                    token = UUID.randomUUID().toString().replace("-", ""),
                )
            ).token
        } catch (e: DataIntegrityViolationException) {
            offerwallUserTokenRepository.findByUserId(userId)?.token ?: throw e
        }
    }

    @Transactional(readOnly = true)
    fun resolveUserId(token: String): Long? =
        offerwallUserTokenRepository.findByToken(token)?.userId
}
