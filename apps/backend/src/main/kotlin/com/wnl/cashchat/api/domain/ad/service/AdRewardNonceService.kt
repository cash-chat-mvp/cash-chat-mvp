package com.wnl.cashchat.api.domain.ad.service

import com.wnl.cashchat.api.domain.ad.persistence.entity.AdRewardNonce
import com.wnl.cashchat.api.domain.ad.persistence.repository.AdRewardNonceRepository
import com.wnl.cashchat.api.domain.ad.properties.AdRewardProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AdRewardNonceService(
    private val adRewardNonceRepository: AdRewardNonceRepository,
    private val adRewardProperties: AdRewardProperties,
) {
    @Transactional
    fun issueFor(userId: Long, now: Instant): AdRewardNonce =
        adRewardNonceRepository.save(
            AdRewardNonce(
                nonce = UUID.randomUUID().toString().replace("-", ""),
                userId = userId,
                expiresAt = now.plus(adRewardProperties.nonceTtl),
            )
        )
}
