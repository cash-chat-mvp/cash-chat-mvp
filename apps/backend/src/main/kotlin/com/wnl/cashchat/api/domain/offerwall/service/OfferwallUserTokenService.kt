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
     * 사용자당 안정적 토큰을 get-or-create 한다. 동시 최초 호출이 와도 PK(user_id) 충돌을
     * DataIntegrityViolationException 으로 흡수하고 기존 토큰을 다시 읽어 반환한다(단일 생성 보장).
     */
    fun tokenFor(userId: Long): String {
        offerwallUserTokenRepository.findByUserId(userId)?.let { return it.token }
        return try {
            doSaveToken(
                OfferwallUserToken(
                    userId = userId,
                    token = UUID.randomUUID().toString().replace("-", ""),
                )
            ).token
        } catch (e: DataIntegrityViolationException) {
            offerwallUserTokenRepository.findByUserId(userId)?.token ?: throw e
        }
    }

    @Transactional
    private fun doSaveToken(token: OfferwallUserToken): OfferwallUserToken =
        offerwallUserTokenRepository.saveAndFlush(token)

    @Transactional(readOnly = true)
    fun resolveUserId(token: String): Long? =
        offerwallUserTokenRepository.findByToken(token)?.userId
}
